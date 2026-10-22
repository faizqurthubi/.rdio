package com.otoradio.app.data.update

import org.json.JSONObject

/**
 * Informasi satu rilis dari GitHub Releases.
 *
 * Semua logika di sini sengaja "murni" (tanpa Android) supaya bisa diuji unit test:
 * parsing JSON, pemilihan asset APK, dan perbandingan versi.
 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** sha256 heksadesimal dari GitHub (field `digest`), null bila rilis lama tidak punya. */
    val sha256: String?,
    val immutable: Boolean,
    val publishedAt: String
)

object ReleaseParser {

    const val ASSET_SUFFIX = "-release.apk"

    /** Tag `v1.2` / `1.2` -> "1.2". */
    fun normalizeVersion(raw: String?): String? =
        raw?.trim()?.removePrefix("v")?.removePrefix("V")?.takeIf { it.isNotEmpty() }

    /**
     * Membandingkan dua versi numerik bertitik ("1.2" < "1.10").
     * Bagian non-numerik diabaikan; komponen yang hilang dianggap 0.
     */
    fun isNewer(remote: String, current: String): Boolean {
        val a = remote.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Mengurai body JSON `GET /repos/{owner}/{repo}/releases/latest`.
     * Mengembalikan null bila tidak ada asset APK rilis yang bisa dipakai.
     */
    fun parseRelease(
        json: String,
        currentVersion: String,
        /** Hanya true pada build debug untuk menguji lewat server lokal. */
        allowInsecureHttp: Boolean = false
    ): UpdateInfo? {
        val root = JSONObject(json)
        val version = normalizeVersion(root.optString("tag_name")) ?: return null
        val assets = root.optJSONArray("assets") ?: return null

        var apkName: String? = null
        var apkUrl: String? = null
        var apkSize = 0L
        var digest: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (!name.endsWith(ASSET_SUFFIX)) continue
            apkName = name
            val url = a.optString("browser_download_url")
            apkUrl = url.takeIf {
                it.startsWith("https://") || (allowInsecureHttp && it.startsWith("http://"))
            }
            apkSize = a.optLong("size")
            digest = a.optString("digest").takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")?.lowercase()
        }
        if (apkName == null || apkUrl == null) return null
        if (!isNewer(version, currentVersion)) return null

        return UpdateInfo(
            version = version,
            notes = root.optString("body").orEmpty().trim(),
            apkUrl = apkUrl,
            apkSize = apkSize,
            sha256 = digest,
            immutable = root.optBoolean("immutable", false),
            publishedAt = root.optString("published_at").orEmpty()
        )
    }
}
