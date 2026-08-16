package com.comichub.source.runtime

import com.comichub.source.api.SourceManifest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

enum class NetworkBodyMode {
    TEXT,
    BINARY
}

data class NetworkRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val sourceId: String? = null,
    val bodyMode: NetworkBodyMode = NetworkBodyMode.TEXT,
    val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES
) {
    init {
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES: Long = 32L * 1024L * 1024L
    }
}

data class NetworkResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val bodyBytes: ByteArray? = null
)

fun interface NetworkTransport {
    suspend fun execute(request: NetworkRequest): NetworkResponse
}

data class NetworkRequestPolicy(
    val requestsPerMinute: Int = 20,
    val concurrency: Int = 2,
    val maxAttempts: Int = 3,
    val cacheTtlMs: Long = 30_000,
    val retryBaseDelayMs: Long = 250,
    val circuitFailureThreshold: Int = 3,
    val circuitCooldownMs: Long = 60_000
) {
    init {
        require(requestsPerMinute > 0) { "requestsPerMinute must be positive" }
        require(concurrency > 0) { "concurrency must be positive" }
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(cacheTtlMs >= 0) { "cacheTtlMs must not be negative" }
        require(retryBaseDelayMs >= 0) { "retryBaseDelayMs must not be negative" }
        require(circuitFailureThreshold > 0) { "circuitFailureThreshold must be positive" }
        require(circuitCooldownMs >= 0) { "circuitCooldownMs must not be negative" }
    }
}

fun SourceManifest.toNetworkRequestPolicy(
    maxAttempts: Int = 3,
    cacheTtlMs: Long = 30_000,
    retryBaseDelayMs: Long = 250
): NetworkRequestPolicy = NetworkRequestPolicy(
    requestsPerMinute = rateLimit.requestsPerMinute,
    concurrency = rateLimit.concurrency,
    maxAttempts = maxAttempts,
    cacheTtlMs = cacheTtlMs,
    retryBaseDelayMs = retryBaseDelayMs
)

sealed interface GatewayResult {
    data class Success(
        val response: NetworkResponse,
        val fromCache: Boolean
    ) : GatewayResult

    data class HttpFailure(
        val statusCode: Int,
        val body: String,
        val attempts: Int
    ) : GatewayResult

    data class TransportFailure(
        val message: String,
        val attempts: Int
    ) : GatewayResult

    data class CircuitOpen(
        val host: String,
        val retryAfterMs: Long
    ) : GatewayResult
}

/**
 * Shared network policy for all source plugins.
 *
 * The gateway deliberately handles compatibility and stability only. It does
 * not rotate IPs, spoof fingerprints, automate challenges, or bypass access
 * controls.
 */
class NetworkGateway(
    private val transport: NetworkTransport,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val healthTracker: SourceHealthTracker = SourceHealthTracker(clock)
) {
    private data class CacheEntry(
        val response: NetworkResponse,
        val expiresAt: Long
    )

    private class HostState(concurrency: Int) {
        val semaphore = Semaphore(concurrency)
        val timingMutex = Mutex()
        var nextAllowedAt: Long = 0
        var consecutiveFailures: Int = 0
        var circuitOpenUntil: Long = 0
    }

    private val hostStates = ConcurrentHashMap<String, HostState>()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    val sourceHealth: StateFlow<Map<String, SourceHealthSnapshot>> = healthTracker.snapshots

    suspend fun get(
        request: NetworkRequest,
        policy: NetworkRequestPolicy = NetworkRequestPolicy()
    ): GatewayResult {
        val startedAt = clock()
        val host = runCatching { URI(request.url).host }.getOrNull()
            ?: return GatewayResult.TransportFailure("URL 缺少有效域名", 0).also {
                healthTracker.record(
                    sourceId = request.sourceId ?: "unknown",
                    host = "unknown",
                    url = request.url,
                    outcome = RequestOutcome.TRANSPORT_FAILURE,
                    statusCode = null,
                    attempts = 0,
                    startedAt = startedAt,
                    message = it.message
                )
            }
        val sourceId = request.sourceId ?: host
        val state = hostStates.computeIfAbsent(host) { HostState(policy.concurrency) }
        val now = clock()

        val cacheable = request.headers.isEmpty()
        if (cacheable) {
            cache[request.url]?.let { entry ->
                if (entry.expiresAt > now) {
                    return GatewayResult.Success(entry.response, fromCache = true).also {
                        healthTracker.record(
                            sourceId = sourceId,
                            host = host,
                            url = request.url,
                            outcome = RequestOutcome.CACHE_HIT,
                            statusCode = entry.response.statusCode,
                            attempts = 0,
                            startedAt = startedAt,
                            fromCache = true
                        )
                    }
                }
                cache.remove(request.url, entry)
            }
        }

        val circuit = state.timingMutex.withLock {
            if (state.circuitOpenUntil > now) state.circuitOpenUntil - now else 0
        }
        if (circuit > 0) {
            return GatewayResult.CircuitOpen(host, circuit).also {
                healthTracker.record(
                    sourceId = sourceId,
                    host = host,
                    url = request.url,
                    outcome = RequestOutcome.CIRCUIT_OPEN,
                    statusCode = null,
                    attempts = 0,
                    startedAt = startedAt,
                    message = "源暂时冷却"
                )
            }
        }

        return state.semaphore.withPermit {
            var attempts = 0
            var lastRetryableStatus: Int? = null
            var lastBody = ""

            while (attempts < policy.maxAttempts) {
                attempts += 1
                awaitRateSlot(state, policy.requestsPerMinute)

                val response = try {
                    transport.execute(request)
                } catch (error: Exception) {
                    if (attempts < policy.maxAttempts) {
                        sleeper(backoff(policy, attempts))
                        continue
                    }
                    recordFailure(state, policy)
                    return@withPermit GatewayResult.TransportFailure(
                        error.message ?: "网络请求失败",
                        attempts
                    ).also {
                        healthTracker.record(
                            sourceId = sourceId,
                            host = host,
                            url = request.url,
                            outcome = RequestOutcome.TRANSPORT_FAILURE,
                            statusCode = null,
                            attempts = attempts,
                            startedAt = startedAt,
                            message = it.message
                        )
                    }
                }

                lastBody = response.body
                when {
                    response.statusCode in 200..299 -> {
                        recordSuccess(state)
                        if (cacheable && policy.cacheTtlMs > 0) {
                            cache[request.url] = CacheEntry(
                                response = response,
                                expiresAt = clock() + policy.cacheTtlMs
                            )
                        }
                        return@withPermit GatewayResult.Success(response, fromCache = false).also {
                            healthTracker.record(
                                sourceId = sourceId,
                                host = host,
                                url = request.url,
                                outcome = RequestOutcome.SUCCESS,
                                statusCode = response.statusCode,
                                attempts = attempts,
                                startedAt = startedAt
                            )
                        }
                    }

                    response.statusCode == 429 || response.statusCode >= 500 -> {
                        lastRetryableStatus = response.statusCode
                        if (attempts < policy.maxAttempts) {
                            sleeper(retryDelay(response, policy, attempts))
                            continue
                        }
                        recordFailure(state, policy)
                        return@withPermit GatewayResult.HttpFailure(
                            statusCode = lastRetryableStatus,
                            body = lastBody,
                            attempts = attempts
                        ).also {
                            healthTracker.record(
                                sourceId = sourceId,
                                host = host,
                                url = request.url,
                                outcome = RequestOutcome.HTTP_FAILURE,
                                statusCode = lastRetryableStatus,
                                attempts = attempts,
                                startedAt = startedAt,
                                message = "HTTP $lastRetryableStatus"
                            )
                        }
                    }

                    else -> {
                        return@withPermit GatewayResult.HttpFailure(
                            statusCode = response.statusCode,
                            body = response.body,
                            attempts = attempts
                        ).also {
                            healthTracker.record(
                                sourceId = sourceId,
                                host = host,
                                url = request.url,
                                outcome = RequestOutcome.HTTP_FAILURE,
                                statusCode = response.statusCode,
                                attempts = attempts,
                                startedAt = startedAt,
                                message = "HTTP ${response.statusCode}"
                            )
                        }
                    }
                }
            }

            recordFailure(state, policy)
            GatewayResult.HttpFailure(
                statusCode = lastRetryableStatus ?: -1,
                body = lastBody,
                attempts = attempts
            ).also {
                healthTracker.record(
                    sourceId = sourceId,
                    host = host,
                    url = request.url,
                    outcome = RequestOutcome.HTTP_FAILURE,
                    statusCode = lastRetryableStatus ?: -1,
                    attempts = attempts,
                    startedAt = startedAt,
                    message = "HTTP ${lastRetryableStatus ?: -1}"
                )
            }
        }
    }

    private suspend fun awaitRateSlot(state: HostState, requestsPerMinute: Int) {
        val interval = max(1L, 60_000L / requestsPerMinute)
        val waitMs = state.timingMutex.withLock {
            val now = clock()
            val wait = max(0L, state.nextAllowedAt - now)
            state.nextAllowedAt = max(now, state.nextAllowedAt) + interval
            wait
        }
        if (waitMs > 0) sleeper(waitMs)
    }

    private suspend fun recordSuccess(state: HostState) {
        state.timingMutex.withLock {
            state.consecutiveFailures = 0
            state.circuitOpenUntil = 0
        }
    }

    private suspend fun recordFailure(state: HostState, policy: NetworkRequestPolicy) {
        state.timingMutex.withLock {
            state.consecutiveFailures += 1
            if (state.consecutiveFailures >= policy.circuitFailureThreshold) {
                state.circuitOpenUntil = clock() + policy.circuitCooldownMs
                state.consecutiveFailures = 0
            }
        }
    }

    private fun retryDelay(
        response: NetworkResponse,
        policy: NetworkRequestPolicy,
        attempt: Int
    ): Long {
        val retryAfter = response.headers.entries
            .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
            ?.value
            ?.toLongOrNull()
            ?.times(1_000L)
        return min(retryAfter ?: backoff(policy, attempt), 10_000L)
    }

    private fun backoff(policy: NetworkRequestPolicy, attempt: Int): Long {
        val multiplier = 1L shl (attempt - 1).coerceAtMost(6)
        return min(policy.retryBaseDelayMs * multiplier, 10_000L)
    }
}
