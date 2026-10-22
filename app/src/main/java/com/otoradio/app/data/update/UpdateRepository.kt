package com.otoradio.app.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.otoradio.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateCheck {
    data class UpToDate(val currentVersion: String) : UpdateCheck
    data class Available(val info: UpdateInfo) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

sealed interface DownloadResult {
    data class Ready(val file: File) : DownloadResult
    data class Rejected(val reason: String) : DownloadResult
}

/**
 * Pemeriksa pembaruan: membaca GitHub Releases, mengunduh APK, lalu memverifikasi
 * digest SHA-256 dan sertifikat penanda tangan sebelum menyerahkannya ke installer.
 */
class UpdateRepository(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiUrl: String =
        BuildConfig.UPDATE_API_URL.ifEmpty {
            "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        }

    fun currentVersionName(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    fun currentVersionCode(): Long =
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
        }.getOrNull() ?: 0L

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OtoRadio-Updater")
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext UpdateCheck.Failed("Server membalas ${resp.code}")
                }
                val current = currentVersionName()
                val info = ReleaseParser.parseRelease(
                    body, current, allowInsecureHttp = BuildConfig.DEBUG
                )
                    ?: return@withContext UpdateCheck.UpToDate(current)
                UpdateCheck.Available(info)
            }
        } catch (t: Throwable) {
            UpdateCheck.Failed(t.message ?: "Tidak bisa menghubungi server")
        }
    }

    /**
     * Mengunduh APK sambil menghitung SHA-256, lalu menolak bila:
     * digest tidak cocok, nama paket berbeda, versi tidak lebih baru,
     * atau sertifikat penanda tangan berbeda dengan aplikasi terpasang.
     */
    suspend fun downloadAndVerify(
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "oto-update.apk")
        if (target.exists()) target.delete()

        val expected = info.sha256
            ?: return@withContext DownloadResult.Rejected(
                "Rilis ini tidak menyertakan SHA-256, unduhan dibatalkan demi keamanan."
            )

        try {
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "OtoRadio-Updater")
                .build()
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext DownloadResult.Rejected("Unduhan gagal (${resp.code})")
                }
                val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: info.apkSize
                var read = 0L
                resp.body?.byteStream()?.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                target.delete()
                return@withContext DownloadResult.Rejected(
                    "SHA-256 tidak cocok — berkas dihapus. Diharapkan ${expected.take(12)}…, didapat ${actual.take(12)}…"
                )
            }
            verifyApk(target)?.let { reason ->
                target.delete()
                return@withContext DownloadResult.Rejected(reason)
            }
            DownloadResult.Ready(target)
        } catch (t: Throwable) {
            target.delete()
            DownloadResult.Rejected(t.message ?: "Unduhan gagal")
        }
    }

    /**
     * Flag PackageManager untuk membaca sertifikat.
     * API < 28 (Android 8) belum mengenal GET_SIGNING_CERTIFICATES; memakainya di sana
     * menghasilkan `signatures` null sehingga APK sah terlihat "tidak bertanda tangan".
     */
    private val pmFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    /** null = lolos verifikasi; String = alasan penolakan. */
    private fun verifyApk(file: File): String? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, pmFlags)
            ?: return "Berkas yang diunduh bukan APK yang sah."

        if (archive.packageName != context.packageName) {
            return "Nama paket tidak cocok (${archive.packageName})."
        }
        val newCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode
        else @Suppress("DEPRECATION") archive.versionCode.toLong()
        if (newCode <= currentVersionCode()) {
            return "Versi di APK ($newCode) tidak lebih baru dari yang terpasang."
        }

        val newSigner = signerSha256(archive)
            ?: return "APK tidak bertanda tangan."
        val ownSigner = runCatching {
            signerSha256(pm.getPackageInfo(context.packageName, pmFlags))
        }.getOrNull() ?: return "Tidak bisa membaca sertifikat aplikasi terpasang."

        if (!newSigner.equals(ownSigner, ignoreCase = true)) {
            return "Sertifikat penanda tangan berbeda — pembaruan ditolak."
        }
        return null
    }

    private fun signerSha256(info: android.content.pm.PackageInfo): String? {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return null
        val first = signers.firstOrNull() ?: return null
        val bytes = MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
