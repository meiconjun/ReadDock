package com.readdock.source.runtime

import java.nio.charset.StandardCharsets

sealed interface RepositoryClientResult {
    data class IndexReady(
        val url: String,
        val index: PluginRepositoryIndex
    ) : RepositoryClientResult

    data class PluginReady(
        val entry: PluginIndexEntry,
        val packageJson: String
    ) : RepositoryClientResult

    data class Failure(val message: String) : RepositoryClientResult
}

class PluginRepositoryClient(
    private val gateway: NetworkGateway,
    private val indexLoader: PluginRepositoryIndexLoader
) {
    suspend fun fetchIndex(
        url: String,
        policy: NetworkRequestPolicy = NetworkRequestPolicy(cacheTtlMs = 60_000)
    ): RepositoryClientResult {
        return when (val result = gateway.get(NetworkRequest(url), policy)) {
            is GatewayResult.Success -> when (val parsed = indexLoader.parse(result.response.body)) {
                is PluginRepositoryParseResult.Success -> RepositoryClientResult.IndexReady(url, parsed.index)
                is PluginRepositoryParseResult.Failure -> {
                    RepositoryClientResult.Failure(parsed.errors.joinToString("；"))
                }
            }
            is GatewayResult.HttpFailure -> RepositoryClientResult.Failure("仓库索引 HTTP ${result.statusCode}")
            is GatewayResult.TransportFailure -> RepositoryClientResult.Failure(result.message)
            is GatewayResult.CircuitOpen -> RepositoryClientResult.Failure("仓库暂时冷却，请稍后重试")
        }
    }

    suspend fun downloadPlugin(
        entry: PluginIndexEntry,
        policy: NetworkRequestPolicy = NetworkRequestPolicy(cacheTtlMs = 0)
    ): RepositoryClientResult {
        return when (val result = gateway.get(NetworkRequest(entry.downloadUrl), policy)) {
            is GatewayResult.Success -> {
                val bodyBytes = result.response.bodyBytes
                    ?: result.response.body.toByteArray(StandardCharsets.UTF_8)
                if (!verifySha256(bodyBytes, entry.sha256)) {
                    RepositoryClientResult.Failure("插件 SHA-256 校验失败：${entry.id}")
                } else {
                    RepositoryClientResult.PluginReady(
                        entry,
                        bodyBytes.toString(StandardCharsets.UTF_8)
                    )
                }
            }
            is GatewayResult.HttpFailure -> RepositoryClientResult.Failure("插件下载 HTTP ${result.statusCode}")
            is GatewayResult.TransportFailure -> RepositoryClientResult.Failure(result.message)
            is GatewayResult.CircuitOpen -> RepositoryClientResult.Failure("插件源暂时冷却，请稍后重试")
        }
    }
}
