package com.comichub.source.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UrlConnectionTransport(
    private val userAgent: String = "ComicHub/0.1"
) : NetworkTransport {
    override suspend fun execute(request: NetworkRequest): NetworkResponse = withContext(Dispatchers.IO) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            val bodyBytes = stream?.use { input ->
                if (connection.contentLengthLong > request.maxResponseBytes) {
                    throw IOException(
                        "响应过大（${connection.contentLengthLong} bytes，限制 ${request.maxResponseBytes} bytes）"
                    )
                }
                readBounded(input, request.maxResponseBytes)
            } ?: ByteArray(0)
            // Binary responses must not create a second large UTF-8 String copy.
            val body = if (request.bodyMode == NetworkBodyMode.TEXT) {
                bodyBytes.toString(Charsets.UTF_8)
            } else {
                ""
            }
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.orEmpty() }
                .mapValues { it.value.joinToString(",") }
            NetworkResponse(statusCode, headers, body, bodyBytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64L * 1024L).toInt())
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw IOException("响应过大（超过 $maxBytes bytes）")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
