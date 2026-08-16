package com.readdock.source.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginRepositoryTest {
    @Test
    fun `parses index and finds newer installed plugin`() {
        val index = """
            {
              "schemaVersion": 1,
              "repositoryId": "readdock.official",
              "plugins": [
                {
                  "id": "com.example.local",
                  "name": "Local Plugin",
                  "version": "1.2.0",
                  "downloadUrl": "https://example.com/plugins/local.json",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
              ]
            }
        """.trimIndent()

        val parsed = PluginRepositoryIndexLoader().parse(index)
        val success = assertIs<PluginRepositoryParseResult.Success>(parsed)
        val updates = PluginRepositoryIndexLoader().updates(
            success.index,
            listOf(InstalledPluginInfo("com.example.local", "Local Plugin", "1.0.0", true))
        )

        assertEquals(1, updates.size)
        assertEquals("1.2.0", updates.single().available.version)
    }

    @Test
    fun `rejects insecure repository entries`() {
        val result = PluginRepositoryIndexLoader().parse(
            """
                {
                  "schemaVersion": 1,
                  "repositoryId": "bad",
                  "plugins": [{
                    "id": "bad plugin",
                    "name": "Bad",
                    "version": "1.0.0",
                    "downloadUrl": "http://example.com/plugin.json",
                    "sha256": "short"
                  }]
                }
            """.trimIndent()
        )

        val failure = assertIs<PluginRepositoryParseResult.Failure>(result)
        assertEquals(3, failure.errors.size)
    }

    @Test
    fun `verifies package bytes with SHA-256`() {
        val bytes = "ReadDock plugin".toByteArray()
        val hash = sha256Hex(bytes)

        assertEquals(true, verifySha256(bytes, hash.uppercase()))
        assertEquals(false, verifySha256(bytes, "0".repeat(64)))
    }

    @Test
    fun `compares semantic versions`() {
        assertEquals(true, compareVersions("1.2.0", "1.1.9") > 0)
        assertEquals(true, compareVersions("1.0.0", "1.0.0") == 0)
        assertEquals(true, compareVersions("1.0.0-beta", "1.0.0-alpha") > 0)
    }
}
