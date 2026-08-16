package com.readdock.source.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SourceManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val baseUrl: String,
    val domains: List<String>,
    val capabilities: Set<SourceCapability>,
    val permissions: Set<PluginPermission>,
    val rateLimit: RateLimit = RateLimit(),
    val requiresUserInteraction: Boolean = false,
    val license: String? = null
)

@Serializable
enum class SourceCapability {
    @SerialName("search")
    SEARCH,
    @SerialName("detail")
    DETAIL,
    @SerialName("chapters")
    CHAPTERS,
    @SerialName("pages")
    PAGES
}

@Serializable
enum class PluginPermission {
    @SerialName("network")
    NETWORK,
    @SerialName("user_session")
    USER_SESSION
}

@Serializable
data class RateLimit(
    val requestsPerMinute: Int = 20,
    val concurrency: Int = 2
)

data class ComicSummary(
    val id: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String? = null,
    val tags: List<String> = emptyList()
)

data class ComicDetail(
    val summary: ComicSummary,
    val author: String,
    val description: String,
    val chapters: List<Chapter>
)

data class Chapter(
    val id: String,
    val sourceId: String,
    val comicId: String,
    val title: String,
    val number: Int
)

data class ComicPage(
    val id: String,
    val chapterId: String,
    val index: Int,
    val imageUrl: String? = null,
    val displayText: String? = null
)

sealed interface SourceFailure {
    val message: String

    data class RateLimited(override val message: String = "请求过于频繁") : SourceFailure
    data class LoginRequired(override val message: String = "需要用户登录") : SourceFailure
    data class ChallengeRequired(override val message: String = "需要用户完成网页验证") : SourceFailure
    data class ParserBroken(override val message: String = "源解析器暂时不可用") : SourceFailure
    data class Network(override val message: String) : SourceFailure
}

interface ComicSource {
    val manifest: SourceManifest

    suspend fun search(query: String, page: Int = 1): List<ComicSummary>

    suspend fun detail(comicId: String): ComicDetail

    suspend fun pages(chapterId: String): List<ComicPage>
}
