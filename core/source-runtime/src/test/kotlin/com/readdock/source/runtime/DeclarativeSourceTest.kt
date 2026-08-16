package com.readdock.source.runtime

import com.readdock.source.api.PluginPermission
import com.readdock.source.api.RateLimit
import com.readdock.source.api.SourceCapability
import com.readdock.source.api.SourceManifest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DeclarativeSourceTest {
    private val definition = DeclarativeSourceDefinition(
        manifest = SourceManifest(
            id = "com.readdock.fixture",
            name = "Fixture Source",
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
            rateLimit = RateLimit()
        ),
        search = SearchDefinition(
            pathTemplate = "/search?q={query}&page={page}",
            itemSelector = ".comic",
            title = FieldSelector(".title"),
            url = FieldSelector("a", "href"),
            cover = FieldSelector("img", "src"),
            tags = FieldSelector(".tag")
        ),
        detail = DetailDefinition(
            title = FieldSelector("h1"),
            author = FieldSelector(".author"),
            description = FieldSelector(".description"),
            chapterItemSelector = ".chapter",
            chapterTitle = FieldSelector(".chapter-title"),
            chapterUrl = FieldSelector("a", "href")
        ),
        pages = PagesDefinition(
            pageSelector = ".page img",
            image = FieldSelector("", "data-src")
        )
    )

    @Test
    fun `selectors turn HTML fixtures into the source contract`() = runBlocking {
        val pagesHtml = """
            <main class="page"><img data-src="/images/one.jpg"></main>
            <main class="page"><img data-src="https://cdn.example/two.jpg"></main>
        """.trimIndent()
        val detailHtml = """
            <h1>星海信使</h1>
            <div class="author">示例作者</div>
            <p class="description">一段说明</p>
            <div class="chapter"><a href="/chapter/1"><span class="chapter-title">出发</span></a></div>
        """.trimIndent()
        val searchHtml = """
            <article class="comic">
                <a href="/comic/sky"><img src="/covers/sky.jpg"><span class="title">星海信使</span></a>
                <span class="tag">科幻</span><span class="tag">冒险</span>
            </article>
        """.trimIndent()
        val source = DeclarativeSource(definition) { url ->
            when {
                url.contains("/search") -> searchHtml
                url.contains("/chapter/1") -> pagesHtml
                else -> detailHtml
            }
        }

        val results = source.search("星", 1)
        val detail = source.detail(results.single().id)
        val pages = source.pages(detail.chapters.single().id)

        assertEquals("星海信使", results.single().title)
        assertEquals(listOf("科幻", "冒险"), results.single().tags)
        assertEquals("示例作者", detail.author)
        assertEquals("出发", detail.chapters.single().title)
        assertEquals("https://fixture.example/images/one.jpg", pages.first().imageUrl)
        assertEquals("https://cdn.example/two.jpg", pages[1].imageUrl)
    }
}
