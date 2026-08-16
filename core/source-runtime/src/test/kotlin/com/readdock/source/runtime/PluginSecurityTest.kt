package com.readdock.source.runtime

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginSecurityTest {
    @Test
    fun `trusted RSA signature allows a signed plugin`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = validPackageJson
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(payload.toByteArray(StandardCharsets.UTF_8))
        }
        val signature = PluginSignature(
            algorithm = "SHA256withRSA",
            keyId = "test-key",
            value = Base64.getEncoder().encodeToString(signer.sign())
        )
        val envelope = Json.encodeToString(
            SignedPluginEnvelope(payload = payload, signature = signature)
        )
        val trustStore = PluginTrustStore(
            mapOf("test-key" to Base64.getEncoder().encodeToString(keyPair.public.encoded))
        )
        val loader = PluginPackageLoader(
            signatureVerifier = PluginSignatureVerifier(trustStore),
            requireSignature = true
        )

        val result = loader.load(envelope) { "" }

        val loaded = assertIs<PluginLoadResult.Success>(result)
        assertEquals("com.example.signed", loaded.source.manifest.id)
    }

    @Test
    fun `required signature rejects unsigned package`() {
        val loader = PluginPackageLoader(requireSignature = true)

        val result = loader.parse(validPackageJson)

        val failure = assertIs<PluginParseResult.Failure>(result)
        assertEquals(true, failure.errors.any { it.contains("签名") })
    }

    private val validPackageJson = """
        {
          "manifest": {
            "id": "com.example.signed",
            "name": "Signed Fixture",
            "version": "1.0.0",
            "apiVersion": 1,
            "baseUrl": "https://example.com",
            "domains": ["example.com"],
            "capabilities": ["search", "detail", "chapters", "pages"],
            "permissions": ["network"],
            "rateLimit": {"requestsPerMinute": 20, "concurrency": 1}
          },
          "search": {
            "pathTemplate": "/search?q={query}&page={page}",
            "itemSelector": ".comic",
            "title": {"css": ".title"},
            "url": {"css": "a", "attribute": "href"}
          },
          "detail": {
            "title": {"css": "h1"},
            "chapterItemSelector": ".chapter",
            "chapterTitle": {"css": ".title"},
            "chapterUrl": {"css": "a", "attribute": "href"}
          },
          "pages": {
            "pageSelector": "img",
            "image": {"css": "", "attribute": "src"}
          }
        }
    """.trimIndent()
}
