package com.readdock.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.Image
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import com.readdock.app.AppScreen
import com.readdock.app.MainActivity
import com.readdock.app.MainViewModel
import com.readdock.app.MessageTone
import com.readdock.app.UiMessage
import com.readdock.app.LocalImportStatus
import com.readdock.app.LibraryTab
import com.readdock.app.LocalFilter
import com.readdock.app.LocalSort
import com.readdock.app.LocalViewMode
import com.readdock.app.OnlineShelfSection
import com.readdock.app.WebReaderLoadStatus
import com.readdock.app.local.LocalReaderScreen
import com.readdock.app.reader.ZoomableReaderImage
import com.readdock.data.LocalComic
import com.readdock.data.LocalCategory
import com.readdock.data.LibraryComic
import com.readdock.data.ReadingHistoryItem
import com.readdock.source.api.Chapter
import com.readdock.source.api.ComicSummary
import com.readdock.source.runtime.RequestOutcome
import com.readdock.source.runtime.SourceHealthSnapshot
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadDockApp() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(context))
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val readingHistory by viewModel.readingHistory.collectAsStateWithLifecycle()
    val localComics by viewModel.localComics.collectAsStateWithLifecycle()
    val localCategories by viewModel.localCategories.collectAsStateWithLifecycle()
    val sourceHealth by viewModel.sourceHealth.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()

    DisposableEffect(activity, viewModel) {
        activity?.setAppBackHandler {
            if (viewModel.canGoBack) {
                viewModel.back()
                true
            } else {
                false
            }
        }
        onDispose {
            activity?.setAppBackHandler(null)
        }
    }

    DisposableEffect(activity, viewModel.screen, viewModel.readerChromeVisible) {
        val window = activity?.window
        val previousNavigationBarColor = window?.navigationBarColor
        val isReader = viewModel.screen == AppScreen.READER ||
            viewModel.screen == AppScreen.WEB_READER ||
            viewModel.screen == AppScreen.LOCAL_READER
        val isImmersiveReader = (viewModel.screen == AppScreen.LOCAL_READER ||
            viewModel.screen == AppScreen.WEB_READER) &&
            !viewModel.readerChromeVisible
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (isReader) {
            window?.navigationBarColor = Color.Black.toArgb()
        }
        if (isImmersiveReader) {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            previousNavigationBarColor?.let { color ->
                window?.navigationBarColor = color
            }
        }
    }

    Scaffold(
        topBar = {
            if (viewModel.screen != AppScreen.SEARCH &&
                viewModel.screen != AppScreen.LIBRARY &&
                viewModel.screen != AppScreen.SOURCES &&
                viewModel.screen != AppScreen.WEB_READER &&
                (viewModel.screen != AppScreen.LOCAL_READER || viewModel.readerChromeVisible)
            ) {
                val isComicReader = viewModel.screen == AppScreen.READER ||
                    viewModel.screen == AppScreen.WEB_READER ||
                    viewModel.screen == AppScreen.LOCAL_READER
                TopAppBar(
                    title = {
                        Text(
                            when (viewModel.screen) {
                                AppScreen.DETAIL -> viewModel.selectedDetail?.summary?.title ?: "漫画详情"
                                AppScreen.READER -> viewModel.selectedChapter?.title ?: "阅读"
                                AppScreen.WEB_READER -> viewModel.selectedChapter?.title ?: "网页阅读"
                                AppScreen.LOCAL_READER -> localComics
                                    .firstOrNull { it.id == viewModel.selectedLocalComicId }
                                    ?.title
                                    ?: "本地阅读"
                                else -> "ReadDock"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isComicReader) Color.Black else MaterialTheme.colorScheme.surface,
                        titleContentColor = if (isComicReader) Color.White else MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = if (isComicReader) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (viewModel.screen == AppScreen.SEARCH ||
                viewModel.screen == AppScreen.LIBRARY ||
                viewModel.screen == AppScreen.SOURCES
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = viewModel.screen == AppScreen.SEARCH,
                        onClick = viewModel::showSearch,
                        icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                        label = { Text("发现") }
                    )
                    NavigationBarItem(
                        selected = viewModel.screen == AppScreen.LIBRARY &&
                            viewModel.libraryTab == LibraryTab.ONLINE,
                        onClick = viewModel::showLibrary,
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = "书架") },
                        label = { Text("书架") }
                    )
                    NavigationBarItem(
                        selected = viewModel.screen == AppScreen.LIBRARY &&
                            viewModel.libraryTab == LibraryTab.LOCAL,
                        onClick = viewModel::showLocalLibrary,
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "本地") },
                        label = { Text("本地") }
                    )
                    NavigationBarItem(
                        selected = viewModel.screen == AppScreen.SOURCES,
                        onClick = viewModel::showSources,
                        icon = { Icon(Icons.Default.Extension, contentDescription = "插件") },
                        label = { Text("插件") }
                    )
                }
            }
        }
    ) { padding ->
        when (viewModel.screen) {
            AppScreen.SEARCH -> SearchScreen(viewModel, padding)
            AppScreen.LIBRARY -> LibraryScreen(
                viewModel,
                libraryItems,
                readingHistory,
                localComics,
                localCategories,
                padding
            )
            AppScreen.SOURCES -> SourcesScreen(viewModel, sourceHealth, padding)
            AppScreen.DETAIL -> DetailScreen(viewModel, savedIds, padding)
            AppScreen.READER -> ReaderScreen(viewModel, padding)
            AppScreen.WEB_READER -> WebReaderScreen(viewModel, padding)
            AppScreen.LOCAL_READER -> viewModel.selectedLocalComicId?.let { id ->
                LocalReaderScreen(
                    comicId = id,
                    padding = padding,
                    chromeVisible = viewModel.readerChromeVisible,
                    onToggleChrome = viewModel::toggleReaderChrome
                )
            } ?: ErrorNotice("没有选择本地漫画")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebReaderScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val url = viewModel.webReaderUrl ?: return
    val allowedDomains = viewModel.webReaderAllowedDomains
    val generation = viewModel.webReaderGeneration
    val previousChapter = viewModel.previousChapter()
    val nextChapter = viewModel.nextChapter()
    var pageError by remember(generation, url) { mutableStateOf<String?>(null) }
    var pageLoading by remember(generation, url) { mutableStateOf(true) }
    var actualPageUrl by remember(generation, url) { mutableStateOf<String?>(null) }
    fun confirmVisibleReaderDocument(view: WebView, pageUrl: String) {
        if (viewModel.webReaderGeneration != generation || !isHttpReaderUrl(pageUrl)) return
        if (!isExpectedReaderUrl(pageUrl, url)) {
            pageError = "章节地址未切换到目标页面：$pageUrl"
            pageLoading = false
            viewModel.markWebReaderLoadFailed(generation, pageUrl, pageError!!)
            return
        }
        view.evaluateJavascript(READER_DOCUMENT_INSPECTION_SCRIPT) { inspection ->
            if (viewModel.webReaderGeneration != generation) return@evaluateJavascript
            val expectedPath = Uri.parse(url).path.orEmpty()
            if (expectedPath.isBlank() || !inspection.contains(expectedPath)) {
                pageError = "WebView 文档仍不是目标章节，已阻止显示为加载成功"
                pageLoading = false
                viewModel.markWebReaderLoadFailed(generation, pageUrl, pageError!!)
                return@evaluateJavascript
            }
            Log.d("ReadDockWebReader", "verified generation=$generation url=$pageUrl document=$inspection")
            actualPageUrl = pageUrl
            pageError = null
            pageLoading = false
            viewModel.markWebReaderLoadFinished(generation, pageUrl)
            CookieManager.getInstance().flush()
        }
    }
    val webView = remember(generation, url, allowedDomains) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = WebSettings.getDefaultUserAgent(context)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            // Keep WebView scrolling intact while treating a short press as
            // the reader chrome toggle. WebView's click listener is not
            // reliable for pages that handle their own touch events.
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            var touchDownX = 0f
            var touchDownY = 0f
            var touchDownAt = 0L
            var touchMoved = false
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        touchDownAt = SystemClock.uptimeMillis()
                        touchMoved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.x - touchDownX) > touchSlop ||
                            abs(event.y - touchDownY) > touchSlop
                        ) {
                            touchMoved = true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val isTap =
                            !touchMoved &&
                                abs(event.x - touchDownX) <= touchSlop &&
                                abs(event.y - touchDownY) <= touchSlop &&
                                SystemClock.uptimeMillis() - touchDownAt < 600L
                        if (isTap) viewModel.toggleReaderChrome()
                    }
                    MotionEvent.ACTION_CANCEL -> touchMoved = true
                }
                false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, pageUrl: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, pageUrl, favicon)
                    if (viewModel.webReaderGeneration != generation) return
                    if (!isHttpReaderUrl(pageUrl)) return
                    pageLoading = true
                    actualPageUrl = pageUrl
                    viewModel.markWebReaderLoadStarted(generation, pageUrl)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = !isAllowedWebHost(request.url.host, allowedDomains)

                override fun onPageFinished(view: WebView, pageUrl: String) {
                    super.onPageFinished(view, pageUrl)
                    if (viewModel.webReaderGeneration != generation) return
                    if (!isHttpReaderUrl(pageUrl)) return
                    view.evaluateJavascript(PURE_IMAGE_READER_SCRIPT, null)
                    confirmVisibleReaderDocument(view, pageUrl)
                }

                override fun onPageCommitVisible(view: WebView, pageUrl: String) {
                    super.onPageCommitVisible(view, pageUrl)
                    if (viewModel.webReaderGeneration != generation) return
                    if (!isHttpReaderUrl(pageUrl)) return
                    // A security-check page can replace its DOM in place after
                    // the initial load. Re-install the reader script when the
                    // visible document changes so a completed user check can
                    // continue into the image-only reader.
                    view.evaluateJavascript(PURE_IMAGE_READER_SCRIPT, null)
                    confirmVisibleReaderDocument(view, pageUrl)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame && isHttpReaderUrl(request.url.toString()) &&
                        viewModel.webReaderGeneration == generation
                    ) {
                        pageError = "网页加载失败：${error.description}"
                        pageLoading = false
                        actualPageUrl = request.url.toString()
                        viewModel.markWebReaderLoadFailed(
                            generation,
                            request.url.toString(),
                            error.description.toString()
                        )
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame && errorResponse.statusCode >= 400 &&
                        isHttpReaderUrl(request.url.toString()) &&
                        viewModel.webReaderGeneration == generation
                    ) {
                        pageError = "网页加载失败：HTTP ${errorResponse.statusCode}"
                        pageLoading = false
                        actualPageUrl = request.url.toString()
                        viewModel.markWebReaderLoadFailed(
                            generation,
                            request.url.toString(),
                            "HTTP ${errorResponse.statusCode}"
                        )
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    BackHandler { viewModel.back() }

    DisposableEffect(webView, generation) {
        onDispose {
            if (viewModel.webReaderGeneration == generation) {
                viewModel.markWebReaderLoadCanceled(generation)
            }
            webView.stopLoading()
            webView.destroy()
        }
    }
    LaunchedEffect(generation, url) {
        pageLoading = true
        pageError = null
        actualPageUrl = null
        viewModel.markWebReaderLoadStarted(generation, url)
        webView.stopLoading()
        webView.loadUrl(url)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        key(generation, url, allowedDomains) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (viewModel.readerChromeVisible) {
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = viewModel.selectedChapter?.title ?: "正在加载章节…",
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    ReaderControls(
                        previousChapter = previousChapter,
                        nextChapter = nextChapter,
                        onPrevious = viewModel::openPreviousChapter,
                        onChapterList = viewModel::back,
                        onNext = viewModel::openNextChapter
                    )
                }
            }
        }
        if (viewModel.readerChromeVisible && (viewModel.isLoading || pageLoading)) {
            Card(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when (viewModel.webReaderLoadStatus) {
                            WebReaderLoadStatus.CANCELED -> "章节加载已取消"
                            WebReaderLoadStatus.FAILED -> "章节加载失败"
                            else -> "正在加载章节…"
                        }
                    )
                }
            }
        }
        (pageError ?: viewModel.webReaderLoadError)?.let { message ->
            Card(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            pageError = null
                            pageLoading = true
                            viewModel.markWebReaderLoadStarted(generation, url)
                            webView.reload()
                        },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

private fun isAllowedWebHost(host: String?, domains: Set<String>): Boolean {
    val normalizedHost = host?.lowercase() ?: return false
    return domains.any { domain ->
        normalizedHost == domain || normalizedHost.endsWith(".$domain")
    }
}

private fun isHttpReaderUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)

private fun isExpectedReaderUrl(actualUrl: String, requestedUrl: String): Boolean {
    val actual = Uri.parse(actualUrl)
    val requested = Uri.parse(requestedUrl)
    return actual.scheme.equals(requested.scheme, ignoreCase = true) &&
        actual.host.equals(requested.host, ignoreCase = true) &&
        actual.path == requested.path
}

private val READER_DOCUMENT_INSPECTION_SCRIPT = """
    (function() {
        var heading = document.querySelector('h1, h2, [data-flux-heading]');
        var images = Array.prototype.slice.call(document.images || [])
            .map(function(image) { return image.currentSrc || image.src || ''; })
            .filter(function(source) { return source.length > 0; });
        return JSON.stringify({
            href: location.href,
            title: document.title || '',
            heading: heading ? (heading.innerText || heading.textContent || '') : '',
            imageCount: images.length,
            firstImage: images.length > 0 ? images[0] : ''
        });
    })()
""".trimIndent()

/** Keep the browser session, but remove site chrome after the chapter loads. */
private val PURE_IMAGE_READER_SCRIPT = """
    (function() {
        if (window.__readdockReaderInstalled) return 'already-installed';
        window.__readdockReaderInstalled = true;

        function render() {
            if (window.__readdockReaderRendered) return 'already-rendered';
            const pages = Array.from(document.querySelectorAll('img.page'));
            if (!pages.length) return 'no-pages';

            const sources = pages.map(function(page) {
                return page.currentSrc || page.getAttribute('src') ||
                    page.getAttribute('data-src') || page.getAttribute('data-original');
            }).filter(Boolean);

            document.documentElement.innerHTML =
                '<head><meta name="viewport" content="width=device-width, initial-scale=1">' +
                '<style>html,body{margin:0;padding:0;background:#000;}body{' +
                'display:flex;flex-direction:column;align-items:center;}img{' +
                'display:block;width:100%;height:auto;max-width:100%;object-fit:contain;}' +
                '</style></head><body></body>';
            const body = document.body;
            sources.forEach(function(source) {
                const image = document.createElement('img');
                image.src = source;
                image.loading = 'eager';
                body.appendChild(image);
            });
            window.__readdockReaderRendered = true;
            return String(sources.length);
        }

        render();
        [500, 1500, 3000, 6000, 12000, 30000].forEach(function(delay) {
            setTimeout(render, delay);
        });
        // Cloudflare may complete a human verification by mutating the
        // current document instead of doing a full navigation. Keep watching
        // briefly so the normal chapter DOM is rendered when it appears.
        const observer = new MutationObserver(render);
        observer.observe(document.documentElement, { childList: true, subtree: true });
        setTimeout(function() { observer.disconnect(); }, 45000);
    })();
""".trimIndent()

@Composable
private fun SearchScreen(viewModel: MainViewModel, padding: PaddingValues) {
    var input by remember(viewModel.query) { mutableStateOf(viewModel.query) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("发现", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "在已启用的数据源中搜索漫画",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("搜索漫画") }
                        )
                        Button(
                            onClick = { viewModel.search(input) },
                            enabled = !viewModel.isLoading,
                            modifier = Modifier.height(56.dp),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text("搜索")
                        }
                    }
                }
            }
        }
        item { StatusMessage(viewModel) }
        if (viewModel.results.isNotEmpty()) {
            item {
                ScreenSectionHeading("搜索结果", "${viewModel.results.size} 条结果 · 第 ${viewModel.searchPage} 页")
            }
            items(viewModel.results, key = { comicUiKey(it) }) { comic ->
                ComicRow(
                    comic = comic,
                    sourceLabel = viewModel.sourceLabel(comic.sourceId),
                    onClick = { viewModel.openComic(comic) }
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = viewModel::previousSearchPage,
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.searchPage > 1 && !viewModel.isLoading
                    ) {
                        Text("上一页")
                    }
                    Text(
                        "第 ${viewModel.searchPage} 页",
                        modifier = Modifier.weight(0.7f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = viewModel::nextSearchPage,
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.isLoading
                    ) {
                        Text("下一页")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    viewModel: MainViewModel,
    libraryItems: List<LibraryComic>,
    readingHistory: List<ReadingHistoryItem>,
    localComics: List<LocalComic>,
    localCategories: List<LocalCategory>,
    padding: PaddingValues
) {
    var pendingDelete by remember { mutableStateOf<LocalComic?>(null) }
    var pendingDeleteCategory by remember { mutableStateOf<LocalCategory?>(null) }
    var assigningComic by remember { mutableStateOf<LocalComic?>(null) }
    var renamingCategory by remember { mutableStateOf<LocalCategory?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importLocalFiles(listOf(it)) } }
    val multipleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importLocalFiles(uris) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::importLocalFolder) }

    val activeCategory = localCategories.firstOrNull { it.id == viewModel.selectedLocalCategoryId }
    val filteredLocalComics = localComics.filter { comic ->
        val matchesStatus = when (viewModel.localFilter) {
            LocalFilter.ALL -> true
            LocalFilter.UNREAD -> !comic.hasBeenOpened
            LocalFilter.READING -> comic.hasBeenOpened && !comic.completed
            LocalFilter.COMPLETED -> comic.completed
        }
        val matchesCategory = activeCategory == null || activeCategory.id in comic.categoryIds
        matchesStatus && matchesCategory
    }
    val localComparator = when (viewModel.localSort) {
        LocalSort.RECENT_READ -> compareBy<LocalComic> { it.lastReadAt }.thenBy { it.updatedAt }
        LocalSort.RECENT_ADDED -> compareBy { it.createdAt }
        LocalSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LocalSort.FILE_NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName }
        LocalSort.PROGRESS -> compareBy<LocalComic> {
            it.currentPage.toFloat() / it.pageCount.coerceAtLeast(1).toFloat()
        }
    }
    val sortedLocalComics = filteredLocalComics.sortedWith(
        if (viewModel.localSortAscending) localComparator else localComparator.reversed()
    )
    val localRows = if (viewModel.localViewMode == LocalViewMode.GRID) {
        sortedLocalComics.chunked(2)
    } else {
        sortedLocalComics.map { listOf(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp)
    ) {
        item {
            LibraryPageHeader(
                title = if (viewModel.libraryTab == LibraryTab.ONLINE) "书架" else "本地",
                supporting = if (viewModel.libraryTab == LibraryTab.ONLINE) {
                    "按数据源保存收藏与阅读进度"
                } else {
                    "设备内的漫画文件与阅读进度"
                },
                count = if (viewModel.libraryTab == LibraryTab.ONLINE) {
                    if (viewModel.onlineShelfSection == OnlineShelfSection.FAVORITES) {
                        "${libraryItems.size} 部收藏"
                    } else {
                        "${readingHistory.size} 条记录"
                    }
                } else {
                    "${sortedLocalComics.size} 部漫画"
                }
            )
        }
        if (viewModel.libraryTab == LibraryTab.ONLINE) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        ShelfSegment(
                            firstLabel = "收藏",
                            secondLabel = "历史",
                            firstSelected = viewModel.onlineShelfSection == OnlineShelfSection.FAVORITES,
                            onFirst = { viewModel.selectOnlineShelfSection(OnlineShelfSection.FAVORITES) },
                            onSecond = { viewModel.selectOnlineShelfSection(OnlineShelfSection.HISTORY) },
                            modifier = Modifier.weight(1f)
                        )
                        if (viewModel.onlineShelfSection == OnlineShelfSection.HISTORY &&
                            readingHistory.isNotEmpty()
                        ) {
                            TextButton(onClick = { showClearHistory = true }) { Text("清空") }
                        }
                    }
                }
            }
            item { StatusMessage(viewModel) }
            if (viewModel.onlineShelfSection == OnlineShelfSection.FAVORITES) {
                if (libraryItems.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "还没有收藏",
                            message = "在在线漫画详情页点按书签，收藏会出现在这里。"
                        )
                    }
                } else {
                    items(libraryItems, key = { comicUiKey(it.sourceId, it.comicId) }) { comic ->
                        val summary = comic.toSummary()
                        ComicRow(
                            comic = summary,
                            sourceLabel = viewModel.sourceLabel(summary.sourceId),
                            onClick = { viewModel.openComic(summary) }
                        )
                    }
                }
            } else if (readingHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "还没有阅读历史",
                        message = "打开在线章节后，当前章节、页码和最后阅读时间会显示在这里。"
                    )
                }
            } else {
                items(
                    readingHistory,
                    key = { "${it.sourceId}::${it.comicId}::${it.chapterId}" }
                ) { history ->
                    HistoryShelfRow(
                        history = history,
                        sourceLabel = viewModel.sourceLabel(history.sourceId),
                        onClick = { viewModel.openHistory(history) }
                    )
                }
            }
        } else {
            item {
                LocalImportToolbar(
                    busy = viewModel.localImportState.status == LocalImportStatus.LOADING,
                    onImportFile = { singlePicker.launch(arrayOf("*/*")) },
                    onImportImages = { multipleImagePicker.launch("image/*") },
                    onImportFolder = { folderPicker.launch(null) },
                    onManageCategories = { showCategoryManager = true }
                )
            }
            item {
                when (viewModel.localImportState.status) {
                    LocalImportStatus.LOADING -> Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(viewModel.localImportState.message ?: "正在导入…")
                        }
                    }
                    LocalImportStatus.SUCCESS,
                    LocalImportStatus.EMPTY -> viewModel.localImportState.message?.let {
                        InlineMessageCard(it, MessageTone.INFO)
                    }
                    LocalImportStatus.ERROR -> viewModel.localImportState.message?.let {
                        InlineMessageCard(it, MessageTone.ERROR)
                    }
                    LocalImportStatus.IDLE -> Unit
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("全部漫画", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${sortedLocalComics.size} / ${localComics.size} 部",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ViewModeToggle(
                        mode = viewModel.localViewMode,
                        onList = { viewModel.selectLocalViewMode(LocalViewMode.LIST) },
                        onGrid = { viewModel.selectLocalViewMode(LocalViewMode.GRID) }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    CompactActionButton(
                        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        label = localFilterLabel(viewModel.localFilter),
                        onClick = {
                            viewModel.selectLocalFilter(
                                nextLocalFilter(viewModel.localFilter)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        CompactActionButton(
                            icon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            label = localSortLabel(viewModel.localSort),
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            LocalSort.values().forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(localSortLabel(sort)) },
                                    onClick = {
                                        viewModel.selectLocalSort(sort)
                                        sortMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (viewModel.localSort == sort) {
                                            Icon(Icons.Default.Check, contentDescription = "当前排序")
                                        }
                                    }
                                )
                            }
                        }
                    }
                    CompactActionButton(
                        label = if (viewModel.localSortAscending) "升序" else "降序",
                        onClick = viewModel::toggleLocalSortDirection,
                        modifier = Modifier.weight(0.72f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactActionButton(
                            label = activeCategory?.name ?: "全部分类",
                            onClick = { categoryMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部分类") },
                                onClick = {
                                    viewModel.selectLocalCategory(null)
                                    categoryMenuExpanded = false
                                }
                            )
                            localCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        viewModel.selectLocalCategory(category.id)
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        "${sortedLocalComics.size}/${localComics.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (sortedLocalComics.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = if (localComics.isEmpty()) "还没有本地漫画" else "没有符合条件的漫画",
                        message = if (localComics.isEmpty()) {
                            "可以导入单个文件、多选图片或整个图片文件夹。"
                        } else {
                            "可以切换筛选、分类或排序条件。"
                        }
                    )
                }
            } else {
                items(
                    localRows,
                    key = { row -> row.joinToString("|") { comic -> "local::${comic.id}" } }
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { comic ->
                            if (viewModel.localViewMode == LocalViewMode.GRID) {
                                LocalComicGridCard(
                                    comic = comic,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.openLocalComic(comic) },
                                    onAssign = { assigningComic = comic },
                                    onDelete = { pendingDelete = comic }
                                )
                            } else {
                                LocalComicListCard(
                                    comic = comic,
                                    onClick = { viewModel.openLocalComic(comic) },
                                    onAssign = { assigningComic = comic },
                                    onDelete = { pendingDelete = comic }
                                )
                            }
                        }
                        if (viewModel.localViewMode == LocalViewMode.GRID && row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { comic ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除本地漫画？") },
            text = { Text("将删除“${comic.title}”及其 App 内的本地文件，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteLocalComic(comic)
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = localCategories,
            onDismiss = { showCategoryManager = false },
            onCreate = viewModel::createLocalCategory,
            onRename = {
                showCategoryManager = false
                renamingCategory = it
            },
            onDelete = {
                showCategoryManager = false
                pendingDeleteCategory = it
            }
        )
    }
    renamingCategory?.let { category ->
        RenameCategoryDialog(
            category = category,
            onDismiss = { renamingCategory = null },
            onConfirm = { name ->
                viewModel.renameLocalCategory(category, name)
                renamingCategory = null
            }
        )
    }
    pendingDeleteCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            title = { Text("删除分类？") },
            text = { Text("删除“${category.name}”不会删除漫画，只会移除分类归属。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalCategory(category)
                    pendingDeleteCategory = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) { Text("取消") }
            }
        )
    }
    assigningComic?.let { comic ->
        LocalCategoryAssignmentDialog(
            comic = comic,
            categories = localCategories,
            onDismiss = { assigningComic = null },
            onToggle = { category, included ->
                viewModel.setLocalCategory(comic, category, included)
            }
        )
    }
    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("清空阅读历史？") },
            text = { Text("在线阅读历史和进度记录会被删除，收藏不会受到影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearOnlineHistory()
                    showClearHistory = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LibraryPageHeader(title: String, supporting: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            count,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun LocalImportToolbar(
    busy: Boolean,
    onImportFile: () -> Unit,
    onImportImages: () -> Unit,
    onImportFolder: () -> Unit,
    onManageCategories: () -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Button(
                onClick = onImportFile,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("导入漫画", maxLines = 1)
            }
            CompactActionButton(
                label = "分类",
                onClick = onManageCategories,
                modifier = Modifier.weight(0.72f)
            )
            Box {
                CompactActionButton(
                    icon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                    label = "更多",
                    onClick = { moreExpanded = true },
                    modifier = Modifier.width(84.dp)
                )
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("多选图片") },
                        onClick = {
                            moreExpanded = false
                            onImportImages()
                        },
                        enabled = !busy
                    )
                    DropdownMenuItem(
                        text = { Text("导入文件夹") },
                        onClick = {
                            moreExpanded = false
                            onImportFolder()
                        },
                        enabled = !busy
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.size(6.dp))
            Text(
                label,
                maxLines = 1,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ViewModeToggle(
    mode: LocalViewMode,
    onList: () -> Unit,
    onGrid: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            ViewModeButton(
                selected = mode == LocalViewMode.LIST,
                imageVector = Icons.Default.ViewList,
                contentDescription = "列表视图",
                onClick = onList
            )
            ViewModeButton(
                selected = mode == LocalViewMode.GRID,
                imageVector = Icons.Default.GridView,
                contentDescription = "网格视图",
                onClick = onGrid
            )
        }
    }
}

@Composable
private fun ViewModeButton(
    selected: Boolean,
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                MaterialTheme.shapes.extraSmall
            )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProgressRail(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.shapes.extraSmall
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.extraSmall)
        )
    }
}

@Composable
private fun ShelfSegment(
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = if (firstSelected) 0 else 1,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.secondary
    ) {
        Tab(
            selected = firstSelected,
            onClick = onFirst,
            modifier = Modifier.heightIn(min = 48.dp),
            text = { Text(firstLabel) }
        )
        Tab(
            selected = !firstSelected,
            onClick = onSecond,
            modifier = Modifier.heightIn(min = 48.dp),
            text = { Text(secondLabel) }
        )
    }
}

@Composable
private fun SourceBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.secondary,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusBadge(label: String) {
    val active = label == "阅读中"
    Surface(
        color = if (active) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        border = if (active) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
        } else {
            null
        }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun CoverMonogram(title: String, modifier: Modifier = Modifier.size(64.dp)) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    title.take(1),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryShelfRow(
    history: ReadingHistoryItem,
    sourceLabel: String,
    onClick: () -> Unit
) {
    val progress = history.currentPage.toFloat() / history.totalPages.coerceAtLeast(1).toFloat()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            CoverMonogram(history.comicTitle, Modifier.size(64.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(history.comicTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                SourceBadge(sourceLabel)
                Text(
                    "第 ${history.chapterNumber} 话 · ${history.chapterTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProgressRail(progress)
                Text(
                    "第 ${history.currentPage}/${history.totalPages} 页 · ${readingTimeLabel(history.lastReadAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (history.completed) "已读完" else "继续",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun LocalComicListCard(
    comic: LocalComic,
    onClick: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = comic.currentPage.toFloat() / comic.pageCount.coerceAtLeast(1).toFloat()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            LocalCover(comic.coverPath, comic.title, Modifier.size(72.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(comic.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(localStatusLabel(comic))
                    Text(
                        comic.format,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ProgressRail(progress)
                Text(
                    "${comic.currentPage.coerceIn(1, comic.pageCount.coerceAtLeast(1))}/${comic.pageCount} 页 · ${localLastReadLabel(comic)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                IconButton(onClick = onAssign, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "管理分类")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除本地漫画")
                }
            }
        }
    }
}

@Composable
private fun LocalComicGridCard(
    comic: LocalComic,
    modifier: Modifier,
    onClick: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = comic.currentPage.toFloat() / comic.pageCount.coerceAtLeast(1).toFloat()
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            LocalCover(comic.coverPath, comic.title, Modifier.fillMaxWidth().height(156.dp))
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(comic.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                StatusBadge(localStatusLabel(comic))
                ProgressRail(progress)
                Text(
                    "${localStatusLabel(comic)} · ${comic.currentPage}/${comic.pageCount} 页",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onAssign) { Icon(Icons.Default.MoreVert, contentDescription = "管理分类") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "删除本地漫画") }
                }
            }
        }
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<LocalCategory>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (LocalCategory) -> Unit,
    onDelete: (LocalCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理分类") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("新分类名称") }
                )
                Button(
                    onClick = {
                        onCreate(name)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("创建分类") }
                if (categories.isEmpty()) {
                    Text("还没有分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(category.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRename(category) }) {
                                Icon(Icons.Default.Edit, contentDescription = "重命名分类")
                            }
                            IconButton(onClick = { onDelete(category) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除分类")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun RenameCategoryDialog(
    category: LocalCategory,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("分类名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun LocalCategoryAssignmentDialog(
    comic: LocalComic,
    categories: List<LocalCategory>,
    onDismiss: () -> Unit,
    onToggle: (LocalCategory, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置分类") },
        text = {
            if (categories.isEmpty()) {
                Text("请先创建分类。")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(category, category.id !in comic.categoryIds) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = category.id in comic.categoryIds,
                                onCheckedChange = { onToggle(category, it) }
                            )
                            Text(category.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

private fun nextLocalFilter(filter: LocalFilter): LocalFilter = when (filter) {
    LocalFilter.ALL -> LocalFilter.UNREAD
    LocalFilter.UNREAD -> LocalFilter.READING
    LocalFilter.READING -> LocalFilter.COMPLETED
    LocalFilter.COMPLETED -> LocalFilter.ALL
}

private fun localFilterLabel(filter: LocalFilter): String = when (filter) {
    LocalFilter.ALL -> "全部"
    LocalFilter.UNREAD -> "未读"
    LocalFilter.READING -> "阅读中"
    LocalFilter.COMPLETED -> "已读"
}

private fun localSortLabel(sort: LocalSort): String = when (sort) {
    LocalSort.RECENT_READ -> "最近阅读"
    LocalSort.RECENT_ADDED -> "最近添加"
    LocalSort.TITLE -> "标题"
    LocalSort.FILE_NAME -> "文件名"
    LocalSort.PROGRESS -> "阅读进度"
}

private fun localStatusLabel(comic: LocalComic): String = when {
    comic.completed -> "已读"
    !comic.hasBeenOpened -> "未读"
    else -> "阅读中"
}

private fun localLastReadLabel(comic: LocalComic): String = when {
    !comic.hasBeenOpened || comic.lastReadAt <= 0L -> "未打开"
    else -> readingTimeLabel(comic.lastReadAt)
}

private fun readingTimeLabel(timestamp: Long): String =
    if (timestamp <= 0L) "未记录时间" else java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(timestamp))

@Composable
private fun LocalComicRow(
    comic: LocalComic,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(comic.title) },
            supportingContent = {
                Text(
                    "${comic.format} · ${comic.pageCount} 页 · " +
                        "进度 ${comic.currentPage.coerceIn(1, comic.pageCount.coerceAtLeast(1))}/${comic.pageCount}"
                )
            },
            leadingContent = { LocalCover(comic.coverPath, comic.title) },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除本地漫画")
                }
            }
        )
    }
}

@Composable
private fun LocalCover(path: String?, title: String, modifier: Modifier = Modifier.size(54.dp)) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { decodeCover(path) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "${title}封面",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = "无封面",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun decodeCover(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = generateSequence(1) { it * 2 }
        .takeWhile { bounds.outWidth / it > 180 || bounds.outHeight / it > 240 }
        .lastOrNull() ?: 1
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun SourcesScreen(
    viewModel: MainViewModel,
    sourceHealth: Map<String, SourceHealthSnapshot>,
    padding: PaddingValues
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            viewModel.installPlugin(json.orEmpty())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("数据源插件", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "导入受信任的插件包，扩展可用的数据源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { picker.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导入插件")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        viewModel.pluginMessage?.let {
            InlineMessage(it)
            Spacer(Modifier.height(8.dp))
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("插件仓库", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = viewModel.repositoryUrl,
                    onValueChange = viewModel::updateRepositoryUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("仓库索引 URL") }
                )
                OutlinedTextField(
                    value = viewModel.repositoryKeyId,
                    onValueChange = viewModel::updateRepositoryKeyId,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("可信公钥 keyId") }
                )
                OutlinedTextField(
                    value = viewModel.repositoryPublicKey,
                    onValueChange = viewModel::updateRepositoryPublicKey,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("RSA 公钥 Base64") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = viewModel::refreshRepository,
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.repositoryBusy
                    ) {
                        Text("读取仓库")
                    }
                    OutlinedButton(
                        onClick = viewModel::clearRepositoryConfiguration,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清除配置")
                    }
                }
                if (viewModel.repositoryBusy) {
                    TextButton(
                        onClick = viewModel::cancelRepositoryOperation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消读取")
                    }
                }
            }
        }
        viewModel.repositoryMessage?.let {
            Spacer(Modifier.height(6.dp))
            InlineMessage(it)
        }
        if (viewModel.repositoryIndex != null) {
            Spacer(Modifier.height(16.dp))
            ScreenSectionHeading("仓库插件", "${viewModel.repositoryPlugins.size} 个可用插件")
            Spacer(Modifier.height(8.dp))
            if (viewModel.repositoryPlugins.isEmpty()) {
                EmptyStateCard(
                    title = "仓库暂无插件",
                    message = "请检查仓库索引，或稍后重新读取。"
                )
            } else {
                val installedById = viewModel.installedPlugins.associateBy { it.id }
                viewModel.repositoryPlugins.forEach { entry ->
                    val installed = installedById[entry.id]
                    val hasUpdate = viewModel.availableUpdates.any { it.available.id == entry.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.id} · v${entry.version}")
                            entry.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            if (entry.domains.isNotEmpty()) {
                                Text("域名：${entry.domains.joinToString("、")}")
                            }
                            if (entry.permissions.isNotEmpty()) {
                                Text(
                                    "权限：${entry.permissions.joinToString("、") { it.name.lowercase() }}"
                                )
                            }
                            if (entry.capabilities.isNotEmpty()) {
                                Text(
                                    "能力：${entry.capabilities.joinToString("、") { it.name.lowercase() }}"
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.installRepositoryPlugin(entry) },
                                    enabled = !viewModel.repositoryBusy &&
                                        (installed == null || hasUpdate)
                                ) {
                                    Text(if (installed == null) "安装" else if (hasUpdate) "更新" else "已安装")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (viewModel.availableUpdates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ScreenSectionHeading("可用更新", "${viewModel.availableUpdates.size} 个插件可以更新")
            viewModel.availableUpdates.forEach { update ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(update.available.name) },
                        supportingContent = {
                            Text("${update.installed.version} → ${update.available.version}")
                        },
                        trailingContent = {
                            TextButton(onClick = { viewModel.installUpdate(update) }) {
                                Text("安装")
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ScreenSectionHeading("源健康状态", "请求成功率和最近网络状态")
        Spacer(Modifier.height(8.dp))
        if (sourceHealth.isEmpty()) {
            EmptyStateCard(
                title = "尚无网络请求记录",
                message = "打开需要网络的漫画源后，健康状态会在这里显示。"
            )
        } else {
            sourceHealth.values.sortedBy { it.sourceId }.forEach { health ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(health.sourceId) },
                        supportingContent = {
                            Text(
                                "${health.host} · 成功率 ${health.successRatePercent}% · " +
                                    "请求 ${health.requestCount} · 最近 ${health.lastLatencyMs}ms" +
                                    if (health.lastFailureMessage != null) " · 最近请求失败" else ""
                            )
                        },
                        trailingContent = {
                            Text(sourceHealthLabel(health))
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("数据源说明", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ReadDock Beta 不内置商业网站数据源。请只安装你有权使用、且符合目标网站条款和版权要求的插件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ScreenSectionHeading("已安装插件", "管理启用状态、回滚和卸载")
        Spacer(Modifier.height(8.dp))
        if (viewModel.installedPlugins.isEmpty()) {
            EmptyStateCard(
                title = "还没有安装插件",
                message = "请从受信任的外部仓库安装，或导入带签名的插件包。"
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.installedPlugins.forEach { plugin ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                            Text("${plugin.id} · v${plugin.version}")
                            if (plugin.domains.isNotEmpty()) {
                                Text("域名：${plugin.domains.joinToString("、")}")
                            }
                            if (plugin.permissions.isNotEmpty()) {
                                Text(
                                    "权限：${plugin.permissions.joinToString("、") { it.name.lowercase() }}"
                                )
                            }
                            if (plugin.capabilities.isNotEmpty()) {
                                Text(
                                    "能力：${plugin.capabilities.joinToString("、") { it.name.lowercase() }}"
                                )
                            }
                            Text(
                                "限速：${plugin.rateLimit.requestsPerMinute}/分钟，" +
                                    "并发 ${plugin.rateLimit.concurrency}"
                            )
                            if (plugin.requiresUserInteraction) {
                                Text("需要用户交互")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("启用")
                                Switch(
                                    checked = plugin.enabled,
                                    onCheckedChange = { viewModel.setPluginEnabled(plugin.id, it) }
                                )
                                if (plugin.canRollback) {
                                    TextButton(onClick = { viewModel.rollbackPlugin(plugin.id) }) {
                                        Text("回滚")
                                    }
                                }
                                TextButton(onClick = { viewModel.uninstallPlugin(plugin.id) }) {
                                    Text("卸载")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    viewModel: MainViewModel,
    savedIds: Set<String>,
    padding: PaddingValues
) {
    val detail = viewModel.selectedDetail ?: return
    val saved = viewModel.isSaved(detail.summary, savedIds)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
                            Text("作者：${detail.author}")
                            Text(
                                "来源：${viewModel.sourceLabel(detail.summary.sourceId)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                detail.summary.tags.joinToString(" · ").ifBlank { "暂无标签" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleSaved,
                            enabled = !viewModel.isSaving
                        ) {
                            Icon(
                                imageVector = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (saved) "取消收藏" else "收藏"
                            )
                        }
                    }
                }
            }
        }
        item {
            if (viewModel.errorMessage != null || viewModel.actionMessage != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ErrorNotice(viewModel.errorMessage)
                        viewModel.actionMessage?.let { InlineMessage(it) }
                    }
                }
            }
        }
        item {
            val coverBitmap = remember(viewModel.coverBytes) {
                viewModel.coverBytes?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap,
                            contentDescription = "${detail.summary.title}封面",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                "${detail.summary.title}\n封面加载中或不可用",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text("简介", style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail.description.ifBlank { "暂无简介" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            ScreenSectionHeading("章节", "${detail.chapters.size} 话")
        }
        if (detail.chapters.isEmpty()) {
            item {
                Text("暂无章节", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(detail.chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    onClick = { viewModel.openChapter(chapter) }
                )
            }
        }
    }
}

@Composable
private fun ReaderScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val restoredPosition = remember(viewModel.selectedChapter?.id) { mutableStateOf(false) }
    val previousChapter = viewModel.previousChapter()
    val nextChapter = viewModel.nextChapter()

    DisposableEffect(viewModel.selectedChapter?.id) {
        onDispose { viewModel.flushReadingProgress() }
    }

    LaunchedEffect(
        viewModel.selectedChapter?.id,
        viewModel.pages.size,
        viewModel.readerProgressLoaded
    ) {
        if (viewModel.pages.isNotEmpty() && viewModel.readerProgressLoaded) {
            listState.scrollToItem(
                (READER_HEADER_ITEM_COUNT + viewModel.resumePage - 1)
                    .coerceIn(READER_HEADER_ITEM_COUNT, READER_HEADER_ITEM_COUNT + viewModel.pages.lastIndex)
            )
            restoredPosition.value = true
        }
    }
    LaunchedEffect(viewModel.selectedChapter?.id) {
            snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                val page = index - READER_HEADER_ITEM_COUNT + 1
                if (restoredPosition.value && page > 0) viewModel.updateReadingProgress(page)
            }
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(padding),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ErrorNotice(viewModel.errorMessage)
                viewModel.actionMessage?.let { InlineMessage(it) }
            }
        }
        item {
            if (viewModel.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("正在加载图片…", color = Color.White)
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                ReaderControls(
                    previousChapter = previousChapter,
                    nextChapter = nextChapter,
                    onPrevious = viewModel::openPreviousChapter,
                    onChapterList = viewModel::back,
                    onNext = viewModel::openNextChapter
                )
            }
        }
        item {
            Text(
                "第 ${viewModel.readerPage}/${viewModel.pages.size.coerceAtLeast(1)} 页",
                color = Color.LightGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(viewModel.pages, key = { it.id }) { page ->
            OnlineReaderPage(
                viewModel = viewModel,
                page = page,
                onPreviousPage = {
                    val target = (page.index - 2).coerceAtLeast(0)
                    coroutineScope.launch {
                        listState.animateScrollToItem(READER_HEADER_ITEM_COUNT + target)
                    }
                },
                onNextPage = {
                    val target = (page.index).coerceAtMost(viewModel.pages.lastIndex)
                    coroutineScope.launch {
                        listState.animateScrollToItem(READER_HEADER_ITEM_COUNT + target)
                    }
                }
            )
        }
    }
}

private data class OnlinePageUiState(
    val loading: Boolean,
    val bitmap: Bitmap? = null,
    val error: String? = null
)

@Composable
private fun OnlineReaderPage(
    viewModel: MainViewModel,
    page: com.readdock.source.api.ComicPage,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    val retryVersion = viewModel.readerRetryVersion(page.id)
    val pageState by produceState(
        initialValue = OnlinePageUiState(loading = page.imageUrl != null),
        viewModel.selectedChapter?.id,
        page.id,
        page.imageUrl,
        retryVersion
    ) {
        if (page.imageUrl == null) {
            value = OnlinePageUiState(loading = false)
            return@produceState
        }
        value = OnlinePageUiState(loading = true)
        try {
            value = OnlinePageUiState(
                loading = false,
                bitmap = withContext(Dispatchers.IO) { viewModel.loadReaderImage(page) }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            value = OnlinePageUiState(
                loading = false,
                error = "图片暂时无法显示，请重试"
            )
        }
    }

    when {
        pageState.bitmap != null -> {
            val bitmap = pageState.bitmap!!
            ZoomableReaderImage(
                image = bitmap.asImageBitmap(),
                pageKey = page.id,
                contentDescription = "第 ${page.index} 页",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                    .heightIn(min = 240.dp),
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage
            )
        }
        pageState.loading -> ReaderPagePlaceholder("正在加载第 ${page.index} 页…")
        pageState.error != null -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Color(0xFF171717))
                .padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "第 ${page.index} 页加载失败，请检查网络后重试",
                color = Color.LightGray
            )
            TextButton(
                onClick = { viewModel.retryReaderPage(page) },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) { Text("重试本页") }
        }
        else -> ReaderPagePlaceholder(page.displayText ?: "图片页面 ${page.index}")
    }
}

@Composable
private fun ReaderPagePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(Color(0xFF171717)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray
        )
    }
}

private const val READER_HEADER_ITEM_COUNT = 4

@Composable
private fun ReaderControls(
    previousChapter: Chapter?,
    nextChapter: Chapter?,
    onPrevious: () -> Unit,
    onChapterList: () -> Unit,
    onNext: () -> Unit
) {
    val buttonColors = ButtonDefaults.textButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color(0xFF666666)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = onPrevious,
            colors = buttonColors
        ) {
            Text(if (previousChapter == null) "上一章（无）" else "上一章")
        }
        TextButton(onClick = onChapterList, colors = buttonColors) {
            Text("章节列表")
        }
        TextButton(
            onClick = onNext,
            colors = buttonColors
        ) {
            Text(if (nextChapter == null) "下一章（无）" else "下一章")
        }
    }
}

@Composable
private fun ComicRow(
    comic: ComicSummary,
    sourceLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            CoverMonogram(comic.title)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(comic.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                SourceBadge(sourceLabel)
                comic.tags.joinToString(" · ").takeIf { it.isNotBlank() }?.let { tags ->
                    Text(
                        tags,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = { Text("第 ${chapter.number} 话  ${chapter.title}") },
            trailingContent = { Text("阅读") }
        )
    }
}

private fun comicUiKey(comic: ComicSummary): String = comicUiKey(comic.sourceId, comic.id)

private fun comicUiKey(sourceId: String, comicId: String): String = "$sourceId::$comicId"

private fun sourceHealthLabel(health: SourceHealthSnapshot): String = when (health.lastOutcome) {
    RequestOutcome.SUCCESS -> "正常"
    RequestOutcome.CACHE_HIT -> "缓存命中"
    RequestOutcome.HTTP_FAILURE -> "HTTP ${health.lastStatusCode ?: "失败"}"
    RequestOutcome.TRANSPORT_FAILURE -> "网络失败"
    RequestOutcome.CIRCUIT_OPEN -> "冷却中"
}

@Composable
private fun ScreenSectionHeading(title: String, supporting: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        supporting?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InlineMessageCard(message: String, tone: MessageTone) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
                color = if (tone == MessageTone.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.secondary
            }
        )
    }
}

@Composable
private fun StatusMessage(viewModel: MainViewModel) {
    when {
        viewModel.isLoading -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
                Text("正在加载漫画…")
            }
        }
        viewModel.errorMessage != null -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ErrorNotice(viewModel.errorMessage)
                }
                TextButton(
                    onClick = if (viewModel.canRetryComic) {
                        viewModel::retryOpenComic
                    } else {
                        viewModel::retrySearch
                    },
                    enabled = !viewModel.isLoading
                ) {
                    Text("重试")
                }
            }
        }
        viewModel.results.isEmpty() -> EmptyStateCard(
            title = "暂无搜索结果",
            message = if (viewModel.query.isBlank()) {
                "输入关键词后，可以从已安装的数据源中搜索漫画。"
            } else {
                "没有找到漫画，试试其他关键词。"
            }
        )
    }
}

@Composable
private fun InlineMessage(message: UiMessage) {
    Text(
        message.text,
        color = if (message.tone == MessageTone.ERROR) {
            MaterialTheme.colorScheme.error
        } else {
                MaterialTheme.colorScheme.secondary
        }
    )
}

@Composable
private fun ErrorNotice(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}
