package com.comichub.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import com.comichub.data.LocalComic
import com.comichub.data.LocalComicRepository
import com.comichub.data.ReadingHistoryItem
import com.comichub.data.RoomLibraryRepository
import com.comichub.data.RoomLocalComicRepository
import com.comichub.app.local.LocalComicImporter
import com.comichub.app.local.LocalImportResult
import com.comichub.app.reader.OnlineReaderImageLoader
import com.comichub.app.reader.ReaderImageException
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

enum class AppScreen {
    SEARCH,
    DETAIL,
    READER,
    LOCAL_READER,
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

enum class LocalImportStatus { IDLE, LOADING, SUCCESS, EMPTY, ERROR }

data class LocalImportUiState(
    val status: LocalImportStatus = LocalImportStatus.IDLE,
    val message: String? = null
)

private fun infoMessage(text: String) = UiMessage(text, MessageTone.INFO)

private fun failureMessage(text: String) = UiMessage(text, MessageTone.ERROR)

class MainViewModel(
    private val appContext: Context,
    private val library: LibraryRepository = RoomLibraryRepository.create(appContext),
    myComicSourceOverride: ComicSource? = null,
    private val imageFetcher: (suspend (String) -> ByteArray)? = null,
    private val localComicRepository: LocalComicRepository = RoomLocalComicRepository.create(appContext)
) : ViewModel() {
    private sealed interface PendingWebAction {
        data class Search(val query: String, val page: Int, val append: Boolean) : PendingWebAction
        data class OpenComic(val comic: ComicSummary) : PendingWebAction
        data class OpenChapter(val chapter: Chapter) : PendingWebAction
    }

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
        fetch = { url -> imageFetcher?.invoke(url) ?: fetchImage(url) }
    )
    private val onlineImageLoader = OnlineReaderImageLoader(imageCache, imageQueue)
    private val backStack = mutableListOf(AppScreen.SEARCH)
    private var searchJob: Job? = null
    private var navigationJob: Job? = null
    private var localImportJob: Job? = null
    private var localDeleteJob: Job? = null
    private var readerGeneration = 0L
    private var readerProgressJob: Job? = null
    private val readerProgressMutex = Mutex()

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
    val localComics: StateFlow<List<LocalComic>> = localComicRepository.observeLocalComics().stateIn(
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
    var coverBytes by mutableStateOf<ByteArray?>(null)
        private set
    var selectedChapter by mutableStateOf<Chapter?>(null)
        private set
    var webReaderUrl by mutableStateOf<String?>(null)
        private set
    var pages by mutableStateOf<List<ComicPage>>(emptyList())
        private set
    private var readerRetryVersions by mutableStateOf<Map<String, Int>>(emptyMap())
    var resumePage by mutableStateOf(1)
        private set
    var readerPage by mutableStateOf(1)
        private set
    var readerProgressLoaded by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var actionMessage by mutableStateOf<UiMessage?>(null)
        private set
    var localImportState by mutableStateOf(LocalImportUiState())
        private set
    var selectedLocalComicId by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    private var savedOverride by mutableStateOf<Pair<String, Boolean>?>(null)
    private var pendingWebAction by mutableStateOf<PendingWebAction?>(null)

    val canGoBack: Boolean
        get() = backStack.size > 1

    val canRetryWebAction: Boolean
        get() = pendingWebAction != null

    init {
        viewModelScope.launch {
            try {
                reloadPluginSources()
                performSearch("", page = 1, append = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                errorMessage = error.message ?: "漫画源初始化失败"
            } finally {
                isLoading = false
            }
        }
    }

    fun search(value: String = query) {
        if (isLoading) return
        query = value
        searchPage = 1
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(value, page = 1, append = false) }
    }

    fun nextSearchPage() {
        if (isLoading) return
        val nextPage = searchPage + 1
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query, page = nextPage, append = true) }
    }

    fun previousSearchPage() {
        if (isLoading || searchPage <= 1) return
        val previousPage = searchPage - 1
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query, page = previousPage, append = false) }
    }

    fun retrySearch() = search(query)

    fun openComic(comic: ComicSummary) {
        navigationJob?.cancel()
        invalidateReaderSession()
        navigationJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            actionMessage = null
            savedOverride = null
            try {
                val detail = registry.require(comic.sourceId).detail(comic.id).also {
                    require(it.summary.sourceId == comic.sourceId && it.summary.id == comic.id) {
                        "漫画源返回了不属于当前结果的漫画详情"
                    }
                    require(it.chapters.all { chapter ->
                        chapter.sourceId == it.summary.sourceId && chapter.comicId == it.summary.id
                    }) {
                        "漫画源返回了不属于当前漫画的章节"
                    }
                }
                selectedDetail = detail
                coverBytes = null
                try {
                    library.saveComic(detail)
                } catch (storageError: Throwable) {
                    errorMessage = "漫画信息保存失败：${storageError.message ?: "未知错误"}"
                }
                navigateTo(AppScreen.DETAIL)
                pendingWebAction = null
                detail.summary.coverUrl?.let { coverUrl ->
                    viewModelScope.launch { loadCover(coverUrl) }
                }
            } catch (sourceError: Throwable) {
                if (sourceError is CancellationException) throw sourceError
                if (sourceError is MyComicChallengeException) {
                    webReaderUrl = sourceError.url
                    pendingWebAction = PendingWebAction.OpenComic(comic)
                    navigateTo(AppScreen.WEB_READER)
                    actionMessage = infoMessage("请在网页会话中完成验证，然后点击“验证完成，重试”")
                } else {
                    errorMessage = "打开漫画失败：${sourceError.message ?: "未知错误"}"
                }
            }
            isLoading = false
        }
    }

    fun openChapter(chapter: Chapter) {
        if (screen == AppScreen.READER && selectedChapter?.id == chapter.id && !isLoading) return
        navigationJob?.cancel()
        val generation = beginReaderSession()
        selectedChapter = chapter
        pages = emptyList()
        readerProgressLoaded = false
        readerPage = 1
        navigationJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            actionMessage = null
            try {
                val detail = selectedDetail
                require(detail != null && chapter.sourceId == detail.summary.sourceId &&
                    chapter.comicId == detail.summary.id) {
                    "章节不属于当前漫画，无法打开"
                }
                val source = registry.require(chapter.sourceId)
                val loadedPages = source.pages(chapter.id)
                if (generation != readerGeneration) return@launch
                selectedChapter = chapter
                pages = loadedPages
                readerProgressLoaded = false
                navigateTo(AppScreen.READER)
                val progress = try {
                    library.saveComic(detail)
                    library.recordChapterOpened(detail.summary, chapter, loadedPages.size)
                } catch (storageError: Throwable) {
                    errorMessage = "阅读进度保存失败：${storageError.message ?: "未知错误"}"
                    null
                }
                if (generation != readerGeneration) return@launch
                resumePage = progress?.currentPage ?: 1
                readerPage = resumePage
                readerProgressLoaded = true
                if (loadedPages.isEmpty()) {
                    errorMessage = "本章节没有可显示的图片"
                }
                pendingWebAction = null
            } catch (sourceError: Throwable) {
                if (sourceError is CancellationException) throw sourceError
                if (generation != readerGeneration) return@launch
                if (sourceError is MyComicChallengeException) {
                    selectedChapter = chapter
                    webReaderUrl = sourceError.url
                    pendingWebAction = PendingWebAction.OpenChapter(chapter)
                    navigateTo(AppScreen.WEB_READER)
                    actionMessage = infoMessage("请在网页会话中完成验证，然后点击“验证完成，重试”")
                } else {
                    errorMessage = "打开章节失败：${sourceError.message ?: "未知错误"}"
                }
            }
            if (generation == readerGeneration) isLoading = false
        }
    }

    fun toggleSaved() {
        val detail = selectedDetail ?: return
        if (isSaving) return
        val saved = isSaved(detail.summary)
        val key = comicKey(detail.summary.sourceId, detail.summary.id)
        savedOverride = key to !saved
        errorMessage = null
        actionMessage = null
        isSaving = true
        viewModelScope.launch {
            try {
                library.saveComic(detail)
                library.setSaved(detail.summary, saved = !saved)
                actionMessage = infoMessage(if (saved) "已取消收藏" else "已收藏")
            } catch (storageError: Throwable) {
                savedOverride = null
                errorMessage = "书架保存失败：${storageError.message ?: "未知错误"}"
                actionMessage = failureMessage("收藏操作失败，请重试")
            } finally {
                isSaving = false
            }
        }
    }

    fun isSaved(
        comic: ComicSummary,
        persistedIds: Set<String> = savedIds.value
    ): Boolean {
        val key = comicKey(comic.sourceId, comic.id)
        return savedOverride?.takeIf { it.first == key }?.second
            ?: (key in persistedIds)
    }

    private suspend fun loadCover(url: String) {
        try {
            val bytes = imageCache.get(url) ?: run {
                val downloaded = imageFetcher?.invoke(url) ?: fetchImage(url)
                imageCache.put(url, downloaded)
                downloaded
            }
            if (selectedDetail?.summary?.coverUrl == url) coverBytes = bytes
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (selectedDetail?.summary?.coverUrl == url) {
                actionMessage = failureMessage("封面加载失败：${error.message ?: "网络错误"}")
            }
        }
    }

    fun retryPendingWebAction() {
        val action = pendingWebAction ?: return
        if (screen == AppScreen.WEB_READER) popScreen()
        when (action) {
            is PendingWebAction.Search -> {
                query = action.query
                searchPage = action.page
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    performSearch(action.query, action.page, action.append)
                }
            }
            is PendingWebAction.OpenComic -> openComic(action.comic)
            is PendingWebAction.OpenChapter -> openChapter(action.chapter)
        }
    }

    suspend fun loadReaderImage(page: ComicPage): Bitmap {
        val chapter = selectedChapter
        require(chapter?.id == page.chapterId) { "页面不属于当前章节" }
        val generation = readerGeneration
        val url = page.imageUrl ?: throw ReaderImageException("当前页面没有图片地址")
        val bitmap = onlineImageLoader.load(url)
        if (generation != readerGeneration || selectedChapter?.id != chapter.id) {
            throw CancellationException("页面已过期")
        }
        return bitmap
    }

    fun readerRetryVersion(pageId: String): Int = readerRetryVersions[pageId] ?: 0

    fun retryReaderPage(page: ComicPage) {
        val url = page.imageUrl ?: return
        imageCache.remove(url)
        onlineImageLoader.clearPage(url)
        readerRetryVersions = readerRetryVersions + (page.id to (readerRetryVersions[page.id] ?: 0) + 1)
    }

    fun importLocalFiles(uris: List<Uri>) {
        if (uris.isEmpty() || localImportJob?.isActive == true) return
        localImportJob = viewModelScope.launch {
            localImportState = LocalImportUiState(LocalImportStatus.LOADING, "正在导入本地漫画…")
            try {
                when (val result = LocalComicImporter(
                    context = appContext,
                    repository = localComicRepository
                ).importFiles(uris)) {
                    is LocalImportResult.Success -> {
                        localImportState = LocalImportUiState(
                            LocalImportStatus.SUCCESS,
                            "已导入：${result.comic.title}（${result.comic.pageCount} 页）"
                        )
                        actionMessage = infoMessage(localImportState.message!!)
                    }
                    is LocalImportResult.Duplicate -> {
                        localImportState = LocalImportUiState(
                            LocalImportStatus.SUCCESS,
                            "文件已在本地书架：${result.existing.title}"
                        )
                        actionMessage = infoMessage(localImportState.message!!)
                    }
                    is LocalImportResult.Empty -> {
                        localImportState = LocalImportUiState(LocalImportStatus.EMPTY, result.message)
                    }
                    is LocalImportResult.Error -> {
                        localImportState = LocalImportUiState(LocalImportStatus.ERROR, result.message)
                        actionMessage = failureMessage(result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = "导入失败：${error.message ?: "未知错误"}"
                localImportState = LocalImportUiState(LocalImportStatus.ERROR, message)
                actionMessage = failureMessage(message)
            }
        }
    }

    fun importLocalFolder(uri: Uri) {
        if (localImportJob?.isActive == true) return
        localImportJob = viewModelScope.launch {
            localImportState = LocalImportUiState(LocalImportStatus.LOADING, "正在扫描文件夹…")
            try {
                when (val result = LocalComicImporter(
                    context = appContext,
                    repository = localComicRepository
                ).importFolder(uri)) {
                    is LocalImportResult.Success -> {
                        localImportState = LocalImportUiState(
                            LocalImportStatus.SUCCESS,
                            "已导入：${result.comic.title}（${result.comic.pageCount} 页）"
                        )
                        actionMessage = infoMessage(localImportState.message!!)
                    }
                    is LocalImportResult.Duplicate -> {
                        localImportState = LocalImportUiState(
                            LocalImportStatus.SUCCESS,
                            "文件夹内容已在本地书架：${result.existing.title}"
                        )
                        actionMessage = infoMessage(localImportState.message!!)
                    }
                    is LocalImportResult.Empty -> localImportState =
                        LocalImportUiState(LocalImportStatus.EMPTY, result.message)
                    is LocalImportResult.Error -> {
                        localImportState = LocalImportUiState(LocalImportStatus.ERROR, result.message)
                        actionMessage = failureMessage(result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = "导入失败：${error.message ?: "未知错误"}"
                localImportState = LocalImportUiState(LocalImportStatus.ERROR, message)
                actionMessage = failureMessage(message)
            }
        }
    }

    fun openLocalComic(comic: LocalComic) {
        if (localDeleteJob?.isActive == true) return
        selectedLocalComicId = comic.id
        errorMessage = null
        actionMessage = null
        navigateTo(AppScreen.LOCAL_READER)
    }

    fun deleteLocalComic(comic: LocalComic) {
        if (localDeleteJob?.isActive == true) return
        localDeleteJob = viewModelScope.launch {
            try {
                localComicRepository.delete(comic.id)
                withContext(Dispatchers.IO) { File(comic.localPath).deleteRecursively() }
                actionMessage = infoMessage("已删除：${comic.title}")
                if (selectedLocalComicId == comic.id && screen == AppScreen.LOCAL_READER) back()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                actionMessage = failureMessage("删除失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun updateReadingProgress(page: Int) {
        val chapter = selectedChapter ?: return
        val totalPages = pages.size
        readerPage = page.coerceIn(1, totalPages.coerceAtLeast(1))
        val generation = readerGeneration
        val pageToPersist = readerPage
        readerProgressJob?.cancel()
        readerProgressJob = viewModelScope.launch {
            try {
                readerProgressMutex.withLock {
                    withContext(NonCancellable) {
                        library.updateProgress(chapter, pageToPersist, totalPages)
                    }.also {
                        if (generation == readerGeneration && selectedChapter?.id == chapter.id) {
                            resumePage = it.currentPage
                        }
                    }
                }
            } catch (storageError: Throwable) {
                if (storageError is CancellationException) throw storageError
                if (generation == readerGeneration && selectedChapter?.id == chapter.id) {
                    errorMessage = "阅读进度保存失败：${storageError.message ?: "未知错误"}"
                }
            }
        }
    }

    /**
     * Reader navigation is based on chapter number, not the order returned by
     * the site. MYCOMIC lists newest chapters first, while the local source
     * lists them oldest first; using list indexes made “下一章” stop at 第 1
     * 话 or move in the opposite direction depending on the source.
     */
    fun previousChapter(): Chapter? = adjacentChapter(offset = -1)

    fun nextChapter(): Chapter? = adjacentChapter(offset = 1)

    private fun adjacentChapter(offset: Int): Chapter? {
        return adjacentChapter(
            chapters = selectedDetail?.chapters.orEmpty(),
            currentId = selectedChapter?.id,
            offset = offset
        )
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
        showRoot(AppScreen.SEARCH)
    }

    fun showLibrary() {
        showRoot(AppScreen.LIBRARY)
    }

    fun showSources() {
        showRoot(AppScreen.SOURCES)
    }

    fun back() {
        if (backStack.size <= 1) return
        navigationJob?.cancel()
        navigationJob = null
        if (screen == AppScreen.READER || screen == AppScreen.WEB_READER) {
            invalidateReaderSession()
        }
        isLoading = false
        popScreen()
        pendingWebAction = null
        if (screen != AppScreen.LOCAL_READER) selectedLocalComicId = null
    }

    private fun navigateTo(target: AppScreen) {
        if (backStack.lastOrNull() == target) {
            screen = target
            return
        }
        backStack += target
        screen = target
    }

    private fun showRoot(target: AppScreen) {
        backStack.clear()
        backStack += AppScreen.SEARCH
        if (target != AppScreen.SEARCH) backStack += target
        screen = target
    }

    private fun popScreen() {
        if (backStack.size <= 1) return
        backStack.removeAt(backStack.lastIndex)
        screen = backStack.last()
    }

    private fun beginReaderSession(): Long {
        readerGeneration += 1
        onlineImageLoader.clearMemory()
        readerRetryVersions = emptyMap()
        readerProgressJob?.cancel()
        return readerGeneration
    }

    private fun invalidateReaderSession() {
        readerGeneration += 1
        onlineImageLoader.clearMemory()
        readerRetryVersions = emptyMap()
        readerProgressJob?.cancel()
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
        actionMessage = null
        val found = mutableListOf<ComicSummary>()
        val failures = mutableListOf<String>()
        var challengeUrl: String? = null
        registry.sources.forEach { source ->
            try {
                found += source.search(value, page)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is MyComicChallengeException && challengeUrl == null) {
                    challengeUrl = error.url
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
        if (failures.isNotEmpty()) {
            errorMessage = if (found.isEmpty()) {
                "漫画源加载失败：${failures.joinToString("、")}"
            } else {
                "部分漫画源暂时不可用：${failures.joinToString("、")}"
            }
        }
        if (challengeUrl != null && found.isEmpty()) {
            webReaderUrl = challengeUrl
            pendingWebAction = PendingWebAction.Search(value, page, append)
            navigateTo(AppScreen.WEB_READER)
            actionMessage = infoMessage("请在网页会话中完成验证，然后点击“验证完成，重试”")
        } else {
            pendingWebAction = null
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
        val isMyComicAsset = runCatching {
            URI(url).host?.lowercase()?.let { host ->
                host == "biccam.com" || host.endsWith(".biccam.com")
            } == true
        }.getOrDefault(false)
        val request = if (isMyComicAsset && myComicWebSession != null) {
            NetworkRequest(
                url = url,
                headers = myComicWebSession.imageRequestHeaders(
                    url = url,
                    referer = selectedChapter?.id
                        ?: selectedDetail?.summary?.id
                        ?: MyComicSource.SITE_URL
                ),
                sourceId = MyComicSource.SOURCE_ID,
                bodyMode = com.comichub.source.runtime.NetworkBodyMode.BINARY,
                maxResponseBytes = MAX_IMAGE_RESPONSE_BYTES
            )
        } else {
            NetworkRequest(
                url = url,
                bodyMode = com.comichub.source.runtime.NetworkBodyMode.BINARY,
                maxResponseBytes = MAX_IMAGE_RESPONSE_BYTES
            )
        }
        return when (
            val result = gateway.get(
                request,
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
        invalidateReaderSession()
        super.onCleared()
    }

    companion object {
        private const val MAX_IMAGE_RESPONSE_BYTES = 24L * 1024L * 1024L
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

internal fun adjacentChapter(
    chapters: List<Chapter>,
    currentId: String?,
    offset: Int
): Chapter? {
    if (currentId == null) return null
    val orderedChapters = chapters.sortedBy(Chapter::number)
    val currentIndex = orderedChapters.indexOfFirst { it.id == currentId }
    if (currentIndex < 0) return null
    return orderedChapters.getOrNull(currentIndex + offset)
}
