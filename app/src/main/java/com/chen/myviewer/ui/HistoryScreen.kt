package com.chen.myviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chen.myviewer.data.Book
import com.chen.myviewer.data.LibraryBook
import com.chen.myviewer.data.ReadingTotals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun History(books: List<LibraryBook>, open: (Book) -> Unit, clear: () -> Unit, library: () -> Unit,
    totals: ReadingTotals = ReadingTotals(), bookTimes: Map<String, Long> = emptyMap(), remove: (Book) -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    val ordered = books.sortedByDescending { it.book.lastOpenedAt }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val groups = ordered.drop(1).groupBy { Instant.ofEpochMilli(it.book.lastOpenedAt).atZone(zone).toLocalDate() }
    fun dateLabel(day: LocalDate) = when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "MM月dd日" else "yyyy年MM月dd日"))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "reading-time") { ReadingTimeSummary(totals) }
        if (ordered.isEmpty()) item(key = "empty") {
            Box(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                EmptyState("还没有阅读记录", "从书库打开一本书，下次就能从这里接着读。", Icons.Outlined.History) {
                    FilledTonalButton(onClick = library) { Icon(Icons.Outlined.AutoStories, null); Spacer(Modifier.width(8.dp)); Text("去书库看看") }
                }
            }
        } else {
        item {
            val latest = ordered.first()
            val stamp = Instant.ofEpochMilli(latest.book.lastOpenedAt).atZone(zone)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResumeBookCard(latest, { open(latest.book) }, "上次阅读 · ${dateLabel(stamp.toLocalDate())} ${stamp.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("累计阅读 ${formatReadingTime(bookTimes[latest.book.id] ?: 0L)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { remove(latest.book) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, "移出历史：${latest.book.title}", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("阅读记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${books.size} 本书，随时从上次的位置继续", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { confirm = true }) { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("清空历史") }
            }
        }
        if (groups.isEmpty()) item { Text("下一段故事，也会留在这里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp)) }
        groups.forEach { (day, rows) ->
            item(key = "date-$day") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text(dateLabel(day), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text("${rows.size} 本", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(rows, key = { it.book.id }) { row ->
                val progress = (row.position?.progress ?: 0f).coerceIn(0f, 1f)
                Surface(
                    onClick = { open(row.book) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BookCover(row.book, Modifier.width(52.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${if (row.book.kind == "TEXT") "小说" else "漫画"} · ${progressLabel(row)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (progress > 0f && !row.book.completed) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Instant.ofEpochMilli(row.book.lastOpenedAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("· 累计 ${formatReadingTime(bookTimes[row.book.id] ?: 0L)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { remove(row.book) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, "移出历史：${row.book.title}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清空最近阅读？") }, text = { Text("阅读进度、书签和阅读时长会保留。") },
        confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("清空") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable
private fun ReadingTimeSummary(totals: ReadingTotals) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.padding(6.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text("阅读时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("今日阅读", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatReadingTime(totals.todayMs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("累计阅读", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatReadingTime(totals.totalMs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text("前台亮屏时计时 · 从本次更新起记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun formatReadingTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> if (minutes % 60 == 0L) "$hours 小时" else "$hours 小时 ${minutes % 60} 分钟"
        minutes > 0 -> "$minutes 分钟"
        else -> "$seconds 秒"
    }
}
