package com.readdock.app.ui

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.readdock.app.AppScreen
import com.readdock.app.MainActivity
import com.readdock.app.MainViewModel
import com.readdock.app.MessageTone
import com.readdock.app.UiMessage
import com.readdock.app.LocalImportStatus
import com.readdock.app.local.LocalReaderScreen
import com.readdock.app.reader.ZoomableReaderImage
import com.readdock.data.LocalComic
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

    DisposableEffect(activity, viewModel.screen) {
        val window = activity?.window
        val previousNavigationBarColor = window?.navigationBarColor
        if (viewModel.screen == AppScreen.READER || viewModel.screen == AppScreen.LOCAL_READER) {
            window?.navigationBarColor = Color.Black.toArgb()
        }
        onDispose {
            previousNavigationBarColor?.let { color ->
                window?.navigationBarColor = color
            }
        }
    }

    Scaffold(
        topBar = {
            if (viewModel.screen != AppScreen.SEARCH &&
                viewModel.screen != AppScreen.LIBRARY &&
                viewModel.screen != AppScreen.SOURCES
            ) {
                val isComicReader = viewModel.screen == AppScreen.READER ||
                    viewModel.screen == AppScreen.LOCAL_READER
                TopAppBar(
                    title = {
                        Text(
                            when (viewModel.screen) {
                                AppScreen.DETAIL -> viewModel.selectedDetail?.summary?.title ?: "漫画详情"
                                AppScreen.READER -> viewModel.selectedChapter?.title ?: "阅读"
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
            AppScreen.LIBRARY -> LibraryScreen(viewModel, libraryItems, readingHistory, localComics, padding)
            AppScreen.SOURCES -> SourcesScreen(viewModel, sourceHealth, padding)
            AppScreen.DETAIL -> DetailScreen(viewModel, savedIds, padding)
            AppScreen.READER -> ReaderScreen(viewModel, padding)
            AppScreen.LOCAL_READER -> viewModel.selectedLocalComicId?.let { id ->
                LocalReaderScreen(comicId = id, padding = padding)
            } ?: ErrorNotice("没有选择本地漫画")
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
        Text("ReadDock", style = MaterialTheme.typography.headlineMedium)
        Text(
            "可扩展的数据源漫画阅读器 · Beta",
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
            Button(
                onClick = { viewModel.search(input) },
                enabled = !viewModel.isLoading
            ) {
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
    localComics: List<LocalComic>,
    padding: PaddingValues
) {
    var pendingDelete by remember { mutableStateOf<LocalComic?>(null) }
    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importLocalFiles(listOf(it)) } }
    val multipleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importLocalFiles(uris) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::importLocalFolder) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("我的书架", style = MaterialTheme.typography.headlineMedium)
                Button(
                    onClick = { singlePicker.launch(arrayOf("*/*")) },
                    enabled = viewModel.localImportState.status != LocalImportStatus.LOADING
                ) {
                    Text("导入本地")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { multipleImagePicker.launch("image/*") },
                    enabled = viewModel.localImportState.status != LocalImportStatus.LOADING
                ) { Text("多选图片") }
                TextButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = viewModel.localImportState.status != LocalImportStatus.LOADING
                ) { Text("导入文件夹") }
            }
            when (viewModel.localImportState.status) {
                LocalImportStatus.LOADING -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(viewModel.localImportState.message ?: "正在导入…")
                }
                LocalImportStatus.SUCCESS -> viewModel.localImportState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                LocalImportStatus.EMPTY -> viewModel.localImportState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LocalImportStatus.ERROR -> viewModel.localImportState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                LocalImportStatus.IDLE -> Unit
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("本地漫画", style = MaterialTheme.typography.titleLarge)
        }
        if (localComics.isEmpty()) {
            item {
                Text(
                    "本地书架为空。可以导入单个文件、多选图片或整个图片文件夹。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(localComics, key = { "local::${it.id}" }) { comic ->
                LocalComicRow(
                    comic = comic,
                    onClick = { viewModel.openLocalComic(comic) },
                    onDelete = { pendingDelete = comic }
                )
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text("在线收藏", style = MaterialTheme.typography.titleLarge)
        }
        if (libraryItems.isEmpty()) {
            item {
                Text(
                    "还没有收藏漫画。在线漫画收藏会独立保存在这里。",
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
}

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
private fun LocalCover(path: String?, title: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { decodeCover(path) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "${title}封面",
            modifier = Modifier.size(54.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) { Text(title.take(1), style = MaterialTheme.typography.titleLarge) }
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
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
        Text("数据源插件", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "导入受信任的插件包，扩展可用的数据源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { picker.launch(arrayOf("application/json", "text/plain")) }) {
                Text("导入插件")
            }
        }
        Spacer(Modifier.height(12.dp))
        viewModel.pluginMessage?.let {
            InlineMessage(it)
            Spacer(Modifier.height(8.dp))
        }
        Text("插件仓库", style = MaterialTheme.typography.titleLarge)
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
        Text("数据源说明", style = MaterialTheme.typography.titleLarge)
        Text(
            "ReadDock Beta 不内置商业网站数据源。请只安装你有权使用、且符合目标网站条款和版权要求的插件。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
                    Text("作者：${detail.author}")
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
        item {
            ErrorNotice(viewModel.errorMessage)
            viewModel.actionMessage?.let { InlineMessage(it) }
        }
        item {
            val coverBitmap = remember(viewModel.coverBytes) {
                viewModel.coverBytes?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
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
            Text(
                detail.description.ifBlank { "暂无简介" },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text("章节", style = MaterialTheme.typography.titleLarge)
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
                    enabled = !viewModel.isLoading,
                    onPrevious = { previousChapter?.let(viewModel::openChapter) },
                    onChapterList = viewModel::back,
                    onNext = { nextChapter?.let(viewModel::openChapter) }
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
    enabled: Boolean,
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
            enabled = enabled && previousChapter != null,
            colors = buttonColors
        ) {
            Text("上一章")
        }
        TextButton(onClick = onChapterList, enabled = enabled, colors = buttonColors) {
            Text("章节列表")
        }
        TextButton(
            onClick = onNext,
            enabled = enabled && nextChapter != null,
            colors = buttonColors
        ) {
            Text("下一章")
        }
    }
}

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
        viewModel.isLoading -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(8.dp))
            Text("正在加载漫画…")
        }
        viewModel.errorMessage != null -> Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            ErrorNotice(viewModel.errorMessage)
            TextButton(onClick = viewModel::retrySearch, enabled = !viewModel.isLoading) {
                Text("重试")
            }
        }
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
