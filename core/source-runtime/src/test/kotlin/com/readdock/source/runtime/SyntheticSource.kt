package com.readdock.source.runtime

import com.readdock.source.api.Chapter
import com.readdock.source.api.ComicDetail
import com.readdock.source.api.ComicPage
import com.readdock.source.api.ComicSource
import com.readdock.source.api.ComicSummary
import com.readdock.source.api.RateLimit
import com.readdock.source.api.SourceCapability
import com.readdock.source.api.SourceManifest

/** Offline-only content used by JVM tests. It is never part of the app artifact. */
class SyntheticSource : ComicSource {
    override val manifest = SourceManifest(
        id = "com.readdock.test.synthetic",
        name = "Synthetic test source",
        version = "1.0.0",
        apiVersion = 1,
        baseUrl = "https://synthetic.invalid",
        domains = listOf("synthetic.invalid"),
        capabilities = setOf(
            SourceCapability.SEARCH,
            SourceCapability.DETAIL,
            SourceCapability.CHAPTERS,
            SourceCapability.PAGES
        ),
        permissions = emptySet(),
        rateLimit = RateLimit(requestsPerMinute = 120, concurrency = 4),
        license = "Test-only synthetic content"
    )

    private val catalog = listOf(
        ComicSummary(
            id = "sky-courier",
            sourceId = manifest.id,
            title = "星海信使",
            tags = listOf("科幻", "冒险")
        ),
        ComicSummary(
            id = "tea-house",
            sourceId = manifest.id,
            title = "雨巷茶馆",
            tags = listOf("日常", "治愈")
        ),
        ComicSummary(
            id = "paper-dragon",
            sourceId = manifest.id,
            title = "纸上龙城",
            tags = listOf("奇幻", "成长")
        )
    )

    private val chapterMap = mapOf(
        "sky-courier" to listOf("出发日", "无风带", "灯塔之外"),
        "tea-house" to listOf("雨落之前", "一杯热茶"),
        "paper-dragon" to listOf("折痕", "墨色城门", "最后一页")
    )

    override suspend fun search(query: String, page: Int): List<ComicSummary> {
        if (page != 1) return emptyList()
        return if (query.isBlank()) {
            catalog
        } else {
            catalog.filter { comic ->
                comic.title.contains(query.trim(), ignoreCase = true) ||
                    comic.tags.any { it.contains(query.trim(), ignoreCase = true) }
            }
        }
    }

    override suspend fun detail(comicId: String): ComicDetail {
        val summary = catalog.first { it.id == comicId }
        val chapters = chapterMap.getValue(comicId).mapIndexed { index, title ->
            Chapter(
                id = "$comicId-${index + 1}",
                sourceId = manifest.id,
                comicId = comicId,
                title = title,
                number = index + 1
            )
        }
        return ComicDetail(
            summary = summary,
            author = "合成测试作者",
            description = "仅用于离线协议测试的合成内容。",
            chapters = chapters
        )
    }

    override suspend fun pages(chapterId: String): List<ComicPage> =
        (1..6).map { index ->
            ComicPage(
                id = "$chapterId-page-$index",
                chapterId = chapterId,
                index = index,
                displayText = "合成测试页面 $index"
            )
        }
}
