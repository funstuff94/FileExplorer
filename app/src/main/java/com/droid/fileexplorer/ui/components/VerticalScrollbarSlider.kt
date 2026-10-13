package com.droid.fileexplorer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.droid.fileexplorer.theme.Win11Blue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Common adapter interface for lazy scrollable containers (Grid & List).
 */
interface ScrollbarAdapter {
    val isScrollable: Boolean
    val canScrollUp: Boolean
    val canScrollDown: Boolean
    val progress: Float // 0f..1f
    val thumbRatio: Float // 0f..1f
    val viewportHeightPx: Float

    suspend fun scrollBy(deltaPixels: Float)
    suspend fun scrollToFraction(fraction: Float)
}

/**
 * Adapter for LazyListState (used in Details view).
 */
@Composable
fun rememberLazyListScrollbarAdapter(state: LazyListState): ScrollbarAdapter {
    return remember(state) {
        object : ScrollbarAdapter {
            override val isScrollable: Boolean
                get() = state.canScrollForward || state.canScrollBackward

            override val canScrollUp: Boolean
                get() = state.canScrollBackward

            override val canScrollDown: Boolean
                get() = state.canScrollForward

            override val viewportHeightPx: Float
                get() = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset).toFloat()

            override val thumbRatio: Float
                get() {
                    val layoutInfo = state.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    if (total == 0) return 1f
                    val visible = layoutInfo.visibleItemsInfo.size
                    if (visible >= total && state.firstVisibleItemIndex == 0) return 1f
                    val viewportHeight = viewportHeightPx
                    val avgItemSize = if (visible > 0) {
                        layoutInfo.visibleItemsInfo.sumOf { it.size }.toFloat() / visible
                    } else 40f
                    val estimatedTotalHeight = total * avgItemSize
                    return (viewportHeight / maxOf(viewportHeight, estimatedTotalHeight)).coerceIn(0.12f, 1f)
                }

            override val progress: Float
                get() {
                    if (!state.canScrollBackward) return 0f
                    if (!state.canScrollForward) return 1f
                    val layoutInfo = state.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    if (total <= 1) return 0f
                    val visible = layoutInfo.visibleItemsInfo.size
                    val maxFirstIndex = maxOf(1, total - visible)
                    val firstIndex = state.firstVisibleItemIndex
                    val firstOffset = state.firstVisibleItemScrollOffset
                    val avgItemSize = if (visible > 0) {
                        layoutInfo.visibleItemsInfo.sumOf { it.size }.toFloat() / visible
                    } else 40f
                    val currentPos = firstIndex.toFloat() + (firstOffset.toFloat() / maxOf(1f, avgItemSize))
                    return (currentPos / maxFirstIndex.toFloat()).coerceIn(0f, 1f)
                }

            override suspend fun scrollBy(deltaPixels: Float) {
                state.scrollBy(deltaPixels)
            }

            override suspend fun scrollToFraction(fraction: Float) {
                val total = state.layoutInfo.totalItemsCount
                if (total == 0) return
                if (fraction <= 0.001f) {
                    state.scrollToItem(0, 0)
                    return
                }
                if (fraction >= 0.999f) {
                    state.scrollToItem(maxOf(0, total - 1), 0)
                    return
                }
                val visible = state.layoutInfo.visibleItemsInfo.size
                val maxFirstIndex = maxOf(1, total - visible)
                val targetFloat = (fraction * maxFirstIndex).coerceIn(0f, maxFirstIndex.toFloat())
                val targetIndex = targetFloat.toInt().coerceIn(0, maxOf(0, total - 1))
                val remainder = targetFloat - targetIndex
                val avgItemSize = if (visible > 0) {
                    state.layoutInfo.visibleItemsInfo.sumOf { it.size }.toFloat() / visible
                } else 40f
                val offset = (remainder * avgItemSize).toInt()
                state.scrollToItem(targetIndex, offset)
            }
        }
    }
}

/**
 * Adapter for LazyGridState (used in Grid and Extra Large views).
 */
@Composable
fun rememberLazyGridScrollbarAdapter(state: LazyGridState): ScrollbarAdapter {
    return remember(state) {
        object : ScrollbarAdapter {
            override val isScrollable: Boolean
                get() = state.canScrollForward || state.canScrollBackward

            override val canScrollUp: Boolean
                get() = state.canScrollBackward

            override val canScrollDown: Boolean
                get() = state.canScrollForward

            override val viewportHeightPx: Float
                get() = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset).toFloat()

            private fun estimateColumns(): Int {
                val visible = state.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return 1
                val firstY = visible.first().offset.y
                val countInRow = visible.count { it.offset.y == firstY }
                return maxOf(1, countInRow)
            }

            override val thumbRatio: Float
                get() {
                    val layoutInfo = state.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    if (total == 0) return 1f
                    val cols = estimateColumns()
                    val totalRows = (total + cols - 1) / cols
                    val visible = layoutInfo.visibleItemsInfo
                    val visibleRows = if (visible.isNotEmpty()) {
                        visible.map { it.offset.y }.distinct().size
                    } else 1
                    if (visibleRows >= totalRows && state.firstVisibleItemIndex == 0) return 1f

                    val viewportHeight = viewportHeightPx
                    val avgRowSize = if (visible.isNotEmpty()) {
                        val rowHeights = visible.groupBy { it.offset.y }.map { (_, items) -> items.maxOf { it.size.height } }
                        if (rowHeights.isNotEmpty()) rowHeights.average().toFloat() else 140f
                    } else 140f
                    val estimatedTotalHeight = totalRows * avgRowSize
                    return (viewportHeight / maxOf(viewportHeight, estimatedTotalHeight)).coerceIn(0.12f, 1f)
                }

            override val progress: Float
                get() {
                    if (!state.canScrollBackward) return 0f
                    if (!state.canScrollForward) return 1f
                    val layoutInfo = state.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    if (total <= 1) return 0f
                    val cols = estimateColumns()
                    val totalRows = (total + cols - 1) / cols
                    val visible = layoutInfo.visibleItemsInfo
                    val visibleRows = if (visible.isNotEmpty()) {
                        visible.map { it.offset.y }.distinct().size
                    } else 1
                    val maxFirstRow = maxOf(1, totalRows - visibleRows)
                    val firstRow = state.firstVisibleItemIndex / cols
                    val firstOffset = state.firstVisibleItemScrollOffset
                    val avgRowSize = if (visible.isNotEmpty()) {
                        val rowHeights = visible.groupBy { it.offset.y }.map { (_, items) -> items.maxOf { it.size.height } }
                        if (rowHeights.isNotEmpty()) rowHeights.average().toFloat() else 140f
                    } else 140f
                    val currentPos = firstRow.toFloat() + (firstOffset.toFloat() / maxOf(1f, avgRowSize))
                    return (currentPos / maxFirstRow.toFloat()).coerceIn(0f, 1f)
                }

            override suspend fun scrollBy(deltaPixels: Float) {
                state.scrollBy(deltaPixels)
            }

            override suspend fun scrollToFraction(fraction: Float) {
                val total = state.layoutInfo.totalItemsCount
                if (total == 0) return
                if (fraction <= 0.001f) {
                    state.scrollToItem(0, 0)
                    return
                }
                if (fraction >= 0.999f) {
                    state.scrollToItem(maxOf(0, total - 1), 0)
                    return
                }
                val cols = estimateColumns()
                val totalRows = (total + cols - 1) / cols
                val visible = state.layoutInfo.visibleItemsInfo
                val visibleRows = if (visible.isNotEmpty()) {
                    visible.map { it.offset.y }.distinct().size
                } else 1
                val maxFirstRow = maxOf(1, totalRows - visibleRows)
                val targetRowFloat = (fraction * maxFirstRow).coerceIn(0f, maxFirstRow.toFloat())
                val targetRow = targetRowFloat.toInt().coerceIn(0, maxOf(0, totalRows - 1))
                val targetIndex = (targetRow * cols).coerceIn(0, maxOf(0, total - 1))
                val remainder = targetRowFloat - targetRow
                val avgRowSize = if (visible.isNotEmpty()) {
                    val rowHeights = visible.groupBy { it.offset.y }.map { (_, items) -> items.maxOf { it.size.height } }
                    if (rowHeights.isNotEmpty()) rowHeights.average().toFloat() else 140f
                } else 140f
                val offset = (remainder * avgRowSize).toInt()
                state.scrollToItem(targetIndex, offset)
            }
        }
    }
}

/**
 * Windows 11 style vertical scrollbar slider.
 * Features:
 * - Persistent visual presence when content is scrollable.
 * - Draggable slider thumb that smoothly scrolls the content.
 * - Clickable track: clicking below the thumb scrolls down by page; clicking above scrolls up.
 * - Dedicated Top and Bottom chevron buttons: clicking or holding scrolls up or down effortlessly.
 */
@Composable
fun VerticalScrollbarSlider(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier
) {
    if (!adapter.isScrollable) return

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val scrollbarInteractionSource = remember { MutableInteractionSource() }
    val isScrollbarHovered by scrollbarInteractionSource.collectIsHoveredAsState()
    var isDraggingThumb by remember { mutableStateOf(false) }

    val buttonHeight = 22.dp
    val scrollbarWidth = 16.dp
    val minThumbHeight = 36.dp

    BoxWithConstraints(
        modifier = modifier
            .width(scrollbarWidth)
            .fillMaxHeight()
            .background(
                if (isScrollbarHovered || isDraggingThumb) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                }
            )
            .hoverable(scrollbarInteractionSource)
    ) {
        val totalHeightPx = constraints.maxHeight.toFloat()
        val buttonHeightPx = with(density) { buttonHeight.toPx() }
        val trackHeightPx = maxOf(0f, totalHeightPx - (buttonHeightPx * 2))

        val thumbRatio = adapter.thumbRatio
        val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
        val thumbHeightPx = (trackHeightPx * thumbRatio).coerceIn(minThumbHeightPx, maxOf(minThumbHeightPx, trackHeightPx))
        val maxThumbOffsetPx = maxOf(0f, trackHeightPx - thumbHeightPx)

        // Current thumb position
        val progress = adapter.progress
        var dragAccumulatedOffset by remember { mutableFloatStateOf(0f) }

        val currentThumbOffsetPx = if (isDraggingThumb) {
            dragAccumulatedOffset.coerceIn(0f, maxThumbOffsetPx)
        } else {
            (progress * maxThumbOffsetPx).coerceIn(0f, maxThumbOffsetPx)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Chevron (Scroll Up) Button
            ScrollbarArrowButton(
                isUp = true,
                enabled = adapter.canScrollUp,
                height = buttonHeight,
                onClick = {
                    coroutineScope.launch {
                        val step = (adapter.viewportHeightPx * 0.35f).coerceAtLeast(180f)
                        adapter.scrollBy(-step)
                    }
                }
            )

            // 2. Track with Draggable Thumb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(trackHeightPx, thumbHeightPx, maxThumbOffsetPx) {
                        detectTapGestures { tapOffset ->
                            val tapY = tapOffset.y
                            val thumbTop = currentThumbOffsetPx
                            val thumbBottom = thumbTop + thumbHeightPx

                            coroutineScope.launch {
                                if (tapY < thumbTop) {
                                    // Clicked above the thumb -> scroll up by page
                                    val pageStep = (adapter.viewportHeightPx * 0.75f).coerceAtLeast(240f)
                                    adapter.scrollBy(-pageStep)
                                } else if (tapY > thumbBottom) {
                                    // Clicked below the thumb -> scroll down by page
                                    val pageStep = (adapter.viewportHeightPx * 0.75f).coerceAtLeast(240f)
                                    adapter.scrollBy(pageStep)
                                }
                            }
                        }
                    }
                    .pointerInput(maxThumbOffsetPx) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val thumbTop = currentThumbOffsetPx
                                val thumbBottom = thumbTop + thumbHeightPx
                                isDraggingThumb = true
                                dragAccumulatedOffset = if (offset.y in thumbTop..thumbBottom) {
                                    thumbTop
                                } else {
                                    (offset.y - thumbHeightPx / 2f).coerceIn(0f, maxThumbOffsetPx)
                                }
                                if (maxThumbOffsetPx > 0f) {
                                    val fraction = dragAccumulatedOffset / maxThumbOffsetPx
                                    coroutineScope.launch {
                                        adapter.scrollToFraction(fraction)
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulatedOffset = (dragAccumulatedOffset + dragAmount.y).coerceIn(0f, maxThumbOffsetPx)
                                if (maxThumbOffsetPx > 0f) {
                                    val fraction = dragAccumulatedOffset / maxThumbOffsetPx
                                    coroutineScope.launch {
                                        adapter.scrollToFraction(fraction)
                                    }
                                }
                            },
                            onDragEnd = {
                                isDraggingThumb = false
                            },
                            onDragCancel = {
                                isDraggingThumb = false
                            }
                        )
                    }
            ) {
                val thumbColor by animateColorAsState(
                    targetValue = when {
                        isDraggingThumb -> Win11Blue
                        isScrollbarHovered -> Win11Blue.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    animationSpec = tween(150),
                    label = "thumbColor"
                )

                val thumbWidth = if (isScrollbarHovered || isDraggingThumb) 12.dp else 10.dp

                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = currentThumbOffsetPx.roundToInt()) }
                        .align(Alignment.TopCenter)
                        .width(thumbWidth)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(RoundedCornerShape(6.dp))
                        .background(thumbColor)
                )
            }

            // 3. Bottom Chevron (Scroll Down) Button
            ScrollbarArrowButton(
                isUp = false,
                enabled = adapter.canScrollDown,
                height = buttonHeight,
                onClick = {
                    coroutineScope.launch {
                        val step = (adapter.viewportHeightPx * 0.35f).coerceAtLeast(180f)
                        adapter.scrollBy(step)
                    }
                }
            )
        }
    }
}

@Composable
private fun ScrollbarArrowButton(
    isUp: Boolean,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Hold-to-repeat scrolling
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            delay(350L)
            while (isPressed && enabled) {
                onClick()
                delay(120L)
            }
        }
    }

    val bg = when {
        isPressed -> Win11Blue.copy(alpha = 0.25f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
        isPressed -> Win11Blue
        isHovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(bg)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isUp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isUp) "Scroll Up" else "Scroll Down",
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
