package com.comichub.source.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PluginRepositoryIndex(
    val schemaVersion: Int = 1,
    val repositoryId: String,
    val plugins: List<PluginIndexEntry> = emptyList()
)

@Serializable
data class PluginIndexEntry(
    val id: String,
    val name: String,
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val description: String? = null
)

data class PluginUpdate(
    val installed: InstalledPluginInfo,
    val available: PluginIndexEntry
)

sealed interface PluginRepositoryParseResult {
    data class Success(val index: PluginRepositoryIndex) : PluginRepositoryParseResult
    data class Failure(val errors: List<String>) : PluginRepositoryParseResult
}

class PluginRepositoryIndexLoader(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val signatureVerifier: PluginSignatureVerifier? = null,
    private val requireSignature: Boolean = false
) {
    fun parse(indexJson: String): PluginRepositoryParseResult {
        val payload = when (val result = SignedPayloadReader(signatureVerifier, requireSignature)
            .read(indexJson, json)) {
            is SignedPayloadResult.Rejected -> return PluginRepositoryParseResult.Failure(result.errors)
            is SignedPayloadResult.Ready -> result.payload
        }
        val index = runCatching { json.decodeFromString<PluginRepositoryIndex>(payload) }
            .getOrElse {
                return PluginRepositoryParseResult.Failure(listOf("插件仓库索引格式错误"))
            }
        val errors = buildList {
            if (index.schemaVersion != 1) add("不支持的仓库索引版本：${index.schemaVersion}")
            index.plugins.forEach { plugin ->
                if (!plugin.id.matches(Regex("[a-zA-Z0-9_.-]+"))) {
                    add("插件 id 无效：${plugin.id}")
                }
                if (!plugin.downloadUrl.startsWith("https://")) {
                    add("插件下载地址必须使用 HTTPS：${plugin.id}")
                }
                if (!plugin.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                    add("插件 SHA-256 无效：${plugin.id}")
                }
            }
        }
        return if (errors.isEmpty()) {
            PluginRepositoryParseResult.Success(index)
        } else {
            PluginRepositoryParseResult.Failure(errors)
        }
    }

    fun updates(
        index: PluginRepositoryIndex,
        installed: List<InstalledPluginInfo>
    ): List<PluginUpdate> {
        val installedById = installed.associateBy { it.id }
        return index.plugins.mapNotNull { available ->
            val current = installedById[available.id] ?: return@mapNotNull null
            if (compareVersions(available.version, current.version) > 0) {
                PluginUpdate(current, available)
            } else {
                null
            }
        }
    }
}

fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split(".", "-", limit = 4)
    val rightParts = right.split(".", "-", limit = 4)
    for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val a = leftParts.getOrNull(index) ?: "0"
        val b = rightParts.getOrNull(index) ?: "0"
        val numericA = a.toIntOrNull()
        val numericB = b.toIntOrNull()
        val comparison = if (numericA != null && numericB != null) {
            numericA.compareTo(numericB)
        } else {
            a.compareTo(b)
        }
        if (comparison != 0) return comparison
    }
    return 0
}
