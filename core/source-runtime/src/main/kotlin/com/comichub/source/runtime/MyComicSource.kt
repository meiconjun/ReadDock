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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * MYCOMIC site adapter.
 *
 * The unit of this source is the MYCOMIC data source, not an individual comic.
 * Search, detail, chapter and page data are parsed from the site's HTML. The
 * fetcher is supplied by the host app so the source can use one browser
 * session for Cloudflare cookies and ordinary site navigation.
 */
class MyComicSource(
    private val fetchHtml: suspend (url: String) -> String = {
        throw MyComicChallengeException(
            url = SITE_URL,
            message = "MYCOMIC 需要浏览器会话才能访问"
        )
    }
) : ComicSource {
    override val manifest: SourceManifest = SourceManifest(
        id = SOURCE_ID,
        name = "MYCOMIC",
        version = "0.3.1",
        apiVersion = 1,
        baseUrl = SITE_URL,
        domains = listOf("mycomic.com", "biccam.com"),
        capabilities = setOf(
            SourceCapability.SEARCH,
            SourceCapability.DETAIL,
            SourceCapability.CHAPTERS,
            SourceCapability.PAGES
        ),
        permissions = setOf(PluginPermission.NETWORK, PluginPermission.USER_SESSION),
        rateLimit = RateLimit(requestsPerMinute = 10, concurrency = 1),
        requiresUserInteraction = true,
        license = "仅用于已获授权的测试内容；网页访问遵循站点要求"
    )

    override suspend fun search(query: String, page: Int): List<ComicSummary> {
        require(page > 0) { "页码必须为正数" }
        val encodedQuery = query.trim()
            .takeIf(String::isNotBlank)
            ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        val url = buildString {
            append("$SITE_URL/comics")
            if (encodedQuery != null) append("?q=$encodedQuery&page=$page")
            else append("?page=$page")
        }
        val document = loadDocument(url, expected = Expected.SEARCH)

        return searchComicAnchors(document)
            .mapNotNull { anchor ->
                val comicUrl = canonicalComicUrl(anchor.attr("href")) ?: return@mapNotNull null
                val image = anchor.selectFirst("img")
                // MYCOMIC uses a generic cover alt such as “随机漫画” for
                // recommendation/random cards. The card heading is the
                // authoritative title; use the image alt only as a fallback.
                val title = cardTitle(anchor, image)
                    .trim()
                    .ifBlank { return@mapNotNull null }
                ComicSummary(
                    id = comicUrl,
                    sourceId = SOURCE_ID,
                    title = title,
                    coverUrl = image?.let(::imageUrl),
                    tags = emptyList()
                )
            }
            .distinctBy(ComicSummary::id)
    }

    /**
     * The catalog page contains navigation and recommendation links that also
     * look like `/cn/comics/{id}`. Only anchors that are part of a comic card
     * (a valid comic cover or an explicit card title) are search results.
     */
    private fun searchComicAnchors(document: Document): List<Element> =
        document.select("a[href]").filter { anchor ->
            val hasComicCover = anchor.select("img").any { imageUrl(it) != null }
            val hasCardTitle = cardTitleElement(anchor) != null
            hasComicCover || hasCardTitle && anchor.parents().any { parent ->
                parent.tagName() in CARD_TAGS || parent.classNames().any { className ->
                    className.contains("comic", ignoreCase = true) ||
                        className.contains("card", ignoreCase = true)
                }
            }
        }

    private fun cardTitle(anchor: Element, image: Element?): String =
        cardTitleElement(anchor)?.text().orEmpty()
            .ifBlank { image?.attr("alt").orEmpty() }
            .ifBlank { anchor.text() }

    private fun cardTitleElement(anchor: Element): Element? {
        val selector = "[data-flux-subheading], [data-flux-heading], [data-comic-title], h2, h3"
        return generateSequence(anchor) { it.parent() }
            .take(CARD_TITLE_ANCESTOR_LIMIT)
            .mapNotNull { container -> container.selectFirst(selector) }
            .firstOrNull { it.text().isNotBlank() }
    }

    override suspend fun detail(comicId: String): ComicDetail {
        val canonicalId = canonicalComicUrl(comicId)
            ?: throw IllegalArgumentException("不是有效的 MYCOMIC 漫画地址：$comicId")
        val document = loadDocument(canonicalId, expected = Expected.DETAIL)
        val cover = document.select(
            "img[src*='/comics/'], img[data-src*='/comics/'], img[data-original*='/comics/']"
        )
            .firstOrNull()
        val title = cover?.attr("alt")?.trim()
            ?.takeIf(String::isNotBlank)
            ?: document.select("h1, h2").firstOrNull()?.text()?.trim()
                ?.takeIf(String::isNotBlank)
            ?: document.title().substringBefore(" - ").trim().ifBlank { "未命名漫画" }
        val author = document.select("a[href]")
            .firstOrNull { hasFilter(it.attr("href"), "author") }
            ?.text()
            ?.trim()
            .orEmpty()
        val tags = document.select("a[href]")
            .filter { href ->
                val value = href.attr("href")
                hasFilter(value, "tag") ||
                    hasFilter(value, "audience") ||
                    hasFilter(value, "country")
            }
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .distinct()
        val chapters = chapterLinks(document)
            .mapNotNull { anchor ->
                val chapterUrl = canonicalChapterUrl(anchor.attr("href")) ?: return@mapNotNull null
                val titleText = anchor.text().trim().ifBlank { return@mapNotNull null }
                chapterUrl to titleText
            }
            .distinctBy { it.first }
            .mapIndexed { index, (url, chapterTitle) ->
                Chapter(
                    id = url,
                    sourceId = SOURCE_ID,
                    comicId = canonicalId,
                    title = chapterTitle,
                    number = chapterNumber(chapterTitle) ?: index + 1
                )
            }

        return ComicDetail(
            summary = ComicSummary(
                id = canonicalId,
                sourceId = SOURCE_ID,
                title = title,
                coverUrl = cover?.let(::imageUrl),
                tags = tags
            ),
            author = author,
            description = description(document),
            chapters = chapters
        )
    }

    override suspend fun pages(chapterId: String): List<ComicPage> {
        val canonicalId = canonicalChapterUrl(chapterId)
            ?: throw IllegalArgumentException("不是有效的 MYCOMIC 章节地址：$chapterId")
        val document = loadDocument(canonicalId, expected = Expected.PAGES)
        return pageImages(document).mapNotNull { imageUrl(it) }
            .distinct()
            .mapIndexed { index, url ->
                ComicPage(
                    id = "$canonicalId#${index + 1}",
                    chapterId = canonicalId,
                    index = index + 1,
                    imageUrl = url
                )
            }
    }

    private suspend fun loadDocument(url: String, expected: Expected): Document {
        val html = try {
            fetchHtml(url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MyComicChallengeException) {
            throw error
        } catch (error: Throwable) {
            throw MyComicNetworkException(url, error.message ?: "网页加载失败", error)
        }
        if (looksLikeChallenge(html)) {
            throw MyComicChallengeException(url)
        }
        val document = Jsoup.parse(html, url)
        val hasExpectedContent = when (expected) {
            Expected.SEARCH -> document.select("a[href]").any {
                canonicalComicUrl(it.attr("href")) != null
            } || document.select("main").isNotEmpty() ||
                document.title().contains("MYCOMIC", ignoreCase = true)
            Expected.DETAIL -> document.select(
                "img[src*='/comics/'], img[data-src*='/comics/'], " +
                    "img[data-original*='/comics/'], h1, h2, title"
            ).any { it.text().isNotBlank() || it.tagName() == "img" }
            Expected.PAGES -> pageImages(document).isNotEmpty()
        }
        if (!hasExpectedContent) {
            throw MyComicParserException(url, "页面结构中没有找到 MYCOMIC 的${expected.label}数据")
        }
        return document
    }

    /**
     * The detail page also contains chapter links for the "推荐给你" cards.
     * MYCOMIC renders the current comic's 单话/单行本/番外篇 groups as
     * `div.mt-8.mb-12 > div[x-data]`; scope extraction to those groups so a
     * recommended comic cannot be attached to the current comic.
     */
    private fun chapterLinks(document: Document): List<Element> =
        document.select("div.mt-8.mb-12 > div[x-data]")
            .filter { it.attr("x-data").contains("chapter", ignoreCase = true) }
            .flatMap { it.select("a[href]") }

    private fun pageImages(document: Document): List<Element> = document.select(
        "img.page, img[data-page], img[data-src*='/chapters/'], " +
            "img[data-original*='/chapters/'], img[data-lazy-src*='/chapters/']"
    )

    private fun canonicalComicUrl(value: String): String? = canonicalPath(value, COMIC_PATH)

    private fun canonicalChapterUrl(value: String): String? = canonicalPath(value, CHAPTER_PATH)

    private fun canonicalPath(value: String, path: Regex): String? {
        val uri = runCatching { URI(SITE_URL).resolve(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host?.lowercase() != "mycomic.com") return null
        val match = path.matchEntire(uri.path) ?: return null
        return "https://mycomic.com${match.value.trimEnd('/')}"
    }

    private fun imageUrl(image: Element): String? {
        val raw = listOf("src", "data-src", "data-original", "data-lazy-src")
            .asSequence()
            .map { image.attr(it).trim() }
            .firstOrNull(String::isNotBlank)
            ?: return null
        val resolved = runCatching { URI(SITE_URL).resolve(raw).toString() }.getOrNull() ?: return null
        val uri = runCatching { URI(resolved) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return resolved.takeIf {
            uri.scheme == "https" && (host == "biccam.com" || host.endsWith(".biccam.com"))
        }
    }

    private fun description(document: Document): String {
        val structuredDescription = document.select("script[type=application/ld+json]")
            .mapNotNull { script ->
                runCatching {
                    Json.parseToJsonElement(script.data())
                        .jsonObject["description"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()
            }
            .firstOrNull { it.isNotBlank() }
        if (structuredDescription != null) return structuredDescription

        val visibleDescription = document.select("div")
            .firstOrNull { it.classNames().contains("md:w-4/5") }
            ?.selectFirst("[x-show]")
            ?.text()
            ?.trim()
        if (!visibleDescription.isNullOrBlank()) return visibleDescription

        return document.select("main p, article p, p")
            .map { it.text().trim() }
            .firstOrNull {
                it.length >= 12 &&
                    !it.contains("评分") &&
                    !it.contains("星评价")
            }
            .orEmpty()
    }

    private fun chapterNumber(title: String): Int? =
        CHAPTER_NUMBER.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun hasFilter(href: String, name: String): Boolean =
        href.contains("filter[$name]", ignoreCase = true) ||
            href.contains("filter%5B$name%5D", ignoreCase = true)

    private fun looksLikeChallenge(html: String): Boolean {
        val text = html.lowercase()
        return text.contains("please enable cookies") ||
            text.contains("you have been blocked") ||
            text.contains("cf-chl-") ||
            text.contains("challenge-platform")
    }

    private enum class Expected(val label: String) {
        SEARCH("搜索"),
        DETAIL("详情"),
        PAGES("图片")
    }

    companion object {
        const val SOURCE_ID = "com.comichub.mycomic"
        const val SITE_URL = "https://mycomic.com/cn"
        const val COMIC_URL = "$SITE_URL/comics/1769"
        const val FIRST_CHAPTER_URL = "$SITE_URL/chapters/15444"

        private val COMIC_PATH = Regex("/cn/comics/\\d+/?")
        private val CHAPTER_PATH = Regex("/cn/chapters/\\d+/?")
        private val CHAPTER_NUMBER = Regex("(?:第\\s*)?(\\d+)")
        private val CARD_TAGS = setOf("article", "li")
        private const val CARD_TITLE_ANCESTOR_LIMIT = 5
    }
}

class MyComicChallengeException(
    val url: String,
    message: String = "MYCOMIC 要求在浏览器会话中完成验证"
) : IllegalStateException(message)

class MyComicNetworkException(
    val url: String,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class MyComicParserException(
    val url: String,
    message: String
) : IllegalStateException(message)
