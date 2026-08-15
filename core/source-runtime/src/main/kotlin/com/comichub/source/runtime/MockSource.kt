package com.comichub.source.runtime

import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicPage
import com.comichub.source.api.ComicSource
import com.comichub.source.api.ComicSummary
import com.comichub.source.api.PluginPermission
import com.comichub.source.api.RateLimit
import com.comichub.source.api.SourceCapability
import com.comichub.source.api.SourceManifest

class MockSource : ComicSource {
    override val manifest = SourceManifest(
        id = "com.comichub.mock",
        name = "本地示例源",
        version = "0.1.0",
        apiVersion = 1,
        baseUrl = "https://mock.local",
        domains = listOf("mock.local"),
        capabilities = setOf(
            SourceCapability.SEARCH,
            SourceCapability.DETAIL,
            SourceCapability.CHAPTERS,
            SourceCapability.PAGES
        ),
        permissions = emptySet(),
        rateLimit = RateLimit(requestsPerMinute = 120, concurrency = 4),
        license = "Internal fixture"
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
            author = "ComicHub 示例作者",
            description = "这是用于验证漫画源插件协议的本地 Fixture，不会访问互联网。",
            chapters = chapters
        )
    }

    override suspend fun pages(chapterId: String): List<ComicPage> {
        val chapterTitle = chapterId.substringAfterLast('-')
        return (1..6).map { index ->
            ComicPage(
                id = "$chapterId-page-$index",
                chapterId = chapterId,
                index = index,
                displayText = "示例页面 $index\n章节编号：$chapterTitle\n\n这里将来显示漫画图片。"
            )
        }
    }
}
