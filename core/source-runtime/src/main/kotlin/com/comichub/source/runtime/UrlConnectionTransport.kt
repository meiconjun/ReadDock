package com.comichub.source.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val bodyBytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val body = bodyBytes.toString(Charsets.UTF_8)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.orEmpty() }
                .mapValues { it.value.joinToString(",") }
            NetworkResponse(statusCode, headers, body, bodyBytes)
        } finally {
            connection.disconnect()
        }
    }
}
