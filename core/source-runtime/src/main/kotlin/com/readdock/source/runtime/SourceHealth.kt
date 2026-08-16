package com.readdock.source.runtime

import java.net.URI
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RequestOutcome {
    SUCCESS,
    CACHE_HIT,
    HTTP_FAILURE,
    TRANSPORT_FAILURE,
    CIRCUIT_OPEN
}

data class SourceRequestLog(
    val sourceId: String,
    val host: String,
    val path: String,
    val outcome: RequestOutcome,
    val statusCode: Int?,
    val attempts: Int,
    val durationMs: Long,
    val message: String?
)

data class SourceHealthSnapshot(
    val sourceId: String,
    val host: String,
    val requestCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val cacheHitCount: Int,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val lastOutcome: RequestOutcome,
    val lastStatusCode: Int?,
    val lastLatencyMs: Long,
    val lastFailureMessage: String?,
    val recentLogs: List<SourceRequestLog>
) {
    val successRatePercent: Int
        get() = if (requestCount == 0) 0 else (successCount * 100) / requestCount
}

/**
 * In-memory observability for source requests. It intentionally keeps only a
 * small recent log per source; no request body or query string is retained.
 */
class SourceHealthTracker(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxRecentLogs: Int = 20
) {
    init {
        require(maxRecentLogs > 0) { "maxRecentLogs must be positive" }
    }

    private data class MutableStats(
        val sourceId: String,
        val host: String,
        var requestCount: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var cacheHitCount: Int = 0,
        var lastSuccessAt: Long? = null,
        var lastFailureAt: Long? = null,
        var lastOutcome: RequestOutcome = RequestOutcome.SUCCESS,
        var lastStatusCode: Int? = null,
        var lastLatencyMs: Long = 0,
        var lastFailureMessage: String? = null,
        val recentLogs: ArrayDeque<SourceRequestLog> = ArrayDeque()
    )

    private val lock = Any()
    private val statsBySource = LinkedHashMap<String, MutableStats>()
    private val _snapshots = MutableStateFlow<Map<String, SourceHealthSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, SourceHealthSnapshot>> = _snapshots.asStateFlow()

    fun record(
        sourceId: String,
        host: String,
        url: String,
        outcome: RequestOutcome,
        statusCode: Int?,
        attempts: Int,
        startedAt: Long,
        message: String? = null,
        fromCache: Boolean = false
    ) {
        val finishedAt = clock()
        val log = SourceRequestLog(
            sourceId = sourceId,
            host = host,
            path = sanitizePath(url),
            outcome = outcome,
            statusCode = statusCode,
            attempts = attempts,
            durationMs = (finishedAt - startedAt).coerceAtLeast(0),
            message = message
        )

        synchronized(lock) {
            val stats = statsBySource.getOrPut(sourceId) { MutableStats(sourceId, host) }
            stats.requestCount += 1
            stats.lastOutcome = outcome
            stats.lastStatusCode = statusCode
            stats.lastLatencyMs = log.durationMs
            if (outcome == RequestOutcome.SUCCESS || outcome == RequestOutcome.CACHE_HIT) {
                stats.successCount += 1
                stats.lastSuccessAt = finishedAt
            } else {
                stats.failureCount += 1
                stats.lastFailureAt = finishedAt
                stats.lastFailureMessage = message
            }
            if (fromCache || outcome == RequestOutcome.CACHE_HIT) stats.cacheHitCount += 1
            stats.recentLogs.addLast(log)
            while (stats.recentLogs.size > maxRecentLogs) stats.recentLogs.removeFirst()
            _snapshots.value = statsBySource.mapValues { (_, current) -> current.snapshot() }
        }
    }

    fun snapshot(sourceId: String): SourceHealthSnapshot? =
        snapshots.value[sourceId]

    private fun MutableStats.snapshot() = SourceHealthSnapshot(
        sourceId = sourceId,
        host = host,
        requestCount = requestCount,
        successCount = successCount,
        failureCount = failureCount,
        cacheHitCount = cacheHitCount,
        lastSuccessAt = lastSuccessAt,
        lastFailureAt = lastFailureAt,
        lastOutcome = lastOutcome,
        lastStatusCode = lastStatusCode,
        lastLatencyMs = lastLatencyMs,
        lastFailureMessage = lastFailureMessage,
        recentLogs = recentLogs.toList()
    )

    private fun sanitizePath(url: String): String =
        runCatching { URI(url).path?.ifBlank { "/" } ?: "/" }
            .getOrDefault("<invalid-url>")
}
