@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.*
import com.chen.myviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private val Green = Color(0xFF315B48)
private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF63765C), primaryContainer = Color(0xFFDCE9DF), onPrimaryContainer = Color(0xFF173B28), secondaryContainer = Color(0xFFE0E8DA), onSecondaryContainer = Color(0xFF24362B), tertiaryContainer = Color(0xFFE7E6DB), onTertiaryContainer = Color(0xFF3E4433), background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6), surfaceContainer = Color(0xFFEFF1E9), onSurface = Color(0xFF24332D))
private val DarkColors = darkColorScheme(primary = Color(0xFFA8CAB2), secondaryContainer = Color(0xFF334638), onSecondaryContainer = Color(0xFFDCE9DF), tertiaryContainer = Color(0xFF414539), onTertiaryContainer = Color(0xFFE7E6DB), background = Color(0xFF171D1A), surface = Color(0xFF171D1A), surfaceContainer = Color(0xFF263229))

@Composable
fun ViewerApp(vm: MainViewModel) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        val library by vm.library.collectAsStateWithLifecycle()
        val reader by vm.reader.collectAsStateWithLifecycle()
        val loading by vm.loading.collectAsStateWithLifecycle()
        val importing by vm.importing.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val failedBook by vm.failedBook.collectAsStateWithLifecycle()
        val notice by vm.notice.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var sourceDialog by remember { mutableStateOf(false) }
        var recursive by rememberSaveable { mutableStateOf(true) }
        var managing by remember { mutableStateOf<Book?>(null) }
        var relinking by remember { mutableStateOf<Book?>(null) }
        fun grant(uri: Uri) = runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
        val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) { val temporary = uris.filterNot(::grant).toSet(); vm.selectedFiles(uris, temporary) } }
        val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) { val retained = grant(uri); vm.selectedFolder(uri, recursive, retained) } }
        val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val book = relinking; relinking = null
            if (uri != null && book != null) { grant(uri); vm.relink(book, uri) }
        }
        LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.notice.value = null } }
        Surface(Modifier.fillMaxSize()) {
            if (reader != null) key(reader!!.position.sessionId) { ReaderScreen(vm, reader!!, settings, snackbar) }
            else Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = { TopAppBar(title = { Column { Text("MYVIEWER", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(listOf("我的书库", "最近阅读", "设置")[tab], fontWeight = FontWeight.Medium) } }, actions = {
                    if (tab == 0) IconButton(onClick = { sourceDialog = true }) { Icon(Icons.Outlined.Add, "导入书籍") }
                }) },
                bottomBar = { NavigationBar {
                    val icons = listOf(Icons.Outlined.AutoStories, Icons.Outlined.History, Icons.Outlined.Tune)
                    listOf("书库", "历史", "设置").forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label) }) }
                } },
            ) { padding -> Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> Bookshelf(library.filter { it.book.onShelf }, vm::open, { managing = it }, { sourceDialog = true })
                    1 -> History(library.filter { it.book.inHistory }, vm::open, vm::clearHistory)
                    else -> SettingsScreen(vm, settings)
                }
            } }
        }
        if (loading != null) {
            BackHandler { vm.closeReader() }
            AlertDialog(onDismissRequest = { vm.closeReader() }, title = { Text("正在打开") }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(loading!!.title) } }, confirmButton = {}, dismissButton = { TextButton(onClick = { vm.closeReader() }) { Text("取消") } })
        }
        if (sourceDialog) AlertDialog(onDismissRequest = { sourceDialog = false }, title = { Text("导入本地书籍") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("支持 TXT 小说和 ZIP 漫画。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { sourceDialog = false; files.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.InsertDriveFile, null); Spacer(Modifier.width(8.dp)); Text("选择文件，可多选") }
            FilledTonalButton(onClick = { sourceDialog = false; folder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("选择文件夹扫描") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(recursive, { recursive = it }); Text("扫描子文件夹") }
        } }, confirmButton = { TextButton(onClick = { sourceDialog = false }) { Text("关闭") } })
        importing?.let { ImportDialog(it, vm) }
        if (managing != null) AlertDialog(onDismissRequest = { managing = null }, title = { Text(managing!!.title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${if (managing!!.kind == "TEXT") "TXT 小说" else "ZIP 漫画"} · ${if (managing!!.storage == "COPY") "应用内副本" else "直接读取"}")
            Text("移出书架后仍保留文件、进度、书签和历史。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { relinking = managing; managing = null; relink.launch(arrayOf("*/*")) }) { Text("重新定位原文件，改为直接读取") }
        } }, confirmButton = { TextButton(onClick = { vm.hide(managing!!); managing = null }) { Text("移出书架") } }, dismissButton = { TextButton(onClick = { managing = null }) { Text("关闭") } })
        if (error != null) AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("暂时无法打开") }, text = { Text(error!!) }, confirmButton = {
            failedBook?.let { failed -> TextButton(onClick = { vm.error.value = null; relinking = failed; relink.launch(arrayOf("*/*")) }) { Text("重新定位文件") } }
        }, dismissButton = { TextButton(onClick = { vm.error.value = null }) { Text("关闭") } })
    }
}

@Composable
private fun Bookshelf(books: List<LibraryBook>, open: (Book) -> Unit, manage: (Book) -> Unit, import: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("全部") }
    var sort by rememberSaveable { mutableStateOf("最近阅读") }
    var sortMenu by remember { mutableStateOf(false) }
    val shown = books.filter { filter == "全部" || (filter == "小说" && it.book.kind == "TEXT") || (filter == "漫画" && it.book.kind == "COMIC") }.let {
        when (sort) { "书名" -> it.sortedBy { row -> row.book.title }; "导入时间" -> it.sortedByDescending { row -> row.book.importedAt }; else -> it }
    }
    if (books.isEmpty()) { EmptyState("把喜欢的故事放进书架", "从手机中选择 TXT 小说或 ZIP 漫画，阅读位置会自动保存。", Icons.Outlined.AutoStories) { Button(onClick = import) { Text("导入第一本书") } }; return }
    LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val recent = books.firstOrNull { it.book.lastOpenedAt > 0 }
            if (recent != null) Surface(onClick = { open(recent.book) }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BookCover(recent.book, Modifier.width(46.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("继续阅读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(recent.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium); Text(progressLabel(recent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "小说", "漫画").forEach { label -> FilterChip(filter == label, { filter = label }, label = { Text(label) }) }
                Spacer(Modifier.weight(1f)); Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "排序：$sort") }
                    DropdownMenu(sortMenu, { sortMenu = false }) { listOf("最近阅读", "书名", "导入时间").forEach { label -> DropdownMenuItem(text = { Text(label) }, onClick = { sort = label; sortMenu = false }) } }
                }
            }
        }
        items(shown, key = { it.book.id }) { row ->
            Column(Modifier.combinedClickable(onClick = { open(row.book) }, onLongClick = { manage(row.book) })) {
                BookCover(row.book, Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp)); Text(row.book.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp)); Text(progressLabel(row), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("这个分类还没有书籍。", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, book.coverPath) { value = withContext(Dispatchers.IO) { book.coverPath?.let { BitmapFactory.decodeFile(it) } } }
    Surface(modifier.aspectRatio(.72f), shape = RoundedCornerShape(9.dp), color = if (book.kind == "TEXT") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (book.kind == "TEXT") "TXT" else "ZIP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(book.title.take(24), fontFamily = FontFamily.Serif, fontSize = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .15f))
        }
    }
}
private fun progressLabel(row: LibraryBook): String = if (row.book.lastOpenedAt == 0L) "尚未阅读" else if (row.book.kind == "COMIC") "第 ${(row.position?.imageIndex ?: 0) + 1} 页" else "已读 ${((row.position?.progress ?: 0f) * 100).toInt()}%"

@Composable
private fun History(books: List<LibraryBook>, open: (Book) -> Unit, clear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    if (books.isEmpty()) EmptyState("从读过的地方继续", "打开过的书籍会显示在这里。", Icons.Outlined.History)
    else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("${books.size} 本最近阅读", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); TextButton(onClick = { confirm = true }) { Text("清空历史") } } }
        items(books, key = { it.book.id }) { row ->
            Row(Modifier.fillMaxWidth().clickable { open(row.book) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(row.book, Modifier.width(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(row.book.title, fontWeight = FontWeight.Medium); Text(progressLabel(row), style = MaterialTheme.typography.bodySmall); Text(SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(row.book.lastOpenedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Outlined.ChevronRight, null)
            }; HorizontalDivider()
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清空最近阅读？") }, text = { Text("阅读进度和书签会保留。") }, confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("清空") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable
private fun SettingsScreen(vm: MainViewModel, settings: ReaderSettings) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("小说阅读", style = MaterialTheme.typography.titleMedium) }
        item { ReadingSettings(settings, vm::updateSettings) }
        item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Text("文件夹来源", style = MaterialTheme.typography.titleMedium) }
        if (folders.isEmpty()) item { Text("导入时选择文件夹后，可在这里刷新。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(folders, key = { it.uri }) { folder -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(12.dp)); Text(folder.name, Modifier.weight(1f))
            IconButton(onClick = { vm.selectedFolder(Uri.parse(folder.uri), folder.recursive) }) { Icon(Icons.Outlined.Refresh, "刷新文件夹") }
            IconButton(onClick = { vm.removeFolder(folder.uri) }) { Icon(Icons.Outlined.Close, "移除扫描来源") }
        } }
        item { HorizontalDivider(); Text("MyViewer ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall); Text("本地阅读 · 进度与书签自动保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedButton(onClick = { vm.clearComicCache() }) { Text("清理漫画图片缓存") } }
    }
}

@Composable
fun ReadingSettings(settings: ReaderSettings, update: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("字号  ${settings.fontSize.toInt()}"); Slider(settings.fontSize, { update(settings.copy(fontSize = it)) }, valueRange = 14f..36f, steps = 21)
        Text("行距  ${"%.1f".format(settings.lineSpacing)}"); Slider(settings.lineSpacing, { update(settings.copy(lineSpacing = it)) }, valueRange = 1.2f..2.3f)
        Text("阅读背景")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { listOf("PAPER" to "纸色", "WHITE" to "浅色", "GREEN" to "护眼", "NIGHT" to "深色").forEach { (id, label) -> FilterChip(settings.background == id, { update(settings.copy(background = id)) }, label = { Text(label) }) } }
        Text("翻页动画")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("SLIDE" to "平移", "CURL" to "仿真翻书").forEach { (id, label) -> FilterChip(settings.animation == id, { update(settings.copy(animation = id)) }, label = { Text(label) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("亮度跟随系统", Modifier.weight(1f)); Switch(settings.brightness < 0, { update(settings.copy(brightness = if (it) -1f else .5f)) }) }
        if (settings.brightness >= 0) Slider(settings.brightness, { update(settings.copy(brightness = it)) }, valueRange = .03f..1f)
    }
}

@Composable
private fun ImportDialog(ui: ImportUi, vm: MainViewModel) {
    AlertDialog(onDismissRequest = vm::dismissImport, title = { Text(if (ui.result != null) "导入结果" else "导入书籍") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ui.message)
        when {
            ui.busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.result != null -> {
                Text("新增 ${ui.result.added} 本 · 已存在 ${ui.result.existing} 本")
                if (ui.result.failures.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 240.dp)) { items(ui.result.failures) { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) } }
            }
            ui.candidates.isNotEmpty() -> {
                LazyColumn(Modifier.heightIn(max = 170.dp)) { items(ui.candidates, key = { it.uri.toString() }) { Text(it.name, maxLines = 2, modifier = Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall) } }
                HorizontalDivider()
                val directAllowed = ui.candidates.all { it.directAllowed }
                Row(Modifier.fillMaxWidth().clickable(enabled = directAllowed) { vm.importCopy(false) }, verticalAlignment = Alignment.CenterVertically) { RadioButton(!ui.copy, { vm.importCopy(false) }, enabled = directAllowed); Column { Text("直接读取原文件"); Text(if (directAllowed) "节省空间，原文件需保持可访问" else "来源未提供长期读取授权，本次需复制", style = MaterialTheme.typography.bodySmall) } }
                Row(Modifier.fillMaxWidth().clickable { vm.importCopy(true) }, verticalAlignment = Alignment.CenterVertically) { RadioButton(ui.copy, { vm.importCopy(true) }); Column { Text("复制到应用"); Text("占用额外空间，复制后可独立阅读", style = MaterialTheme.typography.bodySmall) } }
            }
        }
    } }, confirmButton = { if (!ui.busy) { if (ui.result == null && ui.candidates.isNotEmpty()) TextButton(onClick = { vm.startImport() }) { Text("导入 ${ui.candidates.size} 本") } else TextButton(onClick = vm::dismissImport) { Text("完成") } } }, dismissButton = { if (!ui.busy && ui.result == null) TextButton(onClick = vm::dismissImport) { Text("取消") } })
}

@Composable
private fun EmptyState(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(12.dp)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); action?.invoke()
    }
}
