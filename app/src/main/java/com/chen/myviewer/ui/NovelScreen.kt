@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chen.myviewer.download.FolderDownloadViewModel
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.novel.*

@Composable
internal fun DiscoveryTabs(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("漫画", "小说").forEach { label -> FilterChip(selected == label, { onSelect(label) }, label = { Text(label) }, colors = shelfChipColors()) }
    }
}

@Composable
internal fun NovelScreen(vm: NovelViewModel, reader: MainViewModel, catalogHeader: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads: FolderDownloadViewModel = viewModel()
    var importDialog by remember { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf(NOVEL_SOURCE_URL) }
    var filter by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<NovelSource?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importFile) }
    BackHandler(state.detail != null || state.source != null) { vm.back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.source != null || state.detail != null) IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回小说源") }
            Text(state.detail?.novel?.name ?: state.source?.name ?: "发现", Modifier.weight(1f).clearAndSetSemantics { contentDescription = "小说源页面"; heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.detail == null && state.source == null && !state.catalogFilter.isNullOrBlank()) IconButton(onClick = { vm.importUrl(state.catalogFilter!!) }, enabled = state.busy == null) { Icon(Icons.Outlined.Refresh, "更新当前小说源仓库") }
            if (state.detail == null && state.source == null) IconButton(onClick = { importDialog = true }) { Icon(Icons.Outlined.Add, "导入小说源") }
            if (state.detail != null) IconButton(onClick = vm::refreshDetail, enabled = state.busy == null) { Icon(Icons.Outlined.Refresh, "刷新小说目录") }
        }
        if (state.detail == null && state.source == null) catalogHeader()
        FolderDownloadStatus(downloads)
        state.busy?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.downloadStatus?.let { status -> Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::cancelDownload) { Text("取消下载") }
        } }
        state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp)) { Text(error); TextButton(onClick = vm::dismissError) { Text("关闭提示") } }
        } }
        when {
            state.detail != null -> NovelDetails(vm, reader, state, downloads)
            state.source != null -> {
                OutlinedTextField(state.query, vm::query, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("搜索小说") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    trailingIcon = { IconButton(onClick = { vm.search() }, enabled = state.busy == null) { Icon(Icons.Outlined.Search, "搜索小说") } })
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                    if (state.results.isEmpty() && state.busy == null) item { Text(if (state.page == 0) "输入书名或作者搜索小说" else "没有找到小说，可以调整关键词。", Modifier.padding(vertical = 24.dp)) }
                    items(state.results, key = { it.url }) { book ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = state.busy == null) { vm.show(book) }.padding(vertical = 14.dp)) {
                            Text(book.name, style = MaterialTheme.typography.titleMedium)
                            if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    if (state.hasNextPage) item { TextButton(onClick = { vm.search(true) }, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) { Text("加载更多小说") } }
                }
            }
            else -> {
                OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("筛选小说源") }, singleLine = true)
                val catalogs = (state.sources.mapNotNull { it.catalog } + listOfNotNull(state.catalogFilter?.takeIf { it.isNotBlank() })).distinct()
                if (catalogs.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.catalogFilter == null, { vm.filterCatalog(null) }, label = { Text("全部已导入") }, colors = shelfChipColors())
                    catalogs.forEach { catalog -> FilterChip(state.catalogFilter == catalog, { vm.filterCatalog(catalog) }, label = { Text(novelCatalogName(catalog)) }, colors = shelfChipColors()) }
                    if (state.sources.any { it.catalog == null }) FilterChip(state.catalogFilter == "", { vm.filterCatalog("") }, label = { Text("其他来源") }, colors = shelfChipColors())
                }
                val visibleSources = state.sources.filter {
                    (state.catalogFilter == null || it.catalog.orEmpty() == state.catalogFilter) &&
                        "${it.name} ${it.baseUrl} ${it.json.optString("bookSourceGroup")}".contains(filter, true)
                }
                if (state.sources.isEmpty()) {
                    Text("导入 Legado JSON 书源后即可搜索小说。", Modifier.padding(20.dp))
                    FilledTonalButton(onClick = { importDialog = true }, modifier = Modifier.padding(horizontal = 20.dp)) { Text("导入小说源") }
                } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                    if (visibleSources.isEmpty()) item { Text("没有匹配的小说源，可以调整仓库筛选或关键词。", Modifier.padding(vertical = 20.dp)) }
                    items(visibleSources, key = { it.id }) { source ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = state.busy == null) { vm.choose(source) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                Text(source.baseUrl, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.choose(source) }, enabled = state.busy == null) { Icon(Icons.Outlined.Search, "浏览小说源 ${source.name}") }
                            IconButton(onClick = { removing = source }, enabled = state.busy == null) { Icon(Icons.Outlined.DeleteOutline, "删除小说源 ${source.name}") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (importDialog) ModalBottomSheet(onDismissRequest = { importDialog = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).imePadding().navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("小说书源仓库", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { importDialog = false }) { Icon(Icons.Outlined.Close, "关闭导入面板") }
            }
            Text("选择个人维护仓库，或填写 Legado 书源 JSON 地址。", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NOVEL_SOURCE_CATALOGS.forEach { catalog -> FilterChip(url == catalog.url, { url = catalog.url }, label = { Text(catalog.name) }, colors = shelfChipColors()) }
            }
            NOVEL_SOURCE_CATALOGS.firstOrNull { it.url == url }?.let { catalog -> Text(catalog.repository, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(url, { url = it }, label = { Text("书源 JSON 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { importDialog = false; vm.importUrl(url.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("网络导入") }
            TextButton(onClick = { importDialog = false; filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("从 JSON 文件导入") }
        }
    }
    removing?.let { source -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("删除小说源？") }, text = { Text(source.name) },
        confirmButton = { TextButton(onClick = { removing = null; vm.uninstall(source) }) { Text("删除") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
}

@Composable
private fun NovelDetails(vm: NovelViewModel, reader: MainViewModel, state: NovelBrowserState, downloads: FolderDownloadViewModel) {
    val title = state.detail!!
    val exportState by downloads.state.collectAsStateWithLifecycle()
    val chooseFolder = rememberFolderDownloadPicker(title.book.id, downloads) { chapter, folder -> downloads.novel(title, chapter, folder) }
    val library by reader.library.collectAsStateWithLifecycle()
    val row = library.firstOrNull { it.book.id == title.book.id }
    val book = row?.book ?: title.book
    val progress by remember(book.id) { reader.repository.dao.chapterPositions(book.id) }.collectAsStateWithLifecycle(emptyList())
    val saved = progress.associateBy { it.chapterKey }
    var deleting by remember { mutableStateOf(false) }
    val resume = title.novel.chapters.firstOrNull { it.url == row?.position?.chapterKey } ?: title.novel.chapters.firstOrNull()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(book, Modifier.width(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title.novel.name, style = MaterialTheme.typography.titleLarge)
                    if (title.novel.author.isNotBlank()) Text(title.novel.author, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = vm::addToShelf, enabled = !book.onShelf && state.busy == null) { Text(if (book.onShelf) "已加入书架" else "加入书架") }
                    if (book.completed) Text("已读完", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (title.novel.intro.isNotBlank()) item { Text(title.novel.intro, style = MaterialTheme.typography.bodyMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { resume?.let { reader.open(book, novel = title, novelChapter = it) } }, enabled = resume != null && state.busy == null) { Text(if (row?.position == null) "开始阅读" else "继续阅读") }
                OutlinedButton(onClick = { vm.download(title.novel.chapters) }, enabled = state.downloadStatus == null) { Text("离线下载全书") }
            }
            Text("应用内已下载 ${state.downloaded.size} / ${title.novel.chapters.size} 章", style = MaterialTheme.typography.bodySmall)
            if (state.downloaded.isNotEmpty()) TextButton(onClick = { deleting = true }, enabled = state.downloadStatus == null) { Text("删除离线正文") }
        }
        item {
            OutlinedButton(onClick = { chooseFolder(null) }, enabled = !exportState.running && title.novel.chapters.any { !it.locked }) {
                Icon(Icons.Outlined.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("下载整本到文件夹")
            }
            Text("合并保存为 UTF-8 TXT，可下载 ${title.novel.chapters.count { !it.locked }} 章。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text("章节 · ${title.novel.chapters.size}", style = MaterialTheme.typography.titleMedium) }
        items(title.novel.chapters, key = { it.url }) { chapter ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !chapter.locked && state.busy == null) { reader.open(book, novel = title, novelChapter = chapter) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chapter.name)
                    val status = listOfNotNull(if (chapter.locked) "需站点授权" else null, if (saved[chapter.url]?.completed == true) "已读完" else null, if (chapter.url in state.downloaded) "已下载" else null).joinToString(" · ")
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { chooseFolder(chapter.url) }, enabled = !chapter.locked && !exportState.running) { Icon(Icons.Outlined.FolderOpen, "保存小说章节 ${chapter.name} 到文件夹") }
                if (chapter.url !in state.downloaded) IconButton(onClick = { vm.download(listOf(chapter)) }, enabled = !chapter.locked && state.downloadStatus == null) { Icon(Icons.Outlined.Download, "下载小说章节 ${chapter.name}") }
            }
            HorizontalDivider()
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除离线正文？") }, text = { Text(title.novel.name) },
        confirmButton = { TextButton(onClick = { deleting = false; vm.deleteDownloads() }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
}
