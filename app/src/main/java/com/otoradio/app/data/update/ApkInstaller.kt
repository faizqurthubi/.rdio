package com.otoradio.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Menyerahkan APK hasil unduhan ke installer sistem.
 *
 * Memakai `PackageInstaller` secara langsung akan memaksa kita menangani
 * STATUS_PENDING_USER_ACTION dari BroadcastReceiver; jalur ACTION_VIEW +
 * FileProvider memberi dialog yang sama dengan hasil yang lebih andal,
 * dan tetap meminta konfirmasi pengguna (tidak bisa dihindari di Android).
 */
object ApkInstaller {

    private fun authority(context: Context) = "${context.packageName}.updates"

    /** Apakah pengguna sudah mengizinkan aplikasi ini memasang APK? */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    /** Buka layar izin "Instal aplikasi tidak dikenal" untuk aplikasi ini. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val i = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    /** Menampilkan dialog instalasi sistem. true bila dialog berhasil dibuka. */
    fun install(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(context, authority(context), apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }
}
