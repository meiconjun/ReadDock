package com.comichub.source.runtime

import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicPage
import com.comichub.source.api.ComicSource
import com.comichub.source.api.ComicSummary
import com.comichub.source.api.SourceManifest
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class FieldSelector(
    val css: String,
    val attribute: String? = null
)

@Serializable
data class SearchDefinition(
    val pathTemplate: String,
    val itemSelector: String,
    val title: FieldSelector,
    val url: FieldSelector,
    val cover: FieldSelector? = null,
    val tags: FieldSelector? = null
)

@Serializable
data class DetailDefinition(
    val title: FieldSelector,
    val author: FieldSelector? = null,
    val description: FieldSelector? = null,
    val chapterItemSelector: String,
    val chapterTitle: FieldSelector,
    val chapterUrl: FieldSelector
)

@Serializable
data class PagesDefinition(
    val pageSelector: String,
    val image: FieldSelector
)

@Serializable
data class DeclarativeSourceDefinition(
    override val manifest: SourceManifest,
    val search: SearchDefinition,
    val detail: DetailDefinition,
    val pages: PagesDefinition
) : PluginSourceDefinition

interface PluginSourceDefinition {
    val manifest: SourceManifest
}

/**
 * A safe, selector-only source adapter for ordinary HTML sites.
 *
 * The adapter receives HTML from NetworkGateway and only extracts declared
 * fields. It cannot execute page scripts or make undeclared network calls.
 */
class DeclarativeSource(
    private val definition: DeclarativeSourceDefinition,
    private val fetchHtml: suspend (url: String) -> String
) : ComicSource {
    override val manifest: SourceManifest = definition.manifest

    override suspend fun search(query: String, page: Int): List<ComicSummary> {
        val path = definition.search.pathTemplate
            .replace("{query}", URLEncoder.encode(query, StandardCharsets.UTF_8))
            .replace("{page}", page.toString())
        val document = Jsoup.parse(fetchHtml(resolveUrl(path)), manifest.baseUrl)

        return document.select(definition.search.itemSelector).mapNotNull { item ->
            val url = item.read(definition.search.url)?.let(::resolveUrl) ?: return@mapNotNull null
            ComicSummary(
                id = url,
                sourceId = manifest.id,
                title = item.read(definition.search.title).orEmpty().ifBlank { "未命名漫画" },
                coverUrl = item.read(definition.search.cover)?.let(::resolveUrl),
                tags = item.readAll(definition.search.tags)
            )
        }
    }

    override suspend fun detail(comicId: String): ComicDetail {
        val document = Jsoup.parse(fetchHtml(comicId), manifest.baseUrl)
        val detail = definition.detail
        val summary = ComicSummary(
            id = comicId,
            sourceId = manifest.id,
            title = document.read(detail.title).orEmpty().ifBlank { "未命名漫画" }
        )
        val chapters = document.select(detail.chapterItemSelector).mapIndexedNotNull { index, item ->
            val url = item.read(detail.chapterUrl)?.let(::resolveUrl) ?: return@mapIndexedNotNull null
            Chapter(
                id = url,
                sourceId = manifest.id,
                comicId = comicId,
                title = item.read(detail.chapterTitle).orEmpty().ifBlank { "第 ${index + 1} 话" },
                number = index + 1
            )
        }
        return ComicDetail(
            summary = summary,
            author = document.read(detail.author).orEmpty(),
            description = document.read(detail.description).orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun pages(chapterId: String): List<ComicPage> {
        val document = Jsoup.parse(fetchHtml(chapterId), manifest.baseUrl)
        return document.select(definition.pages.pageSelector).mapIndexedNotNull { index, element ->
            val url = element.read(definition.pages.image)?.let(::resolveUrl)
                ?: return@mapIndexedNotNull null
            ComicPage(
                id = "$chapterId#$index",
                chapterId = chapterId,
                index = index + 1,
                imageUrl = url
            )
        }
    }

    private fun resolveUrl(value: String): String =
        URI(manifest.baseUrl).resolve(value).toString()
}

private fun Element.read(selector: FieldSelector?): String? {
    if (selector == null) return null
    val element = if (selector.css.isBlank()) this else selectFirst(selector.css) ?: return null
    return selector.attribute?.let(element::attr)?.takeIf { it.isNotBlank() } ?: element.text()
}

private fun Element.readAll(selector: FieldSelector?): List<String> {
    if (selector == null) return emptyList()
    return select(selector.css).mapNotNull { element ->
        val value = selector.attribute?.let(element::attr)?.takeIf { it.isNotBlank() } ?: element.text()
        value.takeIf { it.isNotBlank() }
    }
}
