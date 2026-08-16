package com.comichub.app

import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicPage
import com.comichub.source.api.ComicSource
import com.comichub.source.api.ComicSummary
import com.comichub.source.api.RateLimit
import com.comichub.source.api.SourceCapability
import com.comichub.source.api.SourceManifest

/** Test-only source; no production build path references this class. */
class SyntheticComicSource : ComicSource {
    override val manifest = SourceManifest(
        id = SOURCE_ID,
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

    private val comics = listOf(
        ComicSummary("sky-courier", SOURCE_ID, "星海信使", tags = listOf("科幻", "冒险")),
        ComicSummary("tea-house", SOURCE_ID, "雨巷茶馆", tags = listOf("日常", "治愈")),
        ComicSummary("paper-dragon", SOURCE_ID, "纸上龙城", tags = listOf("奇幻", "成长"))
    )

    private val chapterTitles = mapOf(
        "sky-courier" to listOf("出发日", "无风带", "灯塔之外"),
        "tea-house" to listOf("雨落之前", "一杯热茶"),
        "paper-dragon" to listOf("折痕", "墨色城门", "最后一页")
    )

    override suspend fun search(query: String, page: Int): List<ComicSummary> {
        if (page != 1) return emptyList()
        val normalized = query.trim()
        return comics.filter { comic ->
            normalized.isBlank() || comic.title.contains(normalized) ||
                comic.tags.any { it.contains(normalized) }
        }
    }

    override suspend fun detail(comicId: String): ComicDetail {
        val summary = comics.first { it.id == comicId }
        return ComicDetail(
            summary = summary,
            author = "合成测试作者",
            description = "仅用于离线测试的合成内容。",
            chapters = chapterTitles.getValue(comicId).mapIndexed { index, title ->
                Chapter(
                    id = "$comicId-${index + 1}",
                    sourceId = SOURCE_ID,
                    comicId = comicId,
                    title = title,
                    number = index + 1
                )
            }
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

    companion object {
        const val SOURCE_ID = "com.pageloom.test.synthetic"
        const val FIRST_CHAPTER_ID = "sky-courier-1"
    }
}
