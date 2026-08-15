package com.comichub.source.runtime

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalPluginStoreTest {
    @Test
    fun `installs toggles and uninstalls a plugin`() = runBlocking {
        val directory = Files.createTempDirectory("comichub-plugins").toFile()
        try {
            val store = LocalPluginStore(directory)
            val installed = store.install(validPackageJson)

            val info = assertIs<PluginStoreResult.Installed>(installed).plugin
            assertEquals("com.example.local", info.id)
            assertEquals(true, info.enabled)
            assertEquals(1, store.list().size)

            store.setEnabled(info.id, false)
            assertEquals(false, store.list().single().enabled)
            assertEquals(
                0,
                store.loadEnabled { _, _ -> "" }.sources.size
            )

            store.setEnabled(info.id, true)
            assertEquals(1, store.loadEnabled { _, _ -> "<h1>fixture</h1>" }.sources.size)

            store.uninstall(info.id)
            assertEquals(0, store.list().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects invalid packages before writing files`() {
        val directory = Files.createTempDirectory("comichub-invalid").toFile()
        try {
            val store = LocalPluginStore(directory)
            val result = store.install("{\"manifest\":{\"id\":\"bad plugin\"}}")

            assertIs<PluginStoreResult.Rejected>(result)
            assertEquals(0, store.list().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `secure install rejects unsigned packages`() {
        val directory = Files.createTempDirectory("comichub-secure").toFile()
        try {
            val store = LocalPluginStore(directory)
            val result = store.install(
                validPackageJson,
                PluginPackageLoader(requireSignature = true)
            )

            assertIs<PluginStoreResult.Rejected>(result)
            assertEquals(0, store.list().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `keeps previous versions and rolls back the latest update`() {
        val directory = Files.createTempDirectory("comichub-rollback").toFile()
        try {
            val store = LocalPluginStore(directory)
            val first = assertIs<PluginStoreResult.Installed>(store.install(validPackageJson)).plugin
            val secondPackage = validPackageJson.replace("1.0.0", "2.0.0")

            val updated = assertIs<PluginStoreResult.Installed>(store.install(secondPackage)).plugin
            assertEquals("2.0.0", updated.version)
            assertTrue(updated.canRollback)

            val rolledBack = assertIs<PluginStoreResult.RolledBack>(store.rollback(first.id)).plugin
            assertEquals("1.0.0", rolledBack.version)
            assertTrue(rolledBack.canRollback)
            assertEquals("1.0.0", store.list().single().version)
        } finally {
            directory.deleteRecursively()
        }
    }

    private val validPackageJson = """
        {
          "manifest": {
            "id": "com.example.local",
            "name": "Local Plugin",
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
