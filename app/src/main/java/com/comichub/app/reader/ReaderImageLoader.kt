package com.comichub.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.comichub.data.FileImageCache
import com.comichub.data.ImageDownloadQueue
import java.io.File

class ReaderImageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Bounded decoded-image cache shared by the local and online readers.
 * Compressed remote bytes stay on disk; only a small number of sampled Bitmaps
 * are retained in heap memory.
 */
class ReaderBitmapCache(
    maxBytes: Int = 24 * 1024 * 1024
) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(value.byteCount)
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: String, bitmap: Bitmap): Bitmap = cache.put(key, bitmap)?.let { bitmap } ?: bitmap

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}

class SampledBitmapDecoder(
    private val bitmapCache: ReaderBitmapCache = ReaderBitmapCache(),
    private val maxWidth: Int = 2048,
    private val maxHeight: Int = 3072,
    private val maxPixels: Long = 6_000_000L
) {
    fun decodeFile(key: String, file: File): Bitmap {
        bitmapCache.get(key)?.let { return it }
        if (!file.isFile) throw ReaderImageException("图片文件不存在")
        return decode(key) { options -> BitmapFactory.decodeFile(file.absolutePath, options) }
    }

    fun decodeBytes(key: String, bytes: ByteArray): Bitmap {
        bitmapCache.get(key)?.let { return it }
        if (bytes.isEmpty()) throw ReaderImageException("图片内容为空")
        return decode(key) { options -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
    }

    fun clear() = bitmapCache.clear()

    fun clearKey(key: String) = bitmapCache.remove(key)

    private fun decode(key: String, decoder: (BitmapFactory.Options) -> Bitmap?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decoder(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ReaderImageException("图片损坏或格式不受支持")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
            inScaled = false
        }
        val bitmap = try {
            decoder(options)
        } catch (error: OutOfMemoryError) {
            bitmapCache.clear()
            throw ReaderImageException("图片过大，无法安全解码", error)
        } ?: throw ReaderImageException("图片解码失败")
        return bitmapCache.put(key, bitmap)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (
            width.toLong() / sample > maxWidth ||
            height.toLong() / sample > maxHeight ||
            (width.toLong() / sample) * (height.toLong() / sample) > maxPixels
        ) {
            sample *= 2
        }
        return sample
    }
}

/** Loads one online page at a time and never exposes a chapter-sized ByteArray map. */
class OnlineReaderImageLoader(
    private val cache: FileImageCache,
    private val queue: ImageDownloadQueue,
    private val decoder: SampledBitmapDecoder = SampledBitmapDecoder()
) {
    suspend fun load(url: String): Bitmap {
        val file = cache.getFile(url) ?: run {
            val result = queue.downloadOne(url)
            if (result.status != com.comichub.data.DownloadStatus.COMPLETED) {
                throw ReaderImageException(result.error ?: "图片下载失败")
            }
            cache.getFile(url)
        } ?: throw ReaderImageException("图片缓存写入失败")
        return decoder.decodeFile(url, file)
    }

    fun clearMemory() = decoder.clear()

    fun clearPage(url: String) = decoder.clearKey(url)
}
