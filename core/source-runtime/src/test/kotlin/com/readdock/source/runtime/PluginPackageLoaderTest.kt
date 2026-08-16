package com.readdock.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginPackageLoaderTest {
    @Test
    fun `loads a declarative plugin from JSON`() = runBlocking {
        val packageJson = """
            {
              "manifest": {
                "id": "com.readdock.json-fixture",
                "name": "JSON Fixture",
                "version": "0.1.0",
                "apiVersion": 1,
                "baseUrl": "https://fixture.example",
                "domains": ["fixture.example"],
                "capabilities": ["search", "detail", "chapters", "pages"],
                "permissions": ["network"],
                "rateLimit": {
                  "requestsPerMinute": 30,
                  "concurrency": 1
                }
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
                "chapterTitle": {"css": ".chapter-title"},
                "chapterUrl": {"css": "a", "attribute": "href"}
              },
              "pages": {
                "pageSelector": ".page img",
                "image": {"css": "", "attribute": "data-src"}
              }
            }
        """.trimIndent()
        val sourceResult = PluginPackageLoader().load(packageJson) { url ->
            if (url.contains("/search")) {
                "<article class='comic'><a href='/comic/sky'><span class='title'>星海信使</span></a></article>"
            } else if (url.contains("/chapter/1")) {
                "<div class='page'><img data-src='/page/1.jpg'></div>"
            } else {
                "<h1>星海信使</h1><div class='chapter'><a href='/chapter/1'><span class='chapter-title'>出发</span></a></div>"
            }
        }

        val loaded = assertIs<PluginLoadResult.Success>(sourceResult)
        val comic = loaded.source.search("星").single()
        val detail = loaded.source.detail(comic.id)
        val pages = loaded.source.pages(detail.chapters.single().id)

        assertEquals("JSON Fixture", loaded.source.manifest.name)
        assertEquals("星海信使", comic.title)
        assertEquals("出发", detail.chapters.single().title)
        assertEquals("https://fixture.example/page/1.jpg", pages.single().imageUrl)
    }

    @Test
    fun `rejects insecure plugin manifests`() {
        val result = PluginPackageLoader().load(
            """
                {
                  "manifest": {
                    "id": "bad plugin",
                    "name": "Bad",
                    "version": "0.1.0",
                    "apiVersion": 1,
                    "baseUrl": "http://example.com",
                    "domains": [],
                    "capabilities": [],
                    "permissions": [],
                    "rateLimit": {"requestsPerMinute": 1, "concurrency": 1}
                  },
                  "search": {"pathTemplate":"/", "itemSelector":".item", "title":{"css":".title"}, "url":{"css":"a"}},
                  "detail": {"title":{"css":"h1"}, "chapterItemSelector":".chapter", "chapterTitle":{"css":".title"}, "chapterUrl":{"css":"a"}},
                  "pages": {"pageSelector":"img", "image":{"css":"", "attribute":"src"}}
                }
            """.trimIndent(),
            fetchHtml = { "" }
        )

        val failure = assertIs<PluginLoadResult.Failure>(result)
        assertEquals(true, failure.errors.any { it.contains("HTTPS") })
        assertEquals(true, failure.errors.any { it.contains("域名") })
    }
}
