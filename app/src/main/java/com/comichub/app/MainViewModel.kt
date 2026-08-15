package com.comichub.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comichub.data.FileImageCache
import com.comichub.data.ImageDownloadQueue
import com.comichub.data.LibraryComic
import com.comichub.data.LibraryRepository
import com.comichub.data.ReadingHistoryItem
import com.comichub.data.RoomLibraryRepository
import com.comichub.data.DownloadStatus
import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicPage
import com.comichub.source.api.ComicSource
import com.comichub.source.api.ComicSummary
import com.comichub.source.api.SourceManifest
import com.comichub.source.runtime.GatewayResult
import com.comichub.source.runtime.InstalledPluginInfo
import com.comichub.source.runtime.LocalPluginStore
import com.comichub.source.runtime.MockSource
import com.comichub.source.runtime.MyComicChallengeException
import com.comichub.source.runtime.MyComicSource
import com.comichub.source.runtime.NetworkGateway
import com.comichub.source.runtime.NetworkRequest
import com.comichub.source.runtime.NetworkRequestPolicy
import com.comichub.source.runtime.PluginPackageLoader
import com.comichub.source.runtime.PluginRepositoryClient
import com.comichub.source.runtime.PluginRepositoryIndexLoader
import com.comichub.source.runtime.PluginStoreResult
import com.comichub.source.runtime.PluginSignatureVerifier
import com.comichub.source.runtime.PluginTrustStore
import com.comichub.source.runtime.PluginUpdate
import com.comichub.source.runtime.SourceRegistry
import com.comichub.source.runtime.SourceHealthSnapshot
import com.comichub.source.runtime.SourceHealthTracker
import com.comichub.source.runtime.UrlConnectionTransport
import com.comichub.source.runtime.toNetworkRequestPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    SEARCH,
    DETAIL,
    READER,
    WEB_READER,
    LIBRARY,
    SOURCES
}

enum class MessageTone {
    INFO,
    ERROR
}

data class UiMessage(
    val text: String,
    val tone: MessageTone
)

private fun infoMessage(text: String) = UiMessage(text, MessageTone.INFO)

private fun failureMessage(text: String) = UiMessage(text, MessageTone.ERROR)

class MainViewModel(
    appContext: Context,
    private val library: LibraryRepository = RoomLibraryRepository.create(appContext),
    myComicSourceOverride: ComicSource? = null
) : ViewModel() {
    private val builtinSource = MockSource()
    private val myComicWebSession = if (myComicSourceOverride == null) {
        MyComicWebSession(appContext)
    } else {
        null
    }
    private val myComicSource = myComicSourceOverride ?: MyComicSource { url ->
        myComicWebSession!!.fetchHtml(url)
    }
    private val registry = SourceRegistry.default()
    private val repositoryPreferences = appContext.getSharedPreferences(
        "plugin_repository",
        Context.MODE_PRIVATE
    )
    private val pluginStore = LocalPluginStore(File(appContext.filesDir, "plugins")) {
        PluginPackageLoader(signatureVerifier = createSignatureVerifier())
    }
    private val healthTracker = SourceHealthTracker()
    private val gateway = NetworkGateway(
        transport = UrlConnectionTransport(),
        healthTracker = healthTracker
    )
    private val imageCache = FileImageCache(File(appContext.cacheDir, "comic-images"))
    private val imageQueue = ImageDownloadQueue(
        cache = imageCache,
        fetch = { url -> fetchImage(url) }
    )

    val libraryItems: StateFlow<List<LibraryComic>> = library.observeLibrary().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val readingHistory: StateFlow<List<ReadingHistoryItem>> = library.observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val sourceHealth: StateFlow<Map<String, SourceHealthSnapshot>> = healthTracker.snapshots
    val savedIds: StateFlow<Set<String>> = libraryItems.map { comics ->
        comics.mapTo(mutableSetOf()) { comicKey(it.sourceId, it.comicId) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    var screen by mutableStateOf(AppScreen.SEARCH)
        private set
    var query by mutableStateOf("")
        private set
    var searchPage by mutableStateOf(1)
        private set
    var results by mutableStateOf<List<ComicSummary>>(emptyList())
        private set
    var catalog by mutableStateOf<List<ComicSummary>>(emptyList())
        private set
    var installedPlugins by mutableStateOf<List<InstalledPluginInfo>>(emptyList())
        private set
    var pluginMessage by mutableStateOf<UiMessage?>(null)
        private set
    var repositoryUrl by mutableStateOf(
        repositoryPreferences.getString("url", "").orEmpty()
    )
        private set
    var repositoryKeyId by mutableStateOf(
        repositoryPreferences.getString("key_id", "").orEmpty()
    )
        private set
    var repositoryPublicKey by mutableStateOf(
        repositoryPreferences.getString("public_key", "").orEmpty()
    )
        private set
    var repositoryMessage by mutableStateOf<UiMessage?>(null)
        private set
    var availableUpdates by mutableStateOf<List<PluginUpdate>>(emptyList())
        private set
    var selectedDetail by mutableStateOf<ComicDetail?>(null)
        private set
    var selectedChapter by mutableStateOf<Chapter?>(null)
        private set
    var webReaderUrl by mutableStateOf<String?>(null)
        private set
    var pages by mutableStateOf<List<ComicPage>>(emptyList())
        private set
    var imageBytes by mutableStateOf<Map<String, ByteArray>>(emptyMap())
        private set
    var resumePage by mutableStateOf(1)
        private set
    var readerPage by mutableStateOf(1)
        private set
    var readerProgressLoaded by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            reloadPluginSources()
            performSearch("", page = 1, append = false)
        }
    }

    fun search(value: String = query) {
        query = value
        searchPage = 1
        viewModelScope.launch { performSearch(value, page = 1, append = false) }
    }

    fun nextSearchPage() {
        if (isLoading) return
        val nextPage = searchPage + 1
        viewModelScope.launch { performSearch(query, page = nextPage, append = true) }
    }

    fun previousSearchPage() {
        if (isLoading || searchPage <= 1) return
        val previousPage = searchPage - 1
        viewModelScope.launch { performSearch(query, page = previousPage, append = false) }
    }

    fun openComic(comic: ComicSummary) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val detail = registry.require(comic.sourceId).detail(comic.id)
                selectedDetail = detail
                try {
                    library.saveComic(detail)
                } catch (storageError: Throwable) {
                    errorMessage = "漫画信息保存失败：${storageError.message ?: "未知错误"}"
                }
                screen = AppScreen.DETAIL
            } catch (sourceError: Throwable) {
                if (sourceError is MyComicChallengeException) {
                    webReaderUrl = sourceError.url
                    screen = AppScreen.WEB_READER
                }
                errorMessage = sourceError.message ?: "打开漫画失败"
            }
            isLoading = false
        }
    }

    fun openChapter(chapter: Chapter) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val source = registry.require(chapter.sourceId)
                if (source.manifest.requiresUserInteraction) {
                    selectedChapter = chapter
                    webReaderUrl = chapter.id
                    screen = AppScreen.WEB_READER
                } else {
                    val loadedPages = source.pages(chapter.id)
                    selectedChapter = chapter
                    pages = loadedPages
                    imageBytes = emptyMap()
                    readerProgressLoaded = false
                    screen = AppScreen.READER
                    val detail = selectedDetail
                    val progress = if (detail == null) {
                        null
                    } else {
                        try {
                            library.saveComic(detail)
                            library.recordChapterOpened(detail.summary, chapter, loadedPages.size)
                        } catch (storageError: Throwable) {
                            errorMessage = "阅读进度保存失败：${storageError.message ?: "未知错误"}"
                            null
                        }
                    }
                    resumePage = progress?.currentPage ?: 1
                    readerPage = resumePage
                    readerProgressLoaded = true
                    val imageUrls = loadedPages.mapNotNull(ComicPage::imageUrl)
                    if (imageUrls.isNotEmpty()) {
                        val tasks = imageQueue.download(imageUrls)
                        imageBytes = loadedPages.mapNotNull { page ->
                            val url = page.imageUrl ?: return@mapNotNull null
                            imageCache.get(url)?.let { bytes -> page.id to bytes }
                        }.toMap()
                        if (tasks.any { task -> task.status == DownloadStatus.FAILED }) {
                            errorMessage = "部分页面图片加载失败，已保留可用页面"
                        }
                    }
                }
            } catch (sourceError: Throwable) {
                errorMessage = sourceError.message ?: "打开章节失败"
            }
            isLoading = false
        }
    }

    fun toggleSaved() {
        val detail = selectedDetail ?: return
        val saved = isSaved(detail.summary)
        viewModelScope.launch {
            try {
                library.saveComic(detail)
                library.setSaved(detail.summary, saved = !saved)
            } catch (storageError: Throwable) {
                errorMessage = "书架保存失败：${storageError.message ?: "未知错误"}"
            }
        }
    }

    fun isSaved(comic: ComicSummary): Boolean =
        comicKey(comic.sourceId, comic.id) in savedIds.value

    fun updateReadingProgress(page: Int) {
        val chapter = selectedChapter ?: return
        val totalPages = pages.size
        readerPage = page.coerceIn(1, totalPages.coerceAtLeast(1))
        viewModelScope.launch {
            try {
                library.updateProgress(chapter, readerPage, totalPages)
                    .also { resumePage = it.currentPage }
            } catch (storageError: Throwable) {
                errorMessage = "阅读进度保存失败：${storageError.message ?: "未知错误"}"
            }
        }
    }

    fun installPlugin(packageJson: String) {
        viewModelScope.launch {
            when (val result = pluginStore.install(packageJson)) {
                is PluginStoreResult.Installed -> {
                    pluginMessage = infoMessage("已安装：${result.plugin.name}")
                    reloadPluginSources()
                    performSearch(query, page = 1, append = false)
                }
                is PluginStoreResult.Rejected -> {
                    pluginMessage = failureMessage("插件未安装：${result.errors.joinToString("；")}")
                }
                is PluginStoreResult.NotFound,
                is PluginStoreResult.NoRollback,
                is PluginStoreResult.RolledBack,
                is PluginStoreResult.Completed -> {
                    pluginMessage = failureMessage("插件操作未完成")
                }
            }
        }
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginStore.setEnabled(id, enabled)
            pluginMessage = infoMessage(if (enabled) "插件已启用" else "插件已停用")
            reloadPluginSources()
            performSearch(query, page = 1, append = false)
        }
    }

    fun uninstallPlugin(id: String) {
        viewModelScope.launch {
            when (pluginStore.uninstall(id)) {
                is PluginStoreResult.Completed -> pluginMessage = infoMessage("插件已卸载")
                is PluginStoreResult.NotFound -> pluginMessage = failureMessage("找不到插件")
                else -> pluginMessage = failureMessage("插件卸载失败")
            }
            reloadPluginSources()
            performSearch(query, page = 1, append = false)
        }
    }

    fun rollbackPlugin(id: String) {
        viewModelScope.launch {
            when (val result = pluginStore.rollback(id)) {
                is PluginStoreResult.RolledBack -> {
                    pluginMessage = infoMessage("已回滚到 ${result.plugin.version}")
                    reloadPluginSources()
                    performSearch(query, page = 1, append = false)
                }
                is PluginStoreResult.NoRollback -> pluginMessage = failureMessage("没有可用的历史版本")
                else -> pluginMessage = failureMessage("插件回滚失败")
            }
        }
    }

    fun updateRepositoryUrl(value: String) {
        repositoryUrl = value
    }

    fun updateRepositoryKeyId(value: String) {
        repositoryKeyId = value
    }

    fun updateRepositoryPublicKey(value: String) {
        repositoryPublicKey = value
    }

    fun refreshRepository() {
        repositoryPreferences.edit()
            .putString("url", repositoryUrl)
            .putString("key_id", repositoryKeyId)
            .putString("public_key", repositoryPublicKey)
            .apply()
        availableUpdates = emptyList()
        val url = repositoryUrl.trim()
        val client = createRepositoryClient()
        if (!url.startsWith("https://")) {
            repositoryMessage = failureMessage("仓库地址必须使用 HTTPS")
            return
        }
        if (client == null) {
            repositoryMessage = failureMessage("请先配置仓库 keyId 和 RSA 公钥")
            return
        }
        viewModelScope.launch {
            repositoryMessage = infoMessage("正在检查仓库更新…")
            when (val result = client.fetchIndex(url)) {
                is com.comichub.source.runtime.RepositoryClientResult.IndexReady -> {
                    availableUpdates = client.indexUpdates(result.index)
                    repositoryMessage = infoMessage(if (availableUpdates.isEmpty()) {
                        "仓库检查完成，没有可用更新"
                    } else {
                        "发现 ${availableUpdates.size} 个更新"
                    })
                }
                is com.comichub.source.runtime.RepositoryClientResult.Failure -> {
                    repositoryMessage = failureMessage(result.message)
                }
                is com.comichub.source.runtime.RepositoryClientResult.PluginReady -> {
                    repositoryMessage = failureMessage("仓库返回了插件包而不是索引")
                }
            }
        }
    }

    fun installUpdate(update: PluginUpdate) {
        val client = createRepositoryClient()
        if (client == null) {
            repositoryMessage = failureMessage("请先配置可信公钥")
            return
        }
        viewModelScope.launch {
            repositoryMessage = infoMessage("正在下载 ${update.available.name}…")
            when (val result = client.downloadPlugin(update.available)) {
                is com.comichub.source.runtime.RepositoryClientResult.PluginReady -> {
                    when (val installed = pluginStore.install(result.packageJson, securePluginLoader())) {
                        is PluginStoreResult.Installed -> {
                            repositoryMessage = infoMessage("已安装 ${installed.plugin.name}")
                            reloadPluginSources()
                            performSearch(query, page = 1, append = false)
                        }
                        is PluginStoreResult.Rejected -> {
                            repositoryMessage = failureMessage(
                                "签名校验失败：${installed.errors.joinToString("；")}"
                            )
                        }
                        else -> repositoryMessage = failureMessage("插件安装失败")
                    }
                }
                is com.comichub.source.runtime.RepositoryClientResult.Failure -> {
                    repositoryMessage = failureMessage(result.message)
                }
                is com.comichub.source.runtime.RepositoryClientResult.IndexReady -> {
                    repositoryMessage = failureMessage("仓库返回了索引而不是插件包")
                }
            }
        }
    }

    fun showSearch() {
        screen = AppScreen.SEARCH
    }

    fun showLibrary() {
        screen = AppScreen.LIBRARY
    }

    fun showSources() {
        screen = AppScreen.SOURCES
    }

    fun back() {
        screen = when (screen) {
            AppScreen.READER -> AppScreen.DETAIL
            AppScreen.WEB_READER -> AppScreen.DETAIL
            AppScreen.DETAIL -> AppScreen.SEARCH
            AppScreen.SEARCH, AppScreen.LIBRARY, AppScreen.SOURCES -> AppScreen.SEARCH
        }
    }

    private suspend fun reloadPluginSources() {
        installedPlugins = pluginStore.list()
        val report = pluginStore.loadEnabled { manifest, url ->
            fetchHtml(manifest, url)
        }
        registry.replace(listOf(builtinSource, myComicSource) + report.sources)
        if (report.failures.isNotEmpty()) {
            pluginMessage = failureMessage("插件加载失败：${report.failures.keys.joinToString("、")}")
        }
    }

    private suspend fun performSearch(value: String, page: Int, append: Boolean) {
        isLoading = true
        errorMessage = null
        val found = mutableListOf<ComicSummary>()
        val failures = mutableListOf<String>()
        registry.sources.forEach { source ->
            runCatching { source.search(value, page) }
                .onSuccess(found::addAll)
                .onFailure { error ->
                    if (error is MyComicChallengeException) {
                        webReaderUrl = error.url
                        screen = AppScreen.WEB_READER
                    }
                    failures += source.manifest.name
                }
        }
        results = if (append) {
            (results + found).distinctBy { "${it.sourceId}::${it.id}" }
        } else {
            found
        }
        searchPage = page
        if (value.isBlank() && page == 1) catalog = results
        if (found.isEmpty() && failures.isNotEmpty()) {
            errorMessage = "漫画源加载失败：${failures.joinToString("、")}"
        }
        isLoading = false
    }

    private suspend fun fetchHtml(manifest: SourceManifest, url: String): String {
        return when (
            val result = gateway.get(
                NetworkRequest(url = url, sourceId = manifest.id),
                manifest.toNetworkRequestPolicy()
            )
        ) {
            is GatewayResult.Success -> result.response.body
            is GatewayResult.HttpFailure -> error("HTTP ${result.statusCode}")
            is GatewayResult.TransportFailure -> error(result.message)
            is GatewayResult.CircuitOpen -> error("源暂时冷却，请稍后重试")
        }
    }

    private suspend fun fetchImage(url: String): ByteArray {
        return when (
            val result = gateway.get(
                NetworkRequest(url),
                NetworkRequestPolicy(cacheTtlMs = 0)
            )
        ) {
            is GatewayResult.Success -> result.response.bodyBytes
                ?: result.response.body.toByteArray(Charsets.UTF_8)
            is GatewayResult.HttpFailure -> error("HTTP ${result.statusCode}")
            is GatewayResult.TransportFailure -> error(result.message)
            is GatewayResult.CircuitOpen -> error("图片源暂时冷却，请稍后重试")
        }
    }

    private fun createSignatureVerifier(): PluginSignatureVerifier? {
        val keyId = repositoryKeyId.trim()
        val publicKey = repositoryPublicKey.trim()
        if (keyId.isBlank() || publicKey.isBlank()) return null
        return PluginSignatureVerifier(PluginTrustStore(mapOf(keyId to publicKey)))
    }

    private fun securePluginLoader(): PluginPackageLoader = PluginPackageLoader(
        signatureVerifier = createSignatureVerifier(),
        requireSignature = true
    )

    private fun createRepositoryClient(): PluginRepositoryClient? {
        val verifier = createSignatureVerifier() ?: return null
        return PluginRepositoryClient(
            gateway = gateway,
            indexLoader = PluginRepositoryIndexLoader(
                signatureVerifier = verifier,
                requireSignature = true
            )
        )
    }

    private fun PluginRepositoryClient.indexUpdates(
        index: com.comichub.source.runtime.PluginRepositoryIndex
    ): List<PluginUpdate> = PluginRepositoryIndexLoader().updates(index, pluginStore.list())

    override fun onCleared() {
        myComicWebSession?.destroy()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(context.applicationContext) as T
                    }
                    error("Unsupported ViewModel: ${modelClass.name}")
                }
            }
    }
}

private fun comicKey(sourceId: String, comicId: String): String = "$sourceId::$comicId"
