package com.comichub.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceHealthTest {
    @Test
    fun `tracks gateway outcomes and redacts query strings`() = runBlocking {
        var now = 1_000L
        var calls = 0
        val tracker = SourceHealthTracker(clock = { now }, maxRecentLogs = 2)
        val gateway = NetworkGateway(
            transport = NetworkTransport {
                calls += 1
                if (calls < 2) {
                    NetworkResponse(statusCode = 200, body = "ok")
                } else {
                    NetworkResponse(statusCode = 503, body = "busy")
                }
            },
            clock = { now },
            sleeper = {},
            healthTracker = tracker
        )
        val policy = NetworkRequestPolicy(
            requestsPerMinute = 60_000_000,
            maxAttempts = 1,
            cacheTtlMs = 10_000,
            circuitFailureThreshold = 5
        )

        gateway.get(
            NetworkRequest(
                url = "https://example.com/comic?q=private-value",
                sourceId = "example-source"
            ),
            policy
        )
        now += 5
        gateway.get(
            NetworkRequest(
                url = "https://example.com/comic?q=private-value",
                sourceId = "example-source"
            ),
            policy
        )
        now += 5
        gateway.get(
            NetworkRequest(
                url = "https://example.com/second",
                sourceId = "example-source"
            ),
            policy
        )

        val snapshot = tracker.snapshot("example-source")!!
        assertEquals(3, snapshot.requestCount)
        assertEquals(2, snapshot.successCount)
        assertEquals(1, snapshot.failureCount)
        assertEquals(1, snapshot.cacheHitCount)
        assertEquals(RequestOutcome.HTTP_FAILURE, snapshot.lastOutcome)
        assertEquals(503, snapshot.lastStatusCode)
        assertEquals(2, snapshot.recentLogs.size)
        assertTrue(snapshot.recentLogs.all { "?" !in it.path })
        assertTrue(snapshot.recentLogs.any { it.path == "/second" })
        assertFalse(snapshot.recentLogs.any { "private-value" in it.path })
    }
}
