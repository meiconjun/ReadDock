package com.readdock.app.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val MIN_SCALE = 1f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 4f
private const val HORIZONTAL_DOMINANCE = 1.2f
private const val DOUBLE_TAP_TIMEOUT_MS = 320L

/** A page image surface that keeps vertical parent scrolling independent from horizontal paging. */
@Composable
fun ZoomableReaderImage(
    image: ImageBitmap,
    pageKey: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onSingleTap: () -> Unit = {}
) {
    val density = LocalDensity.current
    val tapScope = rememberCoroutineScope()
    val touchSlop = with(density) { 18.dp.toPx() }
    val swipeThreshold = with(density) { 64.dp.toPx() }
    var scale by remember(pageKey) { mutableStateOf(MIN_SCALE) }
    var offset by remember(pageKey) { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember(pageKey) { mutableStateOf(0L) }
    var lastTapPosition by remember(pageKey) { mutableStateOf(Offset.Unspecified) }

    var containerSize by remember(pageKey) { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(pageKey, touchSlop, swipeThreshold) {
                var pendingTap: Job? = null
                try {
                    awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val start = down.position
                    var currentId: PointerId = pointerId
                    var horizontal = false
                    var vertical = false
                    var transformed = false
                    var multiTouch = false
                    var previousPinchDistance = 0f
                    var previousPinchCentroid = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) {
                            val end = event.changes.firstOrNull()?.position ?: start
                            val dx = end.x - start.x
                            if (!multiTouch && !transformed && !vertical && horizontal && abs(dx) >= swipeThreshold) {
                                if (dx > 0) onPreviousPage() else onNextPage()
                            } else if (!multiTouch && !transformed && !horizontal && !vertical && abs(dx) < touchSlop) {
                                val now = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                                val previousTap = lastTapTime
                                val previousPosition = lastTapPosition
                                if (
                                    previousTap > 0 &&
                                    now - previousTap <= DOUBLE_TAP_TIMEOUT_MS &&
                                    previousPosition != Offset.Unspecified &&
                                    (previousPosition - end).getDistance() <= 48f
                                ) {
                                    pendingTap?.cancel()
                                    if (scale > MIN_SCALE) {
                                        scale = MIN_SCALE
                                        offset = Offset.Zero
                                    } else {
                                        scale = DOUBLE_TAP_SCALE
                                        val focus = end - Offset(
                                            containerSize.width / 2f,
                                            containerSize.height / 2f
                                        )
                                        offset = boundedOffset(
                                            Offset(
                                                -focus.x * (DOUBLE_TAP_SCALE - 1f) / DOUBLE_TAP_SCALE,
                                                -focus.y * (DOUBLE_TAP_SCALE - 1f) / DOUBLE_TAP_SCALE
                                            ),
                                            DOUBLE_TAP_SCALE,
                                            containerSize.width,
                                            containerSize.height
                                        )
                                    }
                                    lastTapTime = 0L
                                    lastTapPosition = Offset.Unspecified
                                } else {
                                    pendingTap?.cancel()
                                    lastTapTime = now
                                    lastTapPosition = end
                                    pendingTap = tapScope.launch {
                                        delay(DOUBLE_TAP_TIMEOUT_MS)
                                        if (lastTapTime == now) {
                                            lastTapTime = 0L
                                            lastTapPosition = Offset.Unspecified
                                            onSingleTap()
                                        }
                                    }
                                }
                            }
                            break
                        }

                        if (active.size > 1) {
                            multiTouch = true
                            transformed = true
                            val first = active[0].position
                            val second = active[1].position
                            val centroid = Offset(
                                (first.x + second.x) / 2f,
                                (first.y + second.y) / 2f
                            )
                            val distance = (second - first).getDistance()
                            val zoom = if (previousPinchDistance > 0f) {
                                (distance / previousPinchDistance).coerceIn(0.5f, 2f)
                            } else {
                                1f
                            }
                            val pan = if (previousPinchDistance > 0f) {
                                centroid - previousPinchCentroid
                            } else {
                                Offset.Zero
                            }
                            scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            offset = boundedOffset(
                                offset + pan,
                                scale,
                                containerSize.width,
                                containerSize.height
                            )
                            previousPinchDistance = distance
                            previousPinchCentroid = centroid
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        previousPinchDistance = 0f

                        val change = active.firstOrNull { it.id == currentId } ?: active.first()
                        currentId = change.id
                        val delta = change.position - start
                        if (scale > MIN_SCALE) {
                            if (change.positionChange() != Offset.Zero) transformed = true
                            offset = boundedOffset(
                                offset + change.positionChange(),
                                scale,
                                containerSize.width,
                                containerSize.height
                            )
                            change.consume()
                        } else if (!horizontal && !vertical && delta.getDistance() > touchSlop) {
                            if (abs(delta.x) > abs(delta.y) * HORIZONTAL_DOMINANCE) {
                                horizontal = true
                                change.consume()
                            } else {
                                vertical = true
                                // Do not consume vertical movement. LazyColumn receives it.
                            }
                        } else if (horizontal) {
                            change.consume()
                        }
                    }
                    }
                } finally {
                    pendingTap?.cancel()
                }
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

private fun boundedOffset(offset: Offset, scale: Float, width: Int, height: Int): Offset {
    val maxX = max(0f, width * (scale - 1f) / 2f)
    val maxY = max(0f, height * (scale - 1f) / 2f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}
