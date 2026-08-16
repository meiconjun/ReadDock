package com.readdock.source.runtime

import com.readdock.source.api.Chapter
import com.readdock.source.api.ComicDetail
import com.readdock.source.api.ComicPage
import com.readdock.source.api.ComicSource
import com.readdock.source.api.ComicSummary
import com.readdock.source.api.SourceManifest
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

@Serializable
data class JavaScriptSourceDefinition(
    override val manifest: SourceManifest,
    val script: String,
    val maxInstructions: Int = DEFAULT_MAX_INSTRUCTIONS
) : PluginSourceDefinition

class JavaScriptSource(
    private val definition: JavaScriptSourceDefinition,
    private val fetchHtml: suspend (url: String) -> String
) : ComicSource {
    override val manifest: SourceManifest = definition.manifest

    override suspend fun search(query: String, page: Int): List<ComicSummary> =
        execute("search", query, page) { value ->
            scriptArray(value).mapNotNull { item ->
                val url = propertyString(item, "url") ?: return@mapNotNull null
                ComicSummary(
                    id = resolveUrl(url),
                    sourceId = manifest.id,
                    title = propertyString(item, "title")?.ifBlank { "未命名漫画" } ?: "未命名漫画",
                    coverUrl = propertyString(item, "cover")?.let(::resolveUrl),
                    tags = propertyStrings(item, "tags")
                )
            }
        }

    override suspend fun detail(comicId: String): ComicDetail =
        execute("detail", comicId) { value ->
            val title = propertyString(value, "title")?.ifBlank { "未命名漫画" } ?: "未命名漫画"
            val chapters = scriptArray(property(value, "chapters")).mapIndexedNotNull { index, item ->
                val url = propertyString(item, "url") ?: return@mapIndexedNotNull null
                Chapter(
                    id = resolveUrl(url),
                    sourceId = manifest.id,
                    comicId = comicId,
                    title = propertyString(item, "title")?.ifBlank { "第 ${index + 1} 话" }
                        ?: "第 ${index + 1} 话",
                    number = propertyNumber(item, "number") ?: index + 1
                )
            }
            ComicDetail(
                summary = ComicSummary(
                    id = comicId,
                    sourceId = manifest.id,
                    title = title,
                    coverUrl = propertyString(value, "cover")?.let(::resolveUrl),
                    tags = propertyStrings(value, "tags")
                ),
                author = propertyString(value, "author").orEmpty(),
                description = propertyString(value, "description").orEmpty(),
                chapters = chapters
            )
        }

    override suspend fun pages(chapterId: String): List<ComicPage> =
        execute("pages", chapterId) { value ->
            scriptArray(value).mapIndexed { index, item ->
                ComicPage(
                    id = "$chapterId#${index + 1}",
                    chapterId = chapterId,
                    index = index + 1,
                    imageUrl = propertyString(item, "url")?.let(::resolveUrl),
                    displayText = propertyString(item, "text")
                )
            }
        }

    private suspend fun <T> execute(
        functionName: String,
        vararg arguments: Any,
        transform: (Any?) -> T
    ): T = withContext(Dispatchers.IO) {
        val factory = LimitedContextFactory(definition.maxInstructions)
        factory.call(ContextAction {
            val context = it
            val scope = context.initStandardObjects()
            context.evaluateString(scope, definition.script, "plugin:${manifest.id}", 1, null)
            val function = ScriptableObject.getProperty(scope, functionName) as? org.mozilla.javascript.Function
                ?: throw JavaScriptPluginException("脚本缺少 $functionName(ctx, value) 函数")
            val contextObject = createContext(scope)
            val result = function.call(context, scope, scope, arrayOf(contextObject, *arguments))
            transform(result)
        })
    }

    private fun createContext(scope: Scriptable): Scriptable {
        val contextObject = hostObject(scope)
        val http = hostObject(scope)
        val url = hostObject(scope)

        put(contextObject, "http", http)
        put(contextObject, "url", url)
        put(http, "get", function { args ->
            val rawUrl = argumentString(args, 0)
            runBlocking { fetchHtml(resolveUrl(rawUrl)) }
        })
        put(url, "encode", function { args ->
            URLEncoder.encode(argumentString(args, 0), StandardCharsets.UTF_8)
        })
        put(contextObject, "html", function { args ->
            HtmlDocumentBridge(Jsoup.parse(argumentString(args, 0), manifest.baseUrl), scope)
        })
        return contextObject
    }

    private fun hostObject(scope: Scriptable): NativeObject = NativeObject().apply {
        parentScope = scope
        prototype = ScriptableObject.getClassPrototype(scope, "Object")
    }

    private fun resolveUrl(value: String): String {
        val resolved = URI(manifest.baseUrl).resolve(value).toString()
        val uri = URI(resolved)
        val host = uri.host?.lowercase()
            ?: throw JavaScriptPluginException("请求 URL 缺少域名")
        if (uri.scheme != "https" || !manifest.domains.any { domain ->
                val normalized = domain.lowercase()
                host == normalized || host.endsWith(".$normalized")
            }
        ) {
            throw JavaScriptPluginException("脚本请求了未声明的 HTTPS 域名：$host")
        }
        return resolved
    }

    private fun scriptArray(value: Any?): List<Any?> {
        if (value !is NativeArray) {
            throw JavaScriptPluginException("脚本返回值必须是数组")
        }
        return (0 until value.length.toInt()).map { value.get(it, value) }
    }

    private fun property(value: Any?, name: String): Any? =
        if (value is Scriptable) ScriptableObject.getProperty(value, name) else null

    private fun propertyString(value: Any?, name: String): String? {
        val property = property(value, name)
        return when (property) {
            null, Undefined.instance, Scriptable.NOT_FOUND -> null
            else -> Context.toString(property)
        }
    }

    private fun propertyNumber(value: Any?, name: String): Int? =
        property(value, name).let { (it as? Number)?.toInt() }

    private fun propertyStrings(value: Any?, name: String): List<String> =
        scriptArrayOrEmpty(property(value, name)).mapNotNull { item ->
            when (item) {
                null, Undefined.instance, ScriptableObject.NOT_FOUND -> null
                else -> Context.toString(item)
            }
        }

    private fun scriptArrayOrEmpty(value: Any?): List<Any?> =
        if (value == null || value == Undefined.instance || value == Scriptable.NOT_FOUND) {
            emptyList()
        } else {
            scriptArray(value)
        }

    private fun argumentString(args: Array<out Any>, index: Int): String =
        args.getOrNull(index)?.let(Context::toString).orEmpty()

    private fun put(target: ScriptableObject, name: String, value: Any) {
        ScriptableObject.putProperty(target, name, value)
    }

    private fun function(block: (Array<out Any>) -> Any): BaseFunction =
        object : BaseFunction() {
            override fun call(
                context: Context,
                scope: Scriptable,
                thisObject: Scriptable,
                args: Array<out Any>
            ): Any = block(args)
        }
}

private class HtmlDocumentBridge(
    private val document: org.jsoup.nodes.Document,
    private val scope: Scriptable
) : ScriptableObject() {
    init {
        parentScope = scope
        prototype = ScriptableObject.getClassPrototype(scope, "Object")
        putProperty(this, "select", function { args ->
            elements(argumentString(args, 0))
        })
        putProperty(this, "selectText", function { args ->
            document.selectFirst(argumentString(args, 0))?.text().orEmpty()
        })
        putProperty(this, "selectAttr", function { args ->
            document.selectFirst(argumentString(args, 0))?.attr(argumentString(args, 1)).orEmpty()
        })
    }

    override fun getClassName(): String = "HtmlDocument"

    private fun elements(css: String): NativeArray =
        NativeArray(document.select(css).map { HtmlElementBridge(it, scope) }.toTypedArray()).apply {
            parentScope = scope
            prototype = ScriptableObject.getClassPrototype(scope, "Array")
        }
}

private class HtmlElementBridge(
    private val element: Element,
    private val scope: Scriptable
) : ScriptableObject() {
    init {
        parentScope = scope
        prototype = ScriptableObject.getClassPrototype(scope, "Object")
        putProperty(this, "text", function { _ -> element.text() })
        putProperty(this, "attr", function { args -> element.attr(argumentString(args, 0)) })
        putProperty(this, "select", function { args ->
            NativeArray(
                element.select(argumentString(args, 0)).map { HtmlElementBridge(it, scope) }.toTypedArray()
            ).apply {
                parentScope = scope
                prototype = ScriptableObject.getClassPrototype(scope, "Array")
            }
        })
        putProperty(this, "selectText", function { args ->
            element.selectFirst(argumentString(args, 0))?.text().orEmpty()
        })
        putProperty(this, "selectAttr", function { args ->
            element.selectFirst(argumentString(args, 0))?.attr(argumentString(args, 1)).orEmpty()
        })
    }

    override fun getClassName(): String = "HtmlElement"
}

private fun putProperty(target: ScriptableObject, name: String, value: Any) {
    ScriptableObject.putProperty(target, name, value)
}

private fun argumentString(args: Array<out Any>, index: Int): String =
    args.getOrNull(index)?.let(Context::toString).orEmpty()

private fun function(block: (Array<out Any>) -> Any): BaseFunction =
    object : BaseFunction() {
        override fun call(
            context: Context,
            scope: Scriptable,
            thisObject: Scriptable,
            args: Array<out Any>
        ): Any = block(args)
    }

private class LimitedContextFactory(
    private val maxInstructions: Int
) : ContextFactory() {
    private var instructions = 0

    override fun makeContext(): Context = super.makeContext().apply {
        optimizationLevel = -1
        languageVersion = Context.VERSION_ES6
        instructionObserverThreshold = 1_000
        setClassShutter(org.mozilla.javascript.ClassShutter { false })
    }

    override fun observeInstructionCount(context: Context, instructionCount: Int) {
        instructions += instructionCount
        if (instructions > maxInstructions) {
            throw JavaScriptPluginException("脚本执行超出指令限制")
        }
    }
}

class JavaScriptPluginException(message: String) : IllegalStateException(message)

const val DEFAULT_MAX_INSTRUCTIONS: Int = 100_000
