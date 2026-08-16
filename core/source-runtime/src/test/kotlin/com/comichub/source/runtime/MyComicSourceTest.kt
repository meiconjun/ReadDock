package com.comichub.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyComicSourceTest {
    private val source = MyComicSource {
        fetchedUrls += it
        fixturePages[it] ?: error("missing fixture: $it")
    }

    @Test
    fun `search parses site results for any query`() = runBlocking {
        val result = source.search("烙印战士")

        assertEquals(1, result.size)
        assertEquals(MyComicSource.COMIC_URL, result.single().id)
        assertEquals("烙印战士", result.single().title)
        assertEquals("https://biccam.com/comics/1769-cover.jpg", result.single().coverUrl)
        assertTrue(source.manifest.requiresUserInteraction)
        assertEquals(setOf("mycomic.com", "biccam.com"), source.manifest.domains.toSet())
    }

    @Test
    fun `detail parses all chapters and pages from the site`() = runBlocking {
        val detail = source.detail(MyComicSource.COMIC_URL)

        assertEquals("三浦建太郎", detail.author)
        assertEquals(listOf("格斗", "日本"), detail.summary.tags)
        assertEquals(2, detail.chapters.size)
        assertEquals(MyComicSource.FIRST_CHAPTER_URL, detail.chapters.first().id)
        assertEquals("第01卷", detail.chapters.first().title)
        assertEquals(2, source.pages(MyComicSource.FIRST_CHAPTER_URL).size)
        assertEquals(
            "https://biccam.com/chapters/15444/1-page.jpg",
            source.pages(MyComicSource.FIRST_CHAPTER_URL).first().imageUrl
        )
    }

    @Test
    fun `detail normalizes ids and excludes recommendation chapters`() = runBlocking {
        val detail = source.detail("${MyComicSource.COMIC_URL}/")

        assertEquals(MyComicSource.COMIC_URL, detail.summary.id)
        assertTrue(detail.chapters.all { it.sourceId == MyComicSource.SOURCE_ID })
        assertTrue(detail.chapters.all { it.comicId == MyComicSource.COMIC_URL })
        assertFalse(detail.chapters.any { it.id.endsWith("900001") })
    }

    @Test
    fun `missing detail chapters and missing page urls are safe`() = runBlocking {
        val detail = source.detail("${MyComicSource.SITE_URL}/comics/9999")
        assertTrue(detail.chapters.isEmpty())

        val pages = source.pages("${MyComicSource.SITE_URL}/chapters/15445")
        assertTrue(pages.isEmpty())
    }

    @Test
    fun `undeclared hosts and malformed html are rejected without crashing`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            source.detail("https://evil.example/cn/comics/1769")
        }
        assertFailsWith<MyComicParserException> {
            source.search("异常")
        }
        Unit
    }

    @Test
    fun `valid empty search page returns an empty list`() = runBlocking {
        val result = source.search("不存在")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search prefers card title over generic cover alt and ignores navigation links`() = runBlocking {
        val result = source.search("卡片")

        assertEquals(listOf("海之物语", "猎人游戏W"), result.map { it.title })
        assertTrue(result.none { it.id.endsWith("9000") })
    }

    @Test
    fun `page number is sent to the site's catalog pagination`() = runBlocking {
        val result = source.search("", page = 3)

        assertEquals("https://mycomic.com/cn/comics?page=3", fetchedUrls.last())
        assertEquals("烙印战士", result.single().title)
    }

    companion object {
        private val fetchedUrls = mutableListOf<String>()
        private val fixturePages = mapOf(
            "https://mycomic.com/cn/comics?q=%E7%83%99%E5%8D%B0%E6%88%98%E5%A3%AB&page=1" to """
                <html><body>
                  <a href="/cn/comics/1769">
                    <img src="https://biccam.com/comics/1769-cover.jpg" alt="烙印战士">
                  </a>
                </body></html>
            """.trimIndent(),
            "https://mycomic.com/cn/comics?page=3" to """
                <html><body>
                  <a href="/cn/comics/1769"><img src="https://biccam.com/comics/1769-cover.jpg" alt="烙印战士"></a>
                </body></html>
            """.trimIndent(),
            MyComicSource.COMIC_URL to """
                <html>
                  <head><title>烙印战士 - MYCOMIC - 我的漫画</title></head>
                  <body><main>
                    <img src="https://biccam.com/comics/1769-cover.jpg" alt="烙印战士">
                    <a href="/cn/comics?filter%5Bauthor%5D=%E4%B8%89%E6%B5%A6%E5%BB%BA%E5%A4%AA%E9%83%8E">三浦建太郎</a>
                    <a href="/cn/comics?filter%5Btag%5D=gedou">格斗</a>
                    <a href="/cn/comics?filter%5Bcountry%5D=japan">日本</a>
                    <p>可爱又可笑的妖精与酷又神勇的黑之剑士。</p>
                    <div class="mt-8 mb-12">
                      <div x-data="{ chapters: true }">
                        <a href="/cn/chapters/15444">第01卷</a>
                        <a href="/cn/chapters/806119">第380话</a>
                      </div>
                    </div>
                    <div class="recommendations">
                      <a href="/cn/chapters/900001">第01话</a>
                    </div>
                  </main></body>
                </html>
            """.trimIndent(),
            MyComicSource.FIRST_CHAPTER_URL to """
                <html><body>
                  <img class="page" src="https://biccam.com/chapters/15444/1-page.jpg">
                  <img class="page" src="https://biccam.com/chapters/15444/2-page.jpg">
                </body></html>
            """.trimIndent(),
            "${MyComicSource.SITE_URL}/comics/9999" to """
                <html><head><title>无章节漫画 - MYCOMIC</title></head>
                <body><h1>无章节漫画</h1></body></html>
            """.trimIndent(),
            "${MyComicSource.SITE_URL}/chapters/15445" to """
                <html><body>
                  <img class="page" data-src="">
                  <img class="page" data-src="https://evil.example/page.jpg">
                </body></html>
            """.trimIndent(),
            "https://mycomic.com/cn/comics?q=%E5%BC%82%E5%B8%B8&page=1" to """
                <html><body><p>异常结构</p></body></html>
            """.trimIndent(),
            "https://mycomic.com/cn/comics?q=%E4%B8%8D%E5%AD%98%E5%9C%A8&page=1" to """
                <html><head><title>MYCOMIC 搜索</title></head>
                <body><main><p>没有结果</p></main></body></html>
            """.trimIndent(),
            "https://mycomic.com/cn/comics?q=%E5%8D%A1%E7%89%87&page=1" to """
                <html><body>
                  <nav><a href="/cn/comics/9000">随机漫画</a></nav>
                  <article class="comic-card">
                    <a href="/cn/comics/1769">
                      <img src="https://biccam.com/comics/1769-cover.jpg" alt="随机漫画">
                      <span data-flux-subheading>海之物语</span>
                    </a>
                  </article>
                  <article class="comic-card">
                    <a href="/cn/comics/1770">
                      <img src="https://biccam.com/comics/1770-cover.jpg" alt="随机漫画">
                      <span data-flux-subheading>猎人游戏W</span>
                    </a>
                  </article>
                </body></html>
            """.trimIndent()
        )
    }
}
