@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.myviewer.data.*

@Composable
internal fun Bookshelf(
    books: List<LibraryBook>, sort: LibrarySort, onSort: (LibrarySort) -> Unit,
    filter: String, onFilter: (String) -> Unit, query: String, onQuery: (String) -> Unit,
    columns: Int, state: LazyGridState, onTop: () -> Unit,
    open: (Book) -> Unit, manage: (Book) -> Unit, import: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val term = query.trim()
    val shown = books.filter {
        (filter == "全部" || (filter == "小说" && it.book.kind == "TEXT") || (filter == "漫画" && it.book.kind == "COMIC")) &&
            (term.isEmpty() || it.book.title.contains(term, ignoreCase = true))
    }.let {
        val stable = compareBy<LibraryBook> { row -> row.book.title }.thenBy { row -> row.book.id }
        when (sort) {
            LibrarySort.TITLE -> it.sortedWith(stable)
            LibrarySort.IMPORTED -> it.sortedWith(compareByDescending<LibraryBook> { row -> row.book.importedAt }.then(stable))
            LibrarySort.RECENT -> it.sortedWith(compareByDescending<LibraryBook> { row -> row.book.lastOpenedAt }.thenByDescending { row -> row.book.importedAt }.then(stable))
        }
    }
    val atBottom by remember(state) { derivedStateOf { state.canScrollBackward && !state.canScrollForward } }
    if (books.isEmpty()) {
        EmptyState("把喜欢的故事放进书架", "从手机中选择 TXT 小说或 ZIP 漫画，阅读位置会自动保存。", Icons.Outlined.AutoStories) {
            Button(onClick = import) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("导入第一本书") }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            OutlinedTextField(query, onQuery,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索书名" },
                singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text("搜索书名") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "清空书名搜索") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "小说", "漫画").forEach { label ->
                    FilterChip(filter == label, { onFilter(label) }, label = { Text(label) })
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "排序：${sort.label}") }
                    DropdownMenu(sortMenu, { sortMenu = false }) { LibrarySort.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label) }, trailingIcon = { if (sort == choice) Icon(Icons.Outlined.Check, null) },
                            onClick = { onSort(choice); sortMenu = false })
                    } }
                }
            }
            Text(if (term.isEmpty()) "${shown.size} 本书" else "找到 ${shown.size} 本书", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                if (term.isNotEmpty()) EmptyState("没有找到匹配的书名", "试试书名中的几个字，或换一个分类。", Icons.Outlined.SearchOff) {
                    OutlinedButton(onClick = { onQuery("") }) { Text("清空搜索") }
                } else EmptyState("这个分类还没有书籍。", "可以切换分类，或导入新的书籍。", Icons.Outlined.AutoStories)
            } else {
                LazyVerticalGrid(state = state, columns = GridCells.Fixed(columns.coerceIn(1, 6)), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (columns >= 5) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    val recent = shown.maxByOrNull { it.book.lastOpenedAt }?.takeIf { it.book.lastOpenedAt > 0 && term.isEmpty() }
                    if (recent != null) item(key = "continue-reading", span = { GridItemSpan(maxLineSpan) }) { ResumeBookCard(recent, { open(recent.book) }) }
                    items(shown, key = { it.book.id }) { row ->
                        Column(Modifier.combinedClickable(onClick = { open(row.book) }, onLongClick = { manage(row.book) })) {
                            BookCover(row.book, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(row.book.title, fontSize = if (columns >= 5) 11.sp else 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(progressLabel(row), fontSize = if (columns >= 5) 9.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = atBottom, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    ExtendedFloatingActionButton(onClick = onTop, icon = { Icon(Icons.Outlined.ArrowUpward, null) }, text = { Text("回到顶部") },
                        modifier = Modifier.semantics { contentDescription = "回到书库顶部" }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
internal fun ResumeBookCard(row: LibraryBook, open: () -> Unit, subtitle: String? = null) {
    Surface(onClick = open, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoStories, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text("继续阅读", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(row.book, Modifier.width(64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(row.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${if (row.book.kind == "TEXT") "TXT 小说" else "ZIP 漫画"} · ${progressLabel(row)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                    LinearProgressIndicator(progress = { (row.position?.progress ?: 0f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .65f)) }
                }
            }
        }
    }
}
