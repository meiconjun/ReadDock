package com.comichub.source.api

data class PluginPackage(
    val manifest: SourceManifest,
    val entrypoint: String,
    val signature: String? = null
)

data class PluginValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
)

object PluginManifestValidator {
    fun validate(manifest: SourceManifest): PluginValidationResult {
        val errors = buildList {
            if (!manifest.id.matches(Regex("[a-zA-Z0-9_.-]+"))) {
                add("插件 id 只能包含字母、数字、下划线、点和短横线")
            }
            if (manifest.name.isBlank()) add("插件名称不能为空")
            if (manifest.apiVersion < 1) add("不支持的插件 API 版本")
            if (!manifest.baseUrl.startsWith("https://")) add("baseUrl 必须使用 HTTPS")
            if (manifest.domains.isEmpty()) add("至少声明一个允许访问的域名")
            if (manifest.rateLimit.requestsPerMinute <= 0) add("请求速率必须大于 0")
            if (manifest.rateLimit.concurrency <= 0) add("并发数必须大于 0")
        }
        return PluginValidationResult(errors.isEmpty(), errors)
    }
}
