package com.comichub.source.runtime

import com.comichub.source.api.ComicSource
import com.comichub.source.api.PluginManifestValidator
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

sealed interface PluginParseResult {
    data class Success(
        val definition: PluginSourceDefinition
    ) : PluginParseResult

    data class Failure(
        val errors: List<String>
    ) : PluginParseResult
}

sealed interface PluginLoadResult {
    data class Success(
        val source: ComicSource,
        val definition: PluginSourceDefinition
    ) : PluginLoadResult

    data class Failure(
        val errors: List<String>
    ) : PluginLoadResult
}

class PluginPackageLoader(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val signatureVerifier: PluginSignatureVerifier? = null,
    private val requireSignature: Boolean = false
) {
    fun parse(packageJson: String): PluginParseResult {
        val payload = when (val result = SignedPayloadReader(signatureVerifier, requireSignature)
            .read(packageJson, json)) {
            is SignedPayloadResult.Rejected -> return PluginParseResult.Failure(result.errors)
            is SignedPayloadResult.Ready -> result.payload
        }
        val root = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (error: SerializationException) {
            return PluginParseResult.Failure(
                listOf("插件 JSON 无法解析：${error.message ?: "格式错误"}")
            )
        }

        val definition: PluginSourceDefinition = try {
            if ("script" in root) {
                json.decodeFromJsonElement<JavaScriptSourceDefinition>(root)
            } else {
                json.decodeFromJsonElement<DeclarativeSourceDefinition>(root)
            }
        } catch (error: SerializationException) {
            return PluginParseResult.Failure(
                listOf("插件 JSON 无法解析：${error.message ?: "格式错误"}")
            )
        }

        val errors = buildList {
            addAll(PluginManifestValidator.validate(definition.manifest).errors)
            when (definition) {
                is DeclarativeSourceDefinition -> {
                    if (definition.search.pathTemplate.isBlank()) add("search.pathTemplate 不能为空")
                    if (definition.search.itemSelector.isBlank()) add("search.itemSelector 不能为空")
                    if (definition.detail.chapterItemSelector.isBlank()) {
                        add("detail.chapterItemSelector 不能为空")
                    }
                    if (definition.pages.pageSelector.isBlank()) add("pages.pageSelector 不能为空")
                }
                is JavaScriptSourceDefinition -> {
                    if (definition.script.isBlank()) add("script 不能为空")
                    if (definition.script.length > MAX_SCRIPT_LENGTH) {
                        add("script 超过 ${MAX_SCRIPT_LENGTH} 个字符")
                    }
                    if (definition.maxInstructions !in MIN_INSTRUCTIONS..MAX_INSTRUCTIONS) {
                        add("maxInstructions 必须在 $MIN_INSTRUCTIONS 到 $MAX_INSTRUCTIONS 之间")
                    }
                    if (FORBIDDEN_SCRIPT_TOKEN.containsMatchIn(definition.script)) {
                        add("script 包含未允许的动态执行或 Java 访问 API")
                    }
                }
            }
        }
        return if (errors.isEmpty()) {
            PluginParseResult.Success(definition)
        } else {
            PluginParseResult.Failure(errors)
        }
    }

    fun load(
        packageJson: String,
        fetchHtml: suspend (url: String) -> String
    ): PluginLoadResult {
        val definition = when (val parsed = parse(packageJson)) {
            is PluginParseResult.Failure -> return PluginLoadResult.Failure(parsed.errors)
            is PluginParseResult.Success -> parsed.definition
        }

        val source = when (definition) {
            is DeclarativeSourceDefinition -> DeclarativeSource(definition, fetchHtml)
            is JavaScriptSourceDefinition -> JavaScriptSource(definition, fetchHtml)
            else -> error("不支持的插件定义类型")
        }
        return PluginLoadResult.Success(
            source = source,
            definition = definition
        )
    }

    companion object {
        private const val MAX_SCRIPT_LENGTH = 128 * 1024
        private const val MIN_INSTRUCTIONS = 10_000
        private const val MAX_INSTRUCTIONS = 1_000_000
        private val FORBIDDEN_SCRIPT_TOKEN = Regex(
            "\\b(Packages|JavaAdapter|JavaImporter|importClass|importPackage|eval)\\b|\\bFunction\\s*\\("
        )
    }
}
