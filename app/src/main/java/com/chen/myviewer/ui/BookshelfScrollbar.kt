@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.chen.myviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val HEADER_ROWS = 2

/** Grid rows, rather than individual books, account for adaptive columns and full-width headers. */
private data class ShelfScrollMetrics(val progress: Float, val visibleRows: Float, val totalRows: Int, val columns: Int, val rowHeight: Float, val visibleHeights: Map<Int, Int>) {
    val visibleRatio: Float get() = (visibleRows / totalRows).coerceIn(.01f, 1f)
}

private fun metrics(state: LazyGridState, books: Int): ShelfScrollMetrics? {
    if (books == 0 || (!state.canScrollBackward && !state.canScrollForward)) return null
    val info = state.layoutInfo
    val columns = info.maxSpan.coerceAtLeast(1)
    val totalRows = HEADER_ROWS + ceil(books.toFloat() / columns).toInt()
    val rows = info.visibleItemsInfo.filter { it.row >= 0 && it.size.height > 0 }.groupBy { it.row }
    if (rows.isEmpty()) return null
    val heights = rows.mapValues { (_, items) -> items.maxOf { it.size.height } + info.mainAxisItemSpacing }
    val visible = rows.entries.sumOf { (row, items) ->
        val top = items.minOf { it.offset.y }
        ((minOf(top + heights.getValue(row), info.viewportEndOffset) - maxOf(top, info.viewportStartOffset)).coerceAtLeast(0).toDouble() / heights.getValue(row))
    }.toFloat()
    val first = rows.minBy { it.key }
    val units = first.key + ((info.viewportStartOffset - first.value.minOf { it.offset.y }).toFloat() / heights.getValue(first.key)).coerceIn(0f, 1f)
    val progress = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> (units / (totalRows - visible).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    val bookHeights = heights.filterKeys { it >= HEADER_ROWS }.values.ifEmpty { heights.values }
    return ShelfScrollMetrics(progress, visible, totalRows, columns, bookHeights.average().toFloat().coerceAtLeast(1f), heights)
}

private fun seek(state: LazyGridState, metrics: ShelfScrollMetrics, value: Float) {
    val total = state.layoutInfo.totalItemsCount
    if (total == 0) return
    val fraction = value.coerceIn(0f, 1f)
    if (fraction <= 0f) { state.requestScrollToItem(0); return }
    if (fraction >= 1f) { state.requestScrollToItem(total - 1); return }
    val units = fraction * (metrics.totalRows - metrics.visibleRows).coerceAtLeast(0f)
    val row = floor(units).toInt()
    val index = if (row < HEADER_ROWS) row else HEADER_ROWS + (row - HEADER_ROWS) * metrics.columns
    val height = metrics.visibleHeights[row]?.toFloat() ?: metrics.rowHeight
    state.requestScrollToItem(index.coerceIn(0, total - 1), ((units - row) * height).roundToInt())
}

@Composable
internal fun BookshelfScrollbar(state: LazyGridState, books: Int, modifier: Modifier = Modifier) {
    val position by remember(state, books) { derivedStateOf { metrics(state, books) } }
    val current = position ?: return
    val latest by rememberUpdatedState(current)
    var dragged by remember(state, books) { mutableStateOf<Float?>(null) }
    var draggedRatio by remember(state, books) { mutableFloatStateOf(current.visibleRatio) }
    val progress = dragged ?: current.progress
    val visibleRatio = if (dragged == null) current.visibleRatio else draggedRatio
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f)
    val thumb = MaterialTheme.colorScheme.primary.copy(alpha = if (dragged == null) .7f else 1f)
    Canvas(modifier.width(32.dp).fillMaxHeight().padding(vertical = 8.dp).semantics {
        contentDescription = "书库快速滚动"
        stateDescription = "书架位置 ${(progress * 100).roundToInt()}%"
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        setProgress { seek(state, latest, it); true }
    }.pointerInput(state, books) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val snapshot = latest
            draggedRatio = snapshot.visibleRatio
            val height = size.height.toFloat()
            val thumbHeight = (height * snapshot.visibleRatio).coerceIn(minOf(48.dp.toPx(), height), height)
            val travel = (height - thumbHeight).coerceAtLeast(1f)
            val top = snapshot.progress * travel
            val grab = if (down.position.y in top..top + thumbHeight) down.position.y - top else thumbHeight / 2
            fun move(y: Float) { val fraction = ((y - grab) / travel).coerceIn(0f, 1f); dragged = fraction; seek(state, snapshot, fraction) }
            down.consume(); move(down.position.y)
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    move(change.position.y)
                    event.changes.forEach { it.consume() }
                } while (change.pressed)
            } finally { dragged = null }
        }
    }) {
        val height = (size.height * visibleRatio).coerceIn(minOf(48.dp.toPx(), size.height), size.height)
        val width = if (dragged == null) 4.dp.toPx() else 8.dp.toPx()
        drawRoundRect(track, Offset((size.width - 2.dp.toPx()) / 2, 0f), Size(2.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
        drawRoundRect(thumb, Offset((size.width - width) / 2, progress * (size.height - height)), Size(width, height), CornerRadius(width / 2))
    }
}
