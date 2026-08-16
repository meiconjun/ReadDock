package com.readdock.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetworkGatewayTest {
    private val fastPolicy = NetworkRequestPolicy(
        requestsPerMinute = 60_000_000,
        concurrency = 1,
        maxAttempts = 3,
        cacheTtlMs = 10_000,
        retryBaseDelayMs = 0,
        circuitFailureThreshold = 2,
        circuitCooldownMs = 10_000
    )

    @Test
    fun `retries transient failures and returns success`() = runBlocking {
        var calls = 0
        val gateway = NetworkGateway(NetworkTransport {
            calls += 1
            if (calls == 1) NetworkResponse(503) else NetworkResponse(200, body = "ok")
        }, sleeper = {})

        val result = gateway.get(NetworkRequest("https://example.com/comic"), fastPolicy)

        assertIs<GatewayResult.Success>(result)
        assertEquals("ok", result.response.body)
        assertEquals(2, calls)
    }

    @Test
    fun `serves repeated requests from cache`() = runBlocking {
        var calls = 0
        val gateway = NetworkGateway(NetworkTransport {
            calls += 1
            NetworkResponse(200, body = "cached")
        }, sleeper = {})
        val request = NetworkRequest("https://example.com/cached")

        val first = gateway.get(request, fastPolicy)
        val second = gateway.get(request, fastPolicy)

        assertIs<GatewayResult.Success>(first)
        assertIs<GatewayResult.Success>(second)
        assertEquals(false, first.fromCache)
        assertEquals(true, second.fromCache)
        assertEquals(1, calls)
    }

    @Test
    fun `opens circuit after repeated transient failures`() = runBlocking {
        var calls = 0
        val gateway = NetworkGateway(NetworkTransport {
            calls += 1
            NetworkResponse(503, body = "unavailable")
        }, sleeper = {})
        val request = NetworkRequest("https://example.com/unavailable")

        val first = gateway.get(request, fastPolicy)
        val second = gateway.get(request, fastPolicy)
        val third = gateway.get(request, fastPolicy)

        assertIs<GatewayResult.HttpFailure>(first)
        assertIs<GatewayResult.HttpFailure>(second)
        assertIs<GatewayResult.CircuitOpen>(third)
        assertEquals(6, calls)
    }

    @Test
    fun `does not retry ordinary client errors`() = runBlocking {
        var calls = 0
        val gateway = NetworkGateway(NetworkTransport {
            calls += 1
            NetworkResponse(404, body = "missing")
        }, sleeper = {})

        val result = gateway.get(NetworkRequest("https://example.com/missing"), fastPolicy)

        assertIs<GatewayResult.HttpFailure>(result)
        assertEquals(1, result.attempts)
        assertEquals(1, calls)
    }

    @Test
    fun `keeps binary request metadata for bounded image responses`() = runBlocking {
        var captured: NetworkRequest? = null
        val gateway = NetworkGateway(NetworkTransport { request ->
            captured = request
            NetworkResponse(200, bodyBytes = byteArrayOf(1, 2, 3))
        }, sleeper = {})

        val request = NetworkRequest(
            url = "https://example.com/page.jpg",
            bodyMode = NetworkBodyMode.BINARY,
            maxResponseBytes = 128
        )
        val result = gateway.get(request, fastPolicy)

        assertIs<GatewayResult.Success>(result)
        assertEquals(NetworkBodyMode.BINARY, captured?.bodyMode)
        assertEquals(128L, captured?.maxResponseBytes)
        assertEquals(byteArrayOf(1, 2, 3).toList(), result.response.bodyBytes?.toList())
    }
}
