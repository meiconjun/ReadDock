package com.readdock.app.local

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.readdock.app.reader.ZoomableReaderImage
import kotlin.math.roundToInt

@Composable
fun LocalReaderScreen(
    comicId: String,
    padding: PaddingValues,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit
) {
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
                var sliderPage by remember(state.comic?.id) {
                    mutableStateOf(state.currentPage.toFloat())
                }
                var sliderDragging by remember(state.comic?.id) { mutableStateOf(false) }
                LaunchedEffect(state.currentPage, sliderDragging) {
                    if (!sliderDragging) sliderPage = state.currentPage.toFloat()
                }
                if (chromeVisible) {
                    ReaderPageHeader(state)
                }
                ReaderPageContent(
                    state = state,
                    onRetry = viewModel::retry,
                    onPreviousPage = viewModel::previousPage,
                    onNextPage = viewModel::nextPage,
                    onToggleChrome = onToggleChrome
                )
                if (chromeVisible) ReaderPageControls(
                        currentPage = state.currentPage,
                        pageCount = state.pageCount,
                        sliderPage = sliderPage,
                        previousEnabled = state.canGoPrevious,
                        nextEnabled = state.canGoNext,
                        onPrevious = viewModel::previousPage,
                        onNext = viewModel::nextPage,
                        onSliderChange = {
                            sliderDragging = true
                            sliderPage = it
                        },
                        onSliderFinished = {
                            sliderDragging = false
                            viewModel.goToPage(sliderPage.roundToInt())
                        }
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
private fun ColumnScope.ReaderPageContent(
    state: LocalReaderState,
    onRetry: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleChrome: () -> Unit
) {
    val content = state.content
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isPageLoading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.pointerInput(state.currentPage) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("正在加载第 ${state.currentPage} 页…", color = Color.White)
            }
            state.pageErrorMessage != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(24.dp)
                    .pointerInput(state.currentPage) {
                        detectTapGestures(onTap = { onToggleChrome() })
                    }
            ) {
                Text(state.pageErrorMessage, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试本页") }
            }
            content?.bitmap != null -> ZoomableReaderImage(
                image = content.bitmap.asImageBitmap(),
                pageKey = content.page,
                contentDescription = "第 ${state.currentPage} 页",
                modifier = Modifier.fillMaxSize(),
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onSingleTap = onToggleChrome
            )
            content?.text != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(state.comic?.id) {
                        detectTapGestures(onTap = { onToggleChrome() })
                    }
                    .padding(16.dp)
            ) { Text(content.text, color = Color.White) }
            else -> Text(
                "当前页面没有可显示的内容",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.pointerInput(state.currentPage) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
            )
        }
    }
}

@Composable
private fun ReaderPageControls(
    currentPage: Int,
    pageCount: Int,
    sliderPage: Float,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (pageCount > 1) {
            Slider(
                value = sliderPage.coerceIn(1f, pageCount.toFloat()),
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                valueRange = 1f..pageCount.toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
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
