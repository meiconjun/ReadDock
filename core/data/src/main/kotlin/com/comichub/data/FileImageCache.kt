package com.comichub.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Small file-backed image cache with deterministic keys and LRU-style eviction. */
class FileImageCache(
    private val directory: File,
    private val maxBytes: Long = 256L * 1024L * 1024L
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        directory.mkdirs()
    }

    @Synchronized
    fun get(url: String): ByteArray? {
        val file = fileFor(url)
        if (!file.isFile) return null
        file.setLastModified(System.currentTimeMillis())
        return file.readBytes()
    }

    @Synchronized
    fun put(url: String, bytes: ByteArray) {
        val temporary = File.createTempFile("image-", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            moveIntoPlace(temporary, fileFor(url))
            trimToSize()
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    @Synchronized
    fun clear() {
        directory.listFiles { file -> file.isFile && file.extension == "img" }
            ?.forEach(File::delete)
    }

    @Synchronized
    fun sizeBytes(): Long = directory
        .listFiles { file -> file.isFile && file.extension == "img" }
        ?.sumOf(File::length)
        ?: 0L

    private fun trimToSize() {
        var currentSize = sizeBytes()
        if (currentSize <= maxBytes) return
        val files = directory
            .listFiles { file -> file.isFile && file.extension == "img" }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        for (file in files) {
            if (currentSize <= maxBytes) break
            val length = file.length()
            if (file.delete()) currentSize -= length
        }
    }

    private fun fileFor(url: String): File = File(directory, "${sha256(url)}.img")

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
