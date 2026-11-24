@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
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
    batchMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelect: (String) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onExitBatch: () -> Unit = {},
    onBatchComplete: (Boolean) -> Unit = {},
    onBatchRemove: () -> Unit = {},
) {
    var sortMenu by remember { mutableStateOf(false) }
    var confirmBatchRemove by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val term = query.trim()
    val shown = books.filter {
        (filter == "全部" || (filter == "小说" && it.book.kind == "TEXT") || (filter == "漫画" && it.book.kind == "COMIC")) &&
            (completion == null || it.book.completed == completion) &&
            (term.isEmpty() || it.book.title.contains(term, ignoreCase = true))
    }.let {
        val pinnedFirst = compareByDescending<LibraryBook> { row -> row.book.pinned }
        val stable = compareBy<LibraryBook> { row -> row.book.title }.thenBy { row -> row.book.id }
        when (sort) {
            LibrarySort.TITLE -> it.sortedWith(pinnedFirst.then(stable))
            LibrarySort.IMPORTED -> it.sortedWith(pinnedFirst.thenByDescending { row -> row.book.importedAt }.then(stable))
            LibrarySort.RECENT -> it.sortedWith(pinnedFirst.thenByDescending { row -> row.book.lastOpenedAt }.thenByDescending { row -> row.book.importedAt }.then(stable))
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
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)) {
            if (batchMode) {
                val shownIds = shown.map { it.book.id }
                val allSelected = shownIds.isNotEmpty() && shownIds.all { it in selectedIds }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("已选 ${selectedIds.size} 本", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TextButton(onClick = { onSelectAll(if (allSelected) emptyList() else shownIds) }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        TextButton(onClick = onExitBatch) { Text("完成") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactSearchField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = "搜索书名",
                    modifier = Modifier.weight(1f),
                    clearDescription = "清空书名搜索",
                    onSearch = { focus.clearFocus(); keyboard?.hide() }
                )
                Box {
                    FilledTonalIconButton(
                        onClick = { sortMenu = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, "排序：${sort.label}", Modifier.size(20.dp))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) { LibrarySort.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label) }, trailingIcon = { if (sort == choice) Icon(Icons.Outlined.Check, null) },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSort(choice)
                                sortMenu = false
                            })
                    } }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("全部", "小说", "漫画").forEach { label ->
                            val selected = filter == label
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .selectable(selected = selected, role = Role.Tab) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onFilter(label)
                                    }
                            ) {
                                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)))
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(null to "全部状态", false to "未读完", true to "已读完").forEach { (value, label) ->
                        val selected = completion == value
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(50))
                                .selectable(selected = selected, role = Role.Tab) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCompletion(value)
                                }
                                .semantics { contentDescription = "阅读状态：$label" }
                        ) {
                            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (columns >= 5) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item(key = "shelf-count", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (term.isEmpty()) "${shown.size} 本书" else "找到 ${shown.size} 本书",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "按${sort.label}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    val recent = shown.filterNot { it.book.completed }.maxByOrNull { it.book.lastOpenedAt }?.takeIf { it.book.lastOpenedAt > 0 && term.isEmpty() && !batchMode }
                    if (recent != null) item(key = "continue-reading", span = { GridItemSpan(maxLineSpan) }) { ResumeBookCard(recent, { open(recent.book) }) }
                    items(shown, key = { it.book.id }) { row ->
                        val isSelected = row.book.id in selectedIds
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "cover-press")
                        Column(
                            Modifier
                                .animateItem()
                                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current,
                                    onClick = { if (batchMode) onToggleSelect(row.book.id) else open(row.book) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (batchMode) onToggleSelect(row.book.id) else manage(row.book)
                                    }
                                )
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                BookCover(row.book, Modifier.fillMaxWidth())
                                if (batchMode) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.08f))
                                    )
                                    Icon(
                                        if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = if (isSelected) "已选中 ${row.book.title}" else "未选中 ${row.book.title}",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(if (columns >= 5) 4.dp else 6.dp)
                                            .size(if (columns >= 5) 18.dp else 22.dp)
                                    )
                                } else {
                                    if (row.book.pinned) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopStart)
                                                .padding(if (columns >= 5) 4.dp else 6.dp)
                                                .size(if (columns >= 5) 16.dp else 20.dp)
                                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Outlined.PushPin, "已置顶", Modifier.size(if (columns >= 5) 10.dp else 12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                    if (row.book.unreadChapters > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(if (columns >= 5) 4.dp else 6.dp)
                                        ) {
                                            Text(
                                                "+${row.book.unreadChapters}",
                                                color = MaterialTheme.colorScheme.onTertiary,
                                                fontSize = if (columns >= 5) 9.sp else 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = if (columns >= 5) 5.dp else 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else if (row.book.completed) {
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
                                    }
                                }
                                if (!batchMode && !row.book.completed && row.book.lastOpenedAt > 0 && (row.position?.progress ?: 0f) > 0f) {
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
                                style = if (columns >= 5) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                progressLabel(row),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (row.book.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = atBottom && !batchMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    ExtendedFloatingActionButton(onClick = onTop, icon = { Icon(Icons.Outlined.ArrowUpward, null) }, text = { Text("回到顶部") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.semantics { contentDescription = "回到书库顶部" }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (batchMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onBatchComplete(true) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("标为已读完", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(
                        onClick = { onBatchComplete(false) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("标为未读完", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    Button(
                        onClick = { confirmBatchRemove = true },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("移出书架", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
    if (confirmBatchRemove) {
        AlertDialog(
            onDismissRequest = { confirmBatchRemove = false },
            title = { Text("批量移出书架？") },
            text = { Text("将所选的 ${selectedIds.size} 本书移出书架，文件、进度、书签和历史会保留。") },
            confirmButton = {
                TextButton(onClick = { confirmBatchRemove = false; onBatchRemove() }) { Text("移出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchRemove = false }) { Text("取消") }
            }
        )
    }
}

@Composable
internal fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldDescription: String = placeholder,
    clearDescription: String = "清空搜索",
    onSearch: () -> Unit = {},
    trailingAction: (@Composable RowScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)
        )
    ) {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Search,
                null,
                Modifier.size(18.dp),
                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = fieldDescription }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, clearDescription, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailingAction?.invoke(this)
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BookCover(row.book, Modifier.width(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(
                            if (row.book.completed) "再次阅读" else "继续阅读",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(progressLabel(row), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f))
                }
                Text(row.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                LinearProgressIndicator(
                    progress = { (row.position?.progress ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f)
                )
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)) }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 2.dp) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
