package com.readdock.source.runtime

import com.readdock.source.api.PluginPermission
import com.readdock.source.api.RateLimit
import com.readdock.source.api.SourceCapability
import com.readdock.source.api.SourceManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JavaScriptSourceTest {
    @Test
    fun `loads and executes a restricted javascript plugin`() = runBlocking {
        val script = """
            function search(ctx, query, page) {
                var doc = ctx.html(ctx.http.get("/search?q=" + ctx.url.encode(query) + "&page=" + page));
                return doc.select(".comic").map(function(item) {
                    return {
                        title: item.selectText(".title"),
                        url: item.attr("data-url"),
                        tags: ["fixture"]
                    };
                });
            }
            function detail(ctx, url) {
                var doc = ctx.html(ctx.http.get(url));
                return {
                    title: doc.selectText("h1"),
                    author: doc.selectText(".author"),
                    description: doc.selectText(".description"),
                    chapters: doc.select(".chapter").map(function(item) {
                        return { title: item.selectText(".name"), url: item.attr("data-url") };
                    })
                };
            }
            function pages(ctx, url) {
                var doc = ctx.html(ctx.http.get(url));
                return doc.select(".page").map(function(item) {
                    return { url: item.attr("data-src") };
                });
            }
        """.trimIndent()
        val packageJson = packageJson(script)
        val sourceResult = PluginPackageLoader().load(packageJson) { url ->
            when {
                url.contains("/search") ->
                    "<article class='comic' data-url='/comic/sky'><span class='title'>星海信使</span></article>"
                url.endsWith("/comic/sky") ->
                    "<h1>星海信使</h1><span class='author'>测试作者</span>" +
                        "<span class='description'>测试描述</span>" +
                        "<div class='chapter' data-url='/chapter/1'><span class='name'>出发</span></div>"
                else -> "<div class='page' data-src='/page/1.jpg'></div>"
            }
        }

        val loaded = when (sourceResult) {
            is PluginLoadResult.Success -> sourceResult
            is PluginLoadResult.Failure -> error(sourceResult.errors.joinToString("；"))
        }
        val comic = loaded.source.search("星海", 1).single()
        val detail = loaded.source.detail(comic.id)
        val pages = loaded.source.pages(detail.chapters.single().id)

        assertEquals("星海信使", comic.title)
        assertEquals("https://fixture.example/comic/sky", comic.id)
        assertEquals("测试作者", detail.author)
        assertEquals("出发", detail.chapters.single().title)
        assertEquals("https://fixture.example/page/1.jpg", pages.single().imageUrl)
    }

    @Test
    fun `rejects forbidden javascript tokens`() {
        val result = PluginPackageLoader().parse(
            packageJson("function search(ctx, query, page) { return Packages.java.lang.System; }")
        )

        val failure = assertIs<PluginParseResult.Failure>(result)
        assertEquals(true, failure.errors.any { it.contains("Java") })
    }

    @Test
    fun `stops scripts that exceed instruction budget`() = runBlocking {
        val definition = JavaScriptSourceDefinition(
            manifest = manifest,
            script = """
                function search(ctx, query, page) {
                    while (true) { }
                }
            """.trimIndent(),
            maxInstructions = 10_000
        )
        val source = JavaScriptSource(definition) { "" }

        assertFailsWith<JavaScriptPluginException> {
            source.search("", 1)
        }
        Unit
    }

    private fun packageJson(script: String): String = buildJsonObject {
        put("manifest", Json.encodeToJsonElement(manifest))
        put("script", script)
        put("maxInstructions", 100_000)
    }.toString()

    private companion object {
        val manifest = SourceManifest(
            id = "com.readdock.js-fixture",
            name = "JavaScript Fixture",
            version = "0.1.0",
            apiVersion = 1,
            baseUrl = "https://fixture.example",
            domains = listOf("fixture.example"),
            capabilities = setOf(
                SourceCapability.SEARCH,
                SourceCapability.DETAIL,
                SourceCapability.CHAPTERS,
                SourceCapability.PAGES
            ),
            permissions = setOf(PluginPermission.NETWORK),
            rateLimit = RateLimit(requestsPerMinute = 60, concurrency = 1)
        )
    }
}
