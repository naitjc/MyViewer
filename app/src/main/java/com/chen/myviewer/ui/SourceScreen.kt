@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chen.myviewer.download.FolderDownloadViewModel
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.data.ChapterPosition
import com.chen.myviewer.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SourceScreen(vm: SourceViewModel, reader: MainViewModel, catalogHeader: @Composable () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads: FolderDownloadViewModel = viewModel()
    var repositoryDialog by remember { mutableStateOf(false) }
    var settingsSource by remember { mutableStateOf<String?>(null) }
    var repositoryUrl by remember { mutableStateOf(state.repositoryUrl) }
    settingsSource?.let { sourceId ->
        SourceSettingsScreen(vm.repository, sourceId) { settingsSource = null }
        return
    }
    BackHandler(state.detail != null || state.source != null) { vm.back() }
    Column(Modifier.fillMaxSize()) {
        if (state.detail != null || state.source != null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回漫画源") }
            Text(state.detail?.manga?.title ?: state.source!!.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            if (state.detail != null) IconButton(onClick = vm::refreshDetail, enabled = state.busy == null) { Icon(Icons.Outlined.Refresh, "刷新章节") }
            IconButton(onClick = { settingsSource = state.detail?.sourceId ?: state.source?.id }) { Icon(Icons.Outlined.Settings, "漫画源设置") }
        }
        FolderDownloadStatus(downloads)
        state.busy?.let { message ->
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
        }
        state.downloadStatus?.let { message -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::cancelDownload) { Text("取消下载") }
        } }
        state.error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = vm::dismissError) { Text("关闭提示") }
                }
            }
        }
        when {
            state.detail != null -> MangaDetails(vm, reader, state, downloads)
            state.source != null -> SourceSearch(vm, state)
            else -> SourceCatalog(vm, state, { repositoryUrl = state.repositoryUrl; repositoryDialog = true }, catalogHeader)
        }
    }
    if (repositoryDialog) AlertDialog(onDismissRequest = { repositoryDialog = false }, title = { Text("漫画源仓库") }, text = {
        OutlinedTextField(repositoryUrl, { repositoryUrl = it }, label = { Text("HTTPS 索引地址") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
    }, confirmButton = { TextButton(onClick = { repositoryDialog = false; vm.loadCatalog(true, repositoryUrl) }) { Text("加载仓库") } }, dismissButton = { TextButton(onClick = { repositoryDialog = false }) { Text("取消") } })
}

@Composable
private fun SourceCatalog(vm: SourceViewModel, state: SourceBrowserState, repository: () -> Unit, catalogHeader: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var languageMenu by remember { mutableStateOf(false) }
    var removingSource by remember { mutableStateOf<SourceEntry?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val installed = state.installed.associateBy { it.id }
    val available = (state.installed.map { it.entry } + state.catalog).groupBy { it.id }
        .map { (_, versions) -> versions.maxBy { it.version } }
    val languages = (available.flatMap { it.languages }.map(::sourceLanguageGroup) + state.language)
        .filter { it.isNotBlank() }.distinct().sortedBy(::sourceLanguageLabel)
    val commonLanguages = listOf("zh", "en", "ja", "ko")
    val otherLanguages = languages.filterNot { it in commonLanguages }
    LaunchedEffect(state.busy) { if (state.catalog.isEmpty() && state.busy == null && state.error == null) vm.loadCatalog() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "发现",
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = "发现"; heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            IconButton(onClick = { vm.loadCatalog(true) }, enabled = state.busy == null) { Icon(Icons.Outlined.Refresh, "刷新源仓库") }
            IconButton(onClick = repository) { Icon(Icons.Outlined.Settings, "管理源仓库") }
        }
        catalogHeader()
        CompactSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索漫画源",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onSearch = { focus.clearFocus(); keyboard?.hide() }
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allSelected = state.language.isEmpty()
            FilterChip(
                selected = allSelected,
                onClick = { vm.language("") },
                label = { Text("全部语言", fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Normal) },
                shape = RoundedCornerShape(50),
                colors = shelfChipColors(),
                border = shelfChipBorder(allSelected)
            )
            commonLanguages.filter { it == "zh" || it in languages }.forEach { language ->
                val selected = state.language == language
                FilterChip(
                    selected = selected,
                    onClick = { vm.language(language) },
                    label = { Text(sourceLanguageLabel(language), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    shape = RoundedCornerShape(50),
                    colors = shelfChipColors(),
                    border = shelfChipBorder(selected)
                )
            }
            if (otherLanguages.isNotEmpty()) Box {
                val selected = state.language in otherLanguages
                FilterChip(
                    selected = selected,
                    onClick = { languageMenu = true },
                    shape = RoundedCornerShape(50),
                    colors = shelfChipColors(),
                    border = shelfChipBorder(selected),
                    label = { Text(if (selected) sourceLanguageLabel(state.language) else "更多语言", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) }
                )
                DropdownMenu(languageMenu, { languageMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    otherLanguages.forEach { language ->
                        DropdownMenuItem(text = { Text(sourceLanguageLabel(language)) },
                            trailingIcon = { if (state.language == language) Icon(Icons.Outlined.Check, null) },
                            onClick = { vm.language(language); languageMenu = false })
                    }
                }
            }
        }
        val entries = available.filter { entry ->
            (state.language.isEmpty() || entry.languages.any { sourceLanguageGroup(it) == state.language }) &&
                (query.isBlank() || "${entry.name} ${entry.id}".contains(query, true))
        }.sortedWith(compareByDescending<SourceEntry> { it.id in installed }.thenBy { it.name })
        val installedEntries = entries.filter { it.id in installed }
        val catalogEntries = entries.filterNot { it.id in installed }
        if (entries.isEmpty() && state.busy == null) {
            if (available.isEmpty()) EmptyState("还没有可用的漫画源", "加载仓库后选择漫画源进行安装。", Icons.Outlined.Public) {
                Button(onClick = { vm.loadCatalog(true) }) { Text("重新加载") }
            } else EmptyState("没有匹配的漫画源", "可以选择其他语言或调整源名称。", Icons.Outlined.SearchOff) {
                TextButton(onClick = { query = ""; vm.language("") }) { Text("清除筛选") }
            }
        } else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (installedEntries.isNotEmpty()) {
                item(key = "header-installed") { SourceSectionHeader("已安装 · ${installedEntries.size}") }
                items(installedEntries, key = { it.id }) { entry ->
                    SourceEntryCard(
                        entry = entry,
                        local = installed[entry.id],
                        busy = state.busy != null,
                        onChoose = { vm.choose(it) },
                        onInstall = { vm.install(it) },
                        onRemove = { removingSource = it }
                    )
                }
            }
            if (catalogEntries.isNotEmpty()) {
                if (installedEntries.isNotEmpty()) {
                    item(key = "header-catalog") { SourceSectionHeader("发现更多 · ${catalogEntries.size}", Modifier.padding(top = 6.dp)) }
                }
                items(catalogEntries, key = { it.id }) { entry ->
                    SourceEntryCard(
                        entry = entry,
                        local = null,
                        busy = state.busy != null,
                        onChoose = { vm.choose(it) },
                        onInstall = { vm.install(it) },
                        onRemove = { removingSource = it }
                    )
                }
            }
        }
    }
    removingSource?.let { entry ->
        AlertDialog(onDismissRequest = { removingSource = null }, title = { Text("卸载漫画源？") }, text = { Text(entry.name) },
            confirmButton = { TextButton(onClick = { removingSource = null; vm.uninstall(entry) }) { Text("卸载") } },
            dismissButton = { TextButton(onClick = { removingSource = null }) { Text("取消") } })
    }
}

@Composable
private fun SourceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun SourceEntryCard(
    entry: SourceEntry,
    local: InstalledSource?,
    busy: Boolean,
    onChoose: (SourceEntry) -> Unit,
    onInstall: (SourceEntry) -> Unit,
    onRemove: (SourceEntry) -> Unit,
) {
    val monogram = entry.name.trim().take(1).uppercase()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (local != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (local != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        shadowElevation = if (local != null) 0.dp else 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = local != null && !busy) { onChoose(local!!.entry) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (local != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (monogram.isNotBlank()) Text(
                        monogram,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (local != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) else Icon(
                        Icons.Outlined.Public,
                        null,
                        Modifier.size(22.dp),
                        tint = if (local != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.languages.joinToString(" / ")} · v${entry.version}${if (local != null) " · 已安装" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (local != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (local == null) FilledTonalButton(
                onClick = { onInstall(entry) },
                enabled = !busy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) { Text("安装") }
            else if (local.entry.version < entry.version) TextButton(onClick = { onInstall(entry) }, enabled = !busy) { Text("更新") }
            else TextButton(onClick = { onChoose(local.entry) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "浏览 ${entry.name}" }) { Text("浏览", fontWeight = FontWeight.SemiBold) }
            if (local != null) IconButton(onClick = { onRemove(local.entry) }, enabled = !busy) {
                Icon(Icons.Outlined.DeleteOutline, "卸载 ${entry.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceSearch(vm: SourceViewModel, state: SourceBrowserState) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        CompactSearchField(
            value = state.query,
            onValueChange = vm::query,
            placeholder = "搜索 ${state.source!!.name} 的漫画",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            onSearch = { focus.clearFocus(); keyboard?.hide(); vm.search() },
            trailingAction = {
                IconButton(
                    onClick = { focus.clearFocus(); keyboard?.hide(); vm.search() },
                    enabled = state.busy == null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Search, "搜索漫画", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        )
        if (state.results.isEmpty() && state.busy == null) {
            EmptyState("暂无搜索结果", "输入漫画名称搜索，或重试当前请求。", Icons.Outlined.Search) {
                Button(onClick = { vm.search() }) { Text("重试") }
            }
        } else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.results, key = { it.key }) { manga ->
                Surface(
                    onClick = { vm.show(manga) },
                    enabled = state.busy == null,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        OnlineCover(vm.repository, manga.cover, Modifier.width(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(manga.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (manga.authors.isNotEmpty()) Text(manga.authors.joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            manga.description?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            if (state.hasNextPage) item { TextButton(onClick = { vm.search(true) }, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
        }
    }
}

@Composable
private fun MangaDetails(vm: SourceViewModel, reader: MainViewModel, state: SourceBrowserState, downloads: FolderDownloadViewModel) {
    val title = state.detail!!
    val library by reader.library.collectAsStateWithLifecycle()
    val book = library.firstOrNull { it.book.id == title.book.id }?.book ?: title.book
    val positions by remember(title.book.id) { reader.repository.dao.chapterPositions(title.book.id) }.collectAsStateWithLifecycle(emptyList())
    val last by produceState<com.chen.myviewer.data.ReadingPosition?>(null, title.book.id) { value = reader.repository.dao.position(title.book.id) }
    val exportState by downloads.state.collectAsStateWithLifecycle()
    val chooseFolder = rememberFolderDownloadPicker(title.book.id, downloads) { chapter, folder -> downloads.comic(title, chapter, folder) }
    val chapters = title.manga.chapters.orEmpty()
    var ascending by rememberSaveable(title.book.id) { mutableStateOf(false) }
    val displayedChapters = if (ascending) chapters.asReversed() else chapters
    val selected = chapters.firstOrNull { it.key == last?.chapterKey } ?: chapters.lastOrNull()
    val progress = positions.associateBy(ChapterPosition::chapterKey)
    var deleteChapter by remember { mutableStateOf<SourceChapter?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnlineCover(vm.repository, title.manga.cover, Modifier.width(105.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title.manga.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (title.manga.authors.isNotEmpty()) Text(title.manga.authors.joinToString(" / "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text(title.sourceId, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        if (book.completed) Text("已读完", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = vm::addToShelf,
                    enabled = !book.onShelf && state.busy == null,
                    shape = RoundedCornerShape(50),
                    modifier = if (selected == null) Modifier.fillMaxWidth() else Modifier
                ) {
                    Icon(if (book.onShelf) Icons.Outlined.Check else Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (book.onShelf) "已加入书架" else "加入书架")
                }
                if (selected != null) {
                    Button(
                        onClick = { reader.open(title.book, title, selected) },
                        enabled = state.busy == null,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (last?.chapterKey.isNullOrBlank()) "开始阅读" else "继续阅读")
                    }
                }
            }
        }
        title.manga.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                var expanded by rememberSaveable(title.book.id) { mutableStateOf(false) }
                val long = description.length > 90
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            description,
                            maxLines = if (!long || expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (long) {
                            Text(
                                if (expanded) "收起" else "展开简介",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { chooseFolder(null) }, enabled = !exportState.running && chapters.any { !it.locked }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("下载整本到文件夹")
                    }
                    Text("整本合并为一个 ZIP，含 ${chapters.count { !it.locked }} 个可读章节。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("章节 · ${chapters.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (chapters.size > 1) {
                    TextButton(
                        onClick = { ascending = !ascending },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.SwapVert, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (ascending) "正序" else "倒序", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (chapters.isEmpty()) item { TextButton(onClick = vm::refreshDetail, enabled = state.busy == null) { Text("刷新章节列表") } }
        items(displayedChapters, key = { it.key }) { chapter ->
            val saved = progress[chapter.key]
            val completed = saved?.completed == true
            val downloaded = chapter.key in state.downloaded
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().animateItem()
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !chapter.locked && state.busy == null) { reader.open(title.book, title, chapter) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(chapter.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        val status = listOfNotNull(if (chapter.locked) "已锁定" else null, if (downloaded) "已下载" else null,
                            when {
                                completed -> "已读完"
                                saved?.completed == false -> "未读完"
                                saved != null -> "已读 ${(saved.progress * 100).toInt()}%"
                                else -> null
                            }).joinToString(" · ")
                        if (status.isNotBlank()) Text(status, color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { chooseFolder(chapter.key) }, enabled = !chapter.locked && !exportState.running) { Icon(Icons.Outlined.FolderOpen, "保存 ${chapter.label} 到文件夹") }
                    if (downloaded) IconButton(onClick = { deleteChapter = chapter }, enabled = state.downloadStatus == null) { Icon(Icons.Outlined.DeleteOutline, "删除 ${chapter.label} 的下载") }
                    else IconButton(onClick = { vm.download(chapter) }, enabled = !chapter.locked && state.downloadStatus == null) { Icon(Icons.Outlined.Download, "下载 ${chapter.label}") }
                }
            }
        }
    }
    deleteChapter?.let { chapter -> AlertDialog(onDismissRequest = { deleteChapter = null }, title = { Text("删除章节下载") }, text = { Text(chapter.label) },
        confirmButton = { TextButton(onClick = { vm.deleteDownload(chapter); deleteChapter = null }) { Text("删除下载") } }, dismissButton = { TextButton(onClick = { deleteChapter = null }) { Text("取消") } }) }
}

@Composable
private fun OnlineCover(repository: SourceRepository, url: String?, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = if (url == null) null else try { repository.cover(url) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { null }
    }
    Surface(
        modifier.aspectRatio(.72f),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
