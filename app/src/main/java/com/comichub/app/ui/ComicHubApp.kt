package com.comichub.app.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.Image
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.comichub.app.AppScreen
import com.comichub.app.MainViewModel
import com.comichub.app.MessageTone
import com.comichub.app.UiMessage
import com.comichub.data.LibraryComic
import com.comichub.data.ReadingHistoryItem
import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicSummary
import com.comichub.source.runtime.RequestOutcome
import com.comichub.source.runtime.SourceHealthSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicHubApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(context))
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val readingHistory by viewModel.readingHistory.collectAsStateWithLifecycle()
    val sourceHealth by viewModel.sourceHealth.collectAsStateWithLifecycle()

    // Android's edge-swipe back gesture is dispatched through the activity's
    // OnBackPressedDispatcher. Handle it at the app level so every nested
    // screen follows the same in-app navigation chain instead of finishing
    // the activity and returning to the launcher.
    BackHandler(enabled = viewModel.screen != AppScreen.SEARCH) {
        viewModel.back()
    }

    Scaffold(
        topBar = {
            if (viewModel.screen != AppScreen.SEARCH &&
                viewModel.screen != AppScreen.LIBRARY &&
                viewModel.screen != AppScreen.SOURCES &&
                viewModel.screen != AppScreen.WEB_READER
            ) {
                TopAppBar(
                    title = {
                        Text(
                            when (viewModel.screen) {
                                AppScreen.DETAIL -> viewModel.selectedDetail?.summary?.title ?: "漫画详情"
                                AppScreen.READER -> viewModel.selectedChapter?.title ?: "阅读"
                                AppScreen.WEB_READER -> viewModel.selectedChapter?.title ?: "网页阅读"
                                else -> "ComicHub"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (viewModel.screen == AppScreen.SEARCH ||
                viewModel.screen == AppScreen.LIBRARY ||
                viewModel.screen == AppScreen.SOURCES
            ) {
                NavigationBar {
                    NavigationBarItem(
                        selected = viewModel.screen == AppScreen.SEARCH,
                        onClick = viewModel::showSearch,
                        icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                        label = { Text("发现") }
                    )
                    NavigationBarItem(
                        selected = viewModel.screen == AppScreen.LIBRARY,
                        onClick = viewModel::showLibrary,
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = "书架") },
                        label = { Text("书架") }
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
            AppScreen.LIBRARY -> LibraryScreen(viewModel, libraryItems, readingHistory, padding)
            AppScreen.SOURCES -> SourcesScreen(viewModel, sourceHealth, padding)
            AppScreen.DETAIL -> DetailScreen(viewModel, padding)
            AppScreen.READER -> ReaderScreen(viewModel, padding)
            AppScreen.WEB_READER -> WebReaderScreen(viewModel, padding)
        }
    }
}

@Composable
private fun SearchScreen(viewModel: MainViewModel, padding: PaddingValues) {
    var input by remember(viewModel.query) { mutableStateOf(viewModel.query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("ComicHub", style = MaterialTheme.typography.headlineMedium)
        Text(
            "插件化漫画阅读器原型",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
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
            Button(onClick = { viewModel.search(input) }) {
                Text("搜索")
            }
        }
        Spacer(Modifier.height(12.dp))
        StatusMessage(viewModel)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(viewModel.results, key = { comicUiKey(it) }) { comic ->
                ComicRow(comic = comic, onClick = { viewModel.openComic(comic) })
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (viewModel.searchPage > 1) {
                        TextButton(
                            onClick = viewModel::previousSearchPage,
                            enabled = !viewModel.isLoading
                        ) {
                            Text("上一页")
                        }
                    }
                    Text(
                        "第 ${viewModel.searchPage} 页",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = viewModel::nextSearchPage,
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
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("我的书架", style = MaterialTheme.typography.headlineMedium)
        }
        if (libraryItems.isEmpty()) {
            item {
                Text(
                    "还没有收藏漫画。先去发现页添加一本吧。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(libraryItems, key = { comicUiKey(it.sourceId, it.comicId) }) { comic ->
                val summary = comic.toSummary()
                ComicRow(comic = summary, onClick = { viewModel.openComic(summary) })
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("阅读历史", style = MaterialTheme.typography.titleLarge)
        }
        if (readingHistory.isEmpty()) {
            item {
                Text(
                    "打开章节后，最近阅读的章节和进度会显示在这里。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                readingHistory,
                key = { "${it.sourceId}::${it.comicId}::${it.chapterId}" }
            ) { history ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openComic(history.toSummary()) }
                ) {
                    ListItem(
                        headlineContent = { Text(history.comicTitle) },
                        supportingContent = {
                            Text(
                                "第 ${history.chapterNumber} 话 ${history.chapterTitle} · " +
                                    "第 ${history.currentPage}/${history.totalPages} 页"
                            )
                        },
                        trailingContent = {
                            Text(if (history.completed) "已读完" else "继续")
                        }
                    )
                }
            }
        }
    }
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
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("漫画源插件", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "从本地导入声明式 JSON 插件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { picker.launch(arrayOf("application/json", "text/plain")) }) {
                Text("导入")
            }
        }
        Spacer(Modifier.height(12.dp))
        viewModel.pluginMessage?.let {
            InlineMessage(it)
            Spacer(Modifier.height(8.dp))
        }
        Text("远程插件仓库", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.repositoryUrl,
            onValueChange = viewModel::updateRepositoryUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("仓库索引 URL") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.repositoryKeyId,
            onValueChange = viewModel::updateRepositoryKeyId,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("可信公钥 keyId") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.repositoryPublicKey,
            onValueChange = viewModel::updateRepositoryPublicKey,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("RSA 公钥 Base64") }
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = viewModel::refreshRepository) {
            Text("检查更新")
        }
        viewModel.repositoryMessage?.let {
            Spacer(Modifier.height(6.dp))
            InlineMessage(it)
        }
        if (viewModel.availableUpdates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("可用更新", style = MaterialTheme.typography.titleMedium)
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
        Text("源健康状态", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (sourceHealth.isEmpty()) {
            Text(
                "尚无网络请求记录；打开需要网络的漫画源后会在这里显示。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    (health.lastFailureMessage?.let { " · $it" } ?: "")
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
        Text("内置源", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("本地示例源") },
                supportingContent = { Text("用于验证搜索和阅读流程") },
                trailingContent = { Text("内置") }
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("MYCOMIC（网页会话）") },
                supportingContent = {
                    Text("MYCOMIC 全站数据源；搜索、详情和章节均使用浏览器会话解析")
                },
                trailingContent = { Text("内置") }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("已安装插件", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (viewModel.installedPlugins.isEmpty()) {
            Text(
                "还没有安装插件。可以导入 plugin-sdk/package.example.json。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(viewModel.installedPlugins, key = { it.id }) { plugin ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(plugin.name) },
                            supportingContent = { Text("${plugin.id} · v${plugin.version}") },
                            trailingContent = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val detail = viewModel.selectedDetail ?: return
    val saved = viewModel.isSaved(detail.summary)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
                    Text("作者：${detail.author}")
                }
                IconButton(onClick = viewModel::toggleSaved) {
                    Icon(
                        imageVector = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (saved) "取消收藏" else "收藏"
                    )
                }
            }
        }
        item {
            ErrorNotice(viewModel.errorMessage)
        }
        item {
            Text(detail.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("章节", style = MaterialTheme.typography.titleLarge)
        }
        items(detail.chapters, key = { it.id }) { chapter ->
            ChapterRow(chapter = chapter, onClick = { viewModel.openChapter(chapter) })
        }
    }
}

@Composable
private fun ReaderScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val listState = rememberLazyListState()
    val restoredPosition = remember(viewModel.selectedChapter?.id) { mutableStateOf(false) }

    LaunchedEffect(
        viewModel.selectedChapter?.id,
        viewModel.pages.size,
        viewModel.readerProgressLoaded
    ) {
        if (viewModel.pages.isNotEmpty() && viewModel.readerProgressLoaded) {
            listState.scrollToItem((viewModel.resumePage - 1).coerceIn(0, viewModel.pages.lastIndex))
            restoredPosition.value = true
        }
    }
    LaunchedEffect(viewModel.selectedChapter?.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (restoredPosition.value) viewModel.updateReadingProgress(index + 1)
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ErrorNotice(viewModel.errorMessage)
        }
        item {
            Text(
                "第 ${viewModel.readerPage}/${viewModel.pages.size.coerceAtLeast(1)} 页",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(viewModel.pages, key = { it.id }) { page ->
            Card(modifier = Modifier.fillMaxWidth()) {
                val bitmap = remember(viewModel.imageBytes[page.id]) {
                    viewModel.imageBytes[page.id]?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "第 ${page.index} 页",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            page.displayText ?: page.imageUrl ?: "图片页面 ${page.index}",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebReaderScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val url = viewModel.webReaderUrl ?: return
    var pageError by remember(url) { mutableStateOf<String?>(null) }
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = WebSettings.getDefaultUserAgent(context)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = !isAllowedWebHost(request.url.host)

                override fun onPageFinished(view: WebView, pageUrl: String) {
                    super.onPageFinished(view, pageUrl)
                    view.evaluateJavascript(PURE_IMAGE_READER_SCRIPT, null)
                    pageError = null
                    CookieManager.getInstance().flush()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        pageError = "网页加载失败：${error.description}"
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    LaunchedEffect(url) {
        if (webView.url != url) webView.loadUrl(url)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
        pageError?.let { message ->
            Text(
                message,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun isAllowedWebHost(host: String?): Boolean {
    val normalized = host?.lowercase() ?: return false
    return normalized == "mycomic.com" || normalized.endsWith(".mycomic.com") ||
        normalized == "biccam.com" || normalized.endsWith(".biccam.com")
}

/**
 * Keep the WebView as the browser session for Cloudflare/cookie handling, but
 * turn a successfully loaded chapter into the reader's image-only surface.
 * Challenge/error pages have no `.page` images, so they remain visible for the
 * user to complete the required interaction.
 */
private val PURE_IMAGE_READER_SCRIPT = """
    (function() {
        function render() {
            const pages = Array.from(document.querySelectorAll('img.page'));
            if (!pages.length) return 'no-pages';

            const sources = pages.map(function(page) {
                return page.currentSrc || page.getAttribute('src') ||
                    page.getAttribute('data-src') || page.getAttribute('data-original');
            }).filter(Boolean);

            // Replacing the whole document removes the site's header, scripts,
            // breadcrumb, pagination controls, footer, and any later re-render.
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
            return String(sources.length);
        }

        render();
        // Some pages populate the image list just after load. These retries
        // still run on the original page, but become no-ops after replacement.
        setTimeout(render, 500);
        setTimeout(render, 1500);
    })();
""".trimIndent()

@Composable
private fun ComicRow(comic: ComicSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = { Text(comic.title) },
            supportingContent = { Text(comic.tags.joinToString(" · ")) },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(comic.title.take(1), style = MaterialTheme.typography.titleLarge)
                }
            }
        )
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
private fun StatusMessage(viewModel: MainViewModel) {
    when {
        viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        viewModel.errorMessage != null -> ErrorNotice(viewModel.errorMessage)
        viewModel.results.isEmpty() -> Text(
            "没有找到漫画，试试其他关键词。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            MaterialTheme.colorScheme.primary
        }
    )
}

@Composable
private fun ErrorNotice(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}
