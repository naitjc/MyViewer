@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.data.ChapterPosition
import com.chen.myviewer.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SourceScreen(vm: SourceViewModel, reader: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
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
            state.detail != null -> MangaDetails(vm, reader, state)
            state.source != null -> SourceSearch(vm, state)
            else -> SourceCatalog(vm, state, { repositoryUrl = state.repositoryUrl; repositoryDialog = true })
        }
    }
    if (repositoryDialog) AlertDialog(onDismissRequest = { repositoryDialog = false }, title = { Text("漫画源仓库") }, text = {
        OutlinedTextField(repositoryUrl, { repositoryUrl = it }, label = { Text("HTTPS 索引地址") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
    }, confirmButton = { TextButton(onClick = { repositoryDialog = false; vm.loadCatalog(true, repositoryUrl) }) { Text("加载仓库") } }, dismissButton = { TextButton(onClick = { repositoryDialog = false }) { Text("取消") } })
}

@Composable
private fun SourceCatalog(vm: SourceViewModel, state: SourceBrowserState, repository: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var chineseOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy) { if (state.catalog.isEmpty() && state.busy == null && state.error == null) vm.loadCatalog() }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("漫画源", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.loadCatalog(true) }, enabled = state.busy == null) { Icon(Icons.Outlined.Refresh, "刷新源仓库") }
            IconButton(onClick = repository) { Icon(Icons.Outlined.Settings, "管理源仓库") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("搜索漫画源") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) })
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!chineseOnly, { chineseOnly = false }, label = { Text("全部语言") })
            FilterChip(chineseOnly, { chineseOnly = true }, label = { Text("中文") })
        }
        val installed = state.installed.associateBy { it.id }
        val entries = (state.installed.map { it.entry } + state.catalog).groupBy { it.id }.map { (_, versions) -> versions.maxBy { it.version } }
            .filter { (!chineseOnly || it.languages.any { language -> language.startsWith("zh") }) && (query.isBlank() || "${it.name} ${it.id}".contains(query, true)) }
            .sortedWith(compareByDescending<SourceEntry> { it.id in installed }.thenBy { it.name })
        if (entries.isEmpty() && state.busy == null) {
            EmptyState("还没有可用的漫画源", "加载仓库后选择漫画源进行安装。", Icons.Outlined.Public) {
                Button(onClick = { vm.loadCatalog(true) }) { Text("重新加载") }
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { it.id }) { entry ->
                val local = installed[entry.id]
                Row(Modifier.fillMaxWidth().clickable(enabled = local != null && state.busy == null) { vm.choose(local!!.entry) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.languages.joinToString(" / ")} · v${entry.version}${if (local != null) " · 已安装" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (local == null) FilledTonalButton(onClick = { vm.install(entry) }, enabled = state.busy == null) { Text("安装") }
                    else if (local.entry.version < entry.version) TextButton(onClick = { vm.install(entry) }, enabled = state.busy == null) { Text("更新") }
                    else IconButton(onClick = { vm.choose(local.entry) }, enabled = state.busy == null) { Icon(Icons.Outlined.Search, "浏览 ${entry.name}") }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
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
private fun MangaDetails(vm: SourceViewModel, reader: MainViewModel, state: SourceBrowserState) {
    val title = state.detail!!
    val positions by remember(title.book.id) { reader.repository.dao.chapterPositions(title.book.id) }.collectAsStateWithLifecycle(emptyList())
    val last by produceState<com.chen.myviewer.data.ReadingPosition?>(null, title.book.id) { value = reader.repository.dao.position(title.book.id) }
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
                    FilledTonalButton(onClick = vm::addToShelf, enabled = !title.book.onShelf && state.busy == null) { Text(if (title.book.onShelf) "已加入书架" else "加入书架") }
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
        if (chapters.isEmpty()) item { TextButton(onClick = vm::refreshDetail, enabled = state.busy == null) { Text("刷新章节列表") } }
        items(chapters, key = { it.key }) { chapter ->
            val saved = progress[chapter.key]
            val downloaded = chapter.key in state.downloaded
            Row(Modifier.fillMaxWidth().clickable(enabled = !chapter.locked && state.busy == null) { reader.open(title.book, title, chapter) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chapter.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val status = listOfNotNull(if (chapter.locked) "已锁定" else null, if (downloaded) "已下载" else null,
                        saved?.let { if (it.progress >= .999f) "已读" else "已读 ${(it.progress * 100).toInt()}%" }).joinToString(" · ")
                    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
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
