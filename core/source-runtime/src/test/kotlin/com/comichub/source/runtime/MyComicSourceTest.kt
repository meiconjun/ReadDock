package com.comichub.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
            """.trimIndent()
        )
    }
}
