package com.readdock.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginRepositoryClientTest {
    @Test
    fun `fetches index and verifies downloaded plugin hash`() = runBlocking {
        val packageJson = "{\"plugin\":\"fixture\"}"
        val entry = PluginIndexEntry(
            id = "com.example.remote",
            name = "Remote Fixture",
            version = "1.0.0",
            downloadUrl = "https://repo.example/remote.json",
            sha256 = sha256Hex(packageJson.toByteArray())
        )
        val indexJson = """
            {
              "schemaVersion": 1,
              "repositoryId": "fixture",
              "plugins": [
                {
                  "id": "${entry.id}",
                  "name": "${entry.name}",
                  "version": "${entry.version}",
                  "downloadUrl": "${entry.downloadUrl}",
                  "sha256": "${entry.sha256}"
                }
              ]
            }
        """.trimIndent()
        val gateway = NetworkGateway(NetworkTransport { request ->
            if (request.url.endsWith("index.json")) {
                NetworkResponse(200, body = indexJson)
            } else {
                NetworkResponse(200, body = packageJson)
            }
        }, sleeper = {})
        val client = PluginRepositoryClient(
            gateway = gateway,
            indexLoader = PluginRepositoryIndexLoader()
        )

        val indexResult = client.fetchIndex("https://repo.example/index.json")
        val index = assertIs<RepositoryClientResult.IndexReady>(indexResult).index
        val downloadResult = client.downloadPlugin(index.plugins.single())

        val ready = assertIs<RepositoryClientResult.PluginReady>(downloadResult)
        assertEquals(packageJson, ready.packageJson)
    }

    @Test
    fun `rejects a plugin with a mismatched hash`() = runBlocking {
        val gateway = NetworkGateway(NetworkTransport {
            NetworkResponse(200, body = "tampered")
        }, sleeper = {})
        val client = PluginRepositoryClient(gateway, PluginRepositoryIndexLoader())
        val entry = PluginIndexEntry(
            id = "com.example.tampered",
            name = "Tampered",
            version = "1.0.0",
            downloadUrl = "https://repo.example/tampered.json",
            sha256 = "0".repeat(64)
        )

        val result = client.downloadPlugin(entry)

        val failure = assertIs<RepositoryClientResult.Failure>(result)
        assertEquals(true, failure.message.contains("SHA-256"))
    }

    @Test
    fun `uses raw response bytes for plugin hash and text`() = runBlocking {
        val packageJson = "{\"name\":\"漫画插件\"}"
        val rawBytes = packageJson.toByteArray(Charsets.UTF_8)
        val entry = PluginIndexEntry(
            id = "com.example.bytes",
            name = "Byte Fixture",
            version = "1.0.0",
            downloadUrl = "https://repo.example/bytes.json",
            sha256 = sha256Hex(rawBytes)
        )
        val gateway = NetworkGateway(NetworkTransport {
            NetworkResponse(200, body = "损坏的文本", bodyBytes = rawBytes)
        }, sleeper = {})
        val client = PluginRepositoryClient(gateway, PluginRepositoryIndexLoader())

        val result = client.downloadPlugin(entry)

        val ready = assertIs<RepositoryClientResult.PluginReady>(result)
        assertEquals(packageJson, ready.packageJson)
    }
}
