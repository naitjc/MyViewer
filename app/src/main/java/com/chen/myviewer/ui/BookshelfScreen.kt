@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    filter: String, onFilter: (String) -> Unit, completion: Boolean?, onCompletion: (Boolean?) -> Unit, query: String, onQuery: (String) -> Unit,
    columns: Int, state: LazyGridState, onTop: () -> Unit,
    open: (Book) -> Unit, manage: (Book) -> Unit, import: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val term = query.trim()
    val shown = books.filter {
        (filter == "全部" || (filter == "小说" && it.book.kind == "TEXT") || (filter == "漫画" && it.book.kind == "COMIC")) &&
            (completion == null || it.book.completed == completion) &&
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
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
            OutlinedTextField(
                query,
                onQuery,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索书名" },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("搜索书名", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "清空书名搜索") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                )
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "小说", "漫画").forEach { label ->
                    val selected = filter == label
                    FilterChip(
                        selected = selected,
                        onClick = { onFilter(label) },
                        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        shape = RoundedCornerShape(50),
                        colors = shelfChipColors(),
                        border = shelfChipBorder(selected)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box {
                    FilledTonalIconButton(
                        onClick = { sortMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Outlined.Sort, "排序：${sort.label}", Modifier.size(20.dp))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) { LibrarySort.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label) }, trailingIcon = { if (sort == choice) Icon(Icons.Outlined.Check, null) },
                            onClick = { onSort(choice); sortMenu = false })
                    } }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部状态", false to "未读完", true to "已读完").forEach { (value, label) ->
                    val selected = completion == value
                    FilterChip(
                        selected = selected,
                        onClick = { onCompletion(value) },
                        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        shape = RoundedCornerShape(50),
                        colors = shelfChipColors(),
                        border = shelfChipBorder(selected),
                        modifier = Modifier.semantics { contentDescription = "阅读状态：$label" }
                    )
                }
            }
            Text(
                if (term.isEmpty()) "${shown.size} 本书" else "找到 ${shown.size} 本书",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                if (term.isNotEmpty()) EmptyState("没有找到匹配的书名", "试试调整书名、分类或阅读状态。", Icons.Outlined.SearchOff) {
                    OutlinedButton(onClick = { onQuery("") }) { Text("清空搜索") }
                } else if (completion != null) EmptyState("没有符合筛选条件的书籍", "可以调整分类或阅读状态。", Icons.Outlined.FilterAlt) {
                    OutlinedButton(onClick = { onCompletion(null) }) { Text("显示全部状态") }
                } else EmptyState("这个分类还没有书籍。", "可以切换分类，或导入新的书籍。", Icons.Outlined.AutoStories)
            } else {
                LazyVerticalGrid(state = state, columns = GridCells.Fixed(columns.coerceIn(1, 6)), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (columns >= 5) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    val recent = shown.filterNot { it.book.completed }.maxByOrNull { it.book.lastOpenedAt }?.takeIf { it.book.lastOpenedAt > 0 && term.isEmpty() }
                    if (recent != null) item(key = "continue-reading", span = { GridItemSpan(maxLineSpan) }) { ResumeBookCard(recent, { open(recent.book) }) }
                    items(shown, key = { it.book.id }) { row ->
                        Column(Modifier.combinedClickable(onClick = { open(row.book) }, onLongClick = { manage(row.book) })) {
                            Box(Modifier.fillMaxWidth()) {
                                BookCover(row.book, Modifier.fillMaxWidth())
                                if (row.book.completed) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(if (columns >= 5) 4.dp else 6.dp)
                                            .size(if (columns >= 5) 16.dp else 20.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Check, null, Modifier.size(if (columns >= 5) 10.dp else 13.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                } else if (row.book.lastOpenedAt > 0 && (row.position?.progress ?: 0f) > 0f) {
                                    val progress = (row.position?.progress ?: 0f).coerceIn(0f, 1f)
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = if (columns >= 5) 4.dp else 6.dp, vertical = if (columns >= 5) 4.dp else 6.dp)
                                            .height(if (columns >= 5) 2.5.dp else 3.5.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = 0.16f))
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(progress)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(if (columns >= 5) 6.dp else 8.dp))
                            Text(
                                row.book.title,
                                fontSize = if (columns >= 5) 11.sp else 13.sp,
                                lineHeight = if (columns >= 5) 14.sp else 18.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                progressLabel(row),
                                fontSize = if (columns >= 5) 9.sp else 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (row.book.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = atBottom, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    ExtendedFloatingActionButton(onClick = onTop, icon = { Icon(Icons.Outlined.ArrowUpward, null) }, text = { Text("回到顶部") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.semantics { contentDescription = "回到书库顶部" }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
internal fun shelfChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
internal fun shelfChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
    selectedBorderColor = Color.Transparent,
    borderWidth = 1.dp,
)

@Composable
internal fun ResumeBookCard(row: LibraryBook, open: () -> Unit, subtitle: String? = null) {
    Surface(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCover(row.book, Modifier.width(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (row.book.completed) "再次阅读" else "继续阅读", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                    Text(progressLabel(row), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                }
                LinearProgressIndicator(
                    progress = { (row.position?.progress ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f)
                )
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
