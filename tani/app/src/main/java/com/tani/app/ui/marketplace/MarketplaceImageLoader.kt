package com.tani.app.ui.marketplace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import com.tani.app.R
import com.tani.app.data.Supabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight image pipeline for marketplace cards.
 *
 * - Memory cache for hot images.
 * - Disk cache for weak/offline connections.
 * - One shared in-flight request per URL.
 * - Request tagging so a late response can never overwrite a reused ImageView.
 */
object MarketplaceImageLoader {
    private const val MAX_DECODE_DIMENSION = 900
    private const val MAX_DISK_CACHE_BYTES = 80L * 1024L * 1024L
    private const val MAX_SINGLE_IMAGE_BYTES = 12 * 1024 * 1024
    private const val CACHE_DIR = "marketplace_images_v1"

    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    private val memoryCache = object : android.util.LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun load(scope: LifecycleCoroutineScope, imageView: ImageView, rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return

        // Reset immediately; this avoids retaining an old bitmap while a recycled/reused view loads.
        imageView.tag = url
        imageView.setImageResource(R.drawable.ic_image_placeholder)

        memoryCache.get(url)?.let { bitmap ->
            if (imageView.tag == url) imageView.setImageBitmap(bitmap)
            return
        }

        val appContext = imageView.context.applicationContext
        scope.launch {
            val bitmap = requestBitmap(appContext, url).await() ?: return@launch
            // The view may have been rebound to another product while this request was running.
            if (imageView.tag == url) imageView.setImageBitmap(bitmap)
        }
    }

    private fun requestBitmap(context: Context, url: String): Deferred<Bitmap?> {
        inFlight[url]?.let { return it }

        // LAZY is important: a racing duplicate never starts network/disk work before
        // putIfAbsent decides which single request owns this URL.
        val created = loaderScope.async(start = CoroutineStart.LAZY) {
            memoryCache.get(url)?.let { return@async it }

            val diskBytes = readDisk(context, url)
            val bytes = diskBytes ?: Supabase.downloadPublicBytes(url)?.also {
                if (it.size <= MAX_SINGLE_IMAGE_BYTES) writeDisk(context, url, it)
            }

            if (bytes.isNullOrEmpty() || bytes.size > MAX_SINGLE_IMAGE_BYTES) return@async null

            val bitmap = withContext(Dispatchers.Default) {
                decodeSampledBitmap(bytes, MAX_DECODE_DIMENSION)
            } ?: return@async null

            memoryCache.put(url, bitmap)
            bitmap
        }

        val existing = inFlight.putIfAbsent(url, created)
        if (existing != null) {
            created.cancel()
            return existing
        }

        created.invokeOnCompletion { inFlight.remove(url, created) }
        created.start()
        return created
    }

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, sha256(url) + ".img")
    }

    private fun readDisk(context: Context, url: String): ByteArray? = runCatching {
        val file = cacheFile(context, url)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_SINGLE_IMAGE_BYTES) {
            if (file.exists() && file.length() > MAX_SINGLE_IMAGE_BYTES) file.delete()
            return@runCatching null
        }
        file.setLastModified(System.currentTimeMillis())
        file.readBytes()
    }.getOrNull()

    private fun writeDisk(context: Context, url: String, bytes: ByteArray) {
        runCatching {
            val target = cacheFile(context, url)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            trimDiskCache(target.parentFile ?: return@runCatching)
        }
    }

    private fun trimDiskCache(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_DISK_CACHE_BYTES) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= MAX_DISK_CACHE_BYTES) return
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
