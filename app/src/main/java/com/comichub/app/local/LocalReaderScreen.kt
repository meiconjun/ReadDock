package com.comichub.app.local

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LocalReaderScreen(comicId: String, padding: PaddingValues) {
    val context = LocalContext.current
    val viewModel: LocalReaderViewModel = viewModel(
        key = "local-reader-$comicId",
        factory = LocalReaderViewModel.factory(context, comicId)
    )
    val state = viewModel.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.Black)
    ) {
        when (state.status) {
            LocalReaderStatus.LOADING -> CenteredReaderMessage {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("正在打开本地漫画…", color = Color.White)
            }
            LocalReaderStatus.EMPTY -> CenteredReaderMessage {
                Text("本地漫画没有可读取的页面", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::retry) { Text("重试") }
            }
            LocalReaderStatus.ERROR -> CenteredReaderMessage {
                Text(state.errorMessage ?: "本地漫画打开失败", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::retry) { Text("重新读取") }
            }
            LocalReaderStatus.SUCCESS -> {
                ReaderPageHeader(state)
                ReaderPageContent(state, viewModel::retry)
                ReaderPageControls(
                    currentPage = state.currentPage,
                    pageCount = state.pageCount,
                    previousEnabled = state.canGoPrevious,
                    nextEnabled = state.canGoNext,
                    onPrevious = viewModel::previousPage,
                    onNext = viewModel::nextPage
                )
            }
        }
    }
}

@Composable
private fun ReaderPageHeader(state: LocalReaderState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "第 ${state.currentPage}/${state.pageCount} 页",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            " · ${state.comic?.format.orEmpty()}",
            color = Color.LightGray,
            style = MaterialTheme.typography.labelLarge
        )
        state.progressErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ColumnScope.ReaderPageContent(state: LocalReaderState, onRetry: () -> Unit) {
    val content = state.content
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isPageLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("正在加载第 ${state.currentPage} 页…", color = Color.White)
            }
            state.pageErrorMessage != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(state.pageErrorMessage, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试本页") }
            }
            content?.bitmap != null -> Image(
                bitmap = content.bitmap.asImageBitmap(),
                contentDescription = "第 ${state.currentPage} 页",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            content?.text != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) { Text(content.text, color = Color.White) }
            else -> Text("当前页面没有可显示的内容", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ReaderPageControls(
    currentPage: Int,
    pageCount: Int,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val buttonColors = ButtonDefaults.textButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color(0xFF666666)
        )
        TextButton(
            onClick = onPrevious,
            enabled = previousEnabled,
            colors = buttonColors
        ) { Text("上一页") }
        Text("$currentPage / $pageCount", color = Color.LightGray)
        TextButton(
            onClick = onNext,
            enabled = nextEnabled,
            colors = buttonColors
        ) { Text("下一页") }
    }
}

@Composable
private fun CenteredReaderMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
