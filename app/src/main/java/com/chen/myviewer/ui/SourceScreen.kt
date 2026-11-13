@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val installed = state.installed.associateBy { it.id }
    val available = (state.installed.map { it.entry } + state.catalog).groupBy { it.id }
        .map { (_, versions) -> versions.maxBy { it.version } }
    val languages = (available.flatMap { it.languages }.map(::sourceLanguageGroup) + state.language)
        .filter { it.isNotBlank() }.distinct().sortedBy(::sourceLanguageLabel)
    val commonLanguages = listOf("zh", "en", "ja", "ko")
    val otherLanguages = languages.filterNot { it in commonLanguages }
    LaunchedEffect(state.busy) { if (state.catalog.isEmpty() && state.busy == null && state.error == null) vm.loadCatalog() }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("发现", Modifier.weight(1f).clearAndSetSemantics { contentDescription = "发现"; heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            IconButton(onClick = { vm.loadCatalog(true) }, enabled = state.busy == null) { Icon(Icons.Outlined.Refresh, "刷新源仓库") }
            IconButton(onClick = repository) { Icon(Icons.Outlined.Settings, "管理源仓库") }
        }
        catalogHeader()
        OutlinedTextField(query, { query = it }, placeholder = { Text("搜索漫画源") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)), singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.language.isEmpty(), { vm.language("") }, label = { Text("全部语言") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), colors = shelfChipColors())
            commonLanguages.filter { it == "zh" || it in languages }.forEach { language ->
                FilterChip(state.language == language, { vm.language(language) }, label = { Text(sourceLanguageLabel(language)) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), colors = shelfChipColors())
            }
            if (otherLanguages.isNotEmpty()) Box {
                val selected = state.language in otherLanguages
                FilterChip(selected, { languageMenu = true }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), colors = shelfChipColors(),
                    label = { Text(if (selected) sourceLanguageLabel(state.language) else "更多语言") },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) })
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
        if (entries.isEmpty() && state.busy == null) {
            if (available.isEmpty()) EmptyState("还没有可用的漫画源", "加载仓库后选择漫画源进行安装。", Icons.Outlined.Public) {
                Button(onClick = { vm.loadCatalog(true) }) { Text("重新加载") }
            } else EmptyState("没有匹配的漫画源", "可以选择其他语言或调整源名称。", Icons.Outlined.SearchOff) {
                TextButton(onClick = { query = ""; vm.language("") }) { Text("清除筛选") }
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { it.id }) { entry ->
                val local = installed[entry.id]
                Row(Modifier.fillMaxWidth().clickable(enabled = local != null && state.busy == null) { vm.choose(local!!.entry) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Outlined.Public, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.languages.joinToString(" / ")} · v${entry.version}${if (local != null) " · 已安装" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (local == null) FilledTonalButton(onClick = { vm.install(entry) }, enabled = state.busy == null) { Text("安装") }
                    else if (local.entry.version < entry.version) TextButton(onClick = { vm.install(entry) }, enabled = state.busy == null) { Text("更新") }
                    else TextButton(onClick = { vm.choose(local.entry) }, enabled = state.busy == null, modifier = Modifier.semantics { contentDescription = "浏览 ${entry.name}" }) { Text("浏览") }
                    if (local != null) IconButton(onClick = { removingSource = local.entry }, enabled = state.busy == null) {
                        Icon(Icons.Outlined.DeleteOutline, "卸载 ${entry.name}")
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
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
private fun SourceSearch(vm: SourceViewModel, state: SourceBrowserState) {
    Column {
        OutlinedTextField(state.query, vm::query, label = { Text("搜索 ${state.source!!.name} 的漫画") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }), trailingIcon = {
                IconButton(onClick = { vm.search() }, enabled = state.busy == null) { Icon(Icons.Outlined.Search, "搜索漫画") }
            })
        if (state.results.isEmpty() && state.busy == null) {
            EmptyState("暂无搜索结果", "输入漫画名称搜索，或重试当前请求。", Icons.Outlined.Search) {
                Button(onClick = { vm.search() }) { Text("重试") }
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.results, key = { it.key }) { manga ->
                Row(Modifier.fillMaxWidth().clickable(enabled = state.busy == null) { vm.show(manga) }.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OnlineCover(vm.repository, manga.cover, Modifier.width(64.dp))
                    Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(manga.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (manga.authors.isNotEmpty()) Text(manga.authors.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                        manga.description?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    val selected = chapters.firstOrNull { it.key == last?.chapterKey } ?: chapters.lastOrNull()
    val progress = positions.associateBy(ChapterPosition::chapterKey)
    var deleteChapter by remember { mutableStateOf<SourceChapter?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OnlineCover(vm.repository, title.manga.cover, Modifier.width(105.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title.manga.title, style = MaterialTheme.typography.titleLarge)
                    Text(title.manga.authors.joinToString(" / "), style = MaterialTheme.typography.bodyMedium)
                    Text(title.sourceId, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = vm::addToShelf, enabled = !book.onShelf && state.busy == null) { Text(if (book.onShelf) "已加入书架" else "加入书架") }
                    if (book.completed) Text("已读完", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        title.manga.description?.takeIf { it.isNotBlank() }?.let { description -> item { Text(description, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium) } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("章节 · ${chapters.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (selected != null) Button(onClick = { reader.open(title.book, title, selected) }, enabled = state.busy == null) { Text(if (last?.chapterKey.isNullOrBlank()) "开始阅读" else "继续阅读") }
            }
        }
        item {
            OutlinedButton(onClick = { chooseFolder(null) }, enabled = !exportState.running && chapters.any { !it.locked }) {
                Icon(Icons.Outlined.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("下载整本到文件夹")
            }
            Text("整本合并为一个 ZIP，含 ${chapters.count { !it.locked }} 个可读章节。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chapters.isEmpty()) item { TextButton(onClick = vm::refreshDetail, enabled = state.busy == null) { Text("刷新章节列表") } }
        items(chapters, key = { it.key }) { chapter ->
            val saved = progress[chapter.key]
            val completed = saved?.completed == true
            val downloaded = chapter.key in state.downloaded
            Row(Modifier.fillMaxWidth().clickable(enabled = !chapter.locked && state.busy == null) { reader.open(title.book, title, chapter) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chapter.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val status = listOfNotNull(if (chapter.locked) "已锁定" else null, if (downloaded) "已下载" else null,
                        when {
                            completed -> "已读完"
                            saved?.completed == false -> "未读完"
                            saved != null -> "已读 ${(saved.progress * 100).toInt()}%"
                            else -> null
                        }).joinToString(" · ")
                    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { chooseFolder(chapter.key) }, enabled = !chapter.locked && !exportState.running) { Icon(Icons.Outlined.FolderOpen, "保存 ${chapter.label} 到文件夹") }
                if (downloaded) IconButton(onClick = { deleteChapter = chapter }, enabled = state.downloadStatus == null) { Icon(Icons.Outlined.DeleteOutline, "删除 ${chapter.label} 的下载") }
                else IconButton(onClick = { vm.download(chapter) }, enabled = !chapter.locked && state.downloadStatus == null) { Icon(Icons.Outlined.Download, "下载 ${chapter.label}") }
            }
            HorizontalDivider()
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
    Surface(modifier.aspectRatio(.72f), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
