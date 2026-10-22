package com.otoradio.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memuat logo stasiun yang dibundel di assets/logos/<id>.png (lihat tools/fetch_logos.py).
 * Logo di-decode sekali lalu disimpan di LruCache agar daftar tetap mulus.
 * Bila tidak ada logo, UI memakai tile gradien + inisial.
 */
@Singleton
class LogoStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val cache = object : LruCache<String, ImageBitmap>(64) {}

    suspend fun load(assetPath: String?): ImageBitmap? {
        if (assetPath.isNullOrBlank()) return null
        cache.get(assetPath)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    val bmp: Bitmap = BitmapFactory.decodeStream(input, null, opts) ?: return@use null
                    bmp.asImageBitmap().also { cache.put(assetPath, it) }
                }
            }.getOrNull()
        }
    }

    /** Dipakai service untuk artwork notifikasi (butuh android.graphics.Bitmap). */
    fun loadBitmapBlocking(assetPath: String?): Bitmap? {
        if (assetPath.isNullOrBlank()) return null
        return runCatching {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
