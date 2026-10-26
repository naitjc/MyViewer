@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chen.myviewer.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.chen.myviewer.*
import com.chen.myviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Green = Color(0xFF315B48)
private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF63765C), primaryContainer = Color(0xFFDCE9DF), onPrimaryContainer = Color(0xFF173B28), secondaryContainer = Color(0xFFE0E8DA), onSecondaryContainer = Color(0xFF24362B), tertiaryContainer = Color(0xFFE7E6DB), onTertiaryContainer = Color(0xFF3E4433), background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6), surfaceContainer = Color(0xFFEFF1E9), onSurface = Color(0xFF24332D))
private val DarkColors = darkColorScheme(primary = Color(0xFFA8CAB2), primaryContainer = Color(0xFF2B4838), onPrimaryContainer = Color(0xFFD4ECD9), secondaryContainer = Color(0xFF334638), onSecondaryContainer = Color(0xFFDCE9DF), tertiaryContainer = Color(0xFF414539), onTertiaryContainer = Color(0xFFE7E6DB), background = Color(0xFF171D1A), surface = Color(0xFF171D1A), surfaceContainer = Color(0xFF263229), onSurface = Color(0xFFDEE6DE), onSurfaceVariant = Color(0xFFB8C5B9), outlineVariant = Color(0xFF3F4B42))

@Composable
fun ViewerApp(vm: MainViewModel) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        val library by vm.library.collectAsStateWithLifecycle()
        val reader by vm.reader.collectAsStateWithLifecycle()
        StatusBarAppearance(if (reader?.book?.kind == "COMIC") Color.Black else MaterialTheme.colorScheme.surface)
        val loading by vm.loading.collectAsStateWithLifecycle()
        val importing by vm.importing.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val librarySort by vm.librarySort.collectAsStateWithLifecycle()
        val libraryColumns by vm.libraryColumns.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val failedBook by vm.failedBook.collectAsStateWithLifecycle()
        val notice by vm.notice.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var shelfFilter by rememberSaveable { mutableStateOf("全部") }
        var shelfQuery by rememberSaveable { mutableStateOf("") }
        var lastShelfTap by remember { mutableLongStateOf(-1L) }
        val shelfState = rememberLazyGridState()
        val shelfConfiguration = "${librarySort.name}|$libraryColumns|$shelfFilter|$shelfQuery"
        var appliedShelfConfiguration by rememberSaveable { mutableStateOf(shelfConfiguration) }
        LaunchedEffect(shelfConfiguration) {
            if (appliedShelfConfiguration != shelfConfiguration) {
                shelfState.requestScrollToItem(0)
                appliedShelfConfiguration = shelfConfiguration
            }
        }
        val returnToTop: () -> Unit = { focus.clearFocus(); keyboard?.hide(); shelfState.requestScrollToItem(0) }
        val openBook: (Book) -> Unit = { focus.clearFocus(); keyboard?.hide(); vm.open(it) }
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
                topBar = { TopAppBar(title = { Column { Text("MYVIEWER", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(listOf("我的书库", "最近阅读", "偏好设置")[tab], fontWeight = FontWeight.Medium) } }, actions = {
                    if (tab == 0) IconButton(onClick = { sourceDialog = true }) { Icon(Icons.Outlined.Add, "导入书籍") }
                }) },
                bottomBar = { NavigationBar {
                    val icons = listOf(Icons.Outlined.AutoStories, Icons.Outlined.History, Icons.Outlined.Tune)
                    listOf("书库", "历史", "设置").forEachIndexed { index, label -> NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            focus.clearFocus(); keyboard?.hide()
                            if (index == 0) {
                                val now = SystemClock.uptimeMillis()
                                if (lastShelfTap >= 0 && now - lastShelfTap <= doubleTapTimeout) returnToTop()
                                lastShelfTap = now
                            } else lastShelfTap = -1
                            tab = index
                        },
                        modifier = if (index == 0) Modifier.semantics { customActions = listOf(CustomAccessibilityAction("回到书库顶部") { tab = 0; returnToTop(); true }) } else Modifier,
                        icon = { Icon(icons[index], label) }, label = { Text(label) }) }
                } },
            ) { padding -> Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> Bookshelf(books = library.filter { it.book.onShelf }, sort = librarySort, onSort = vm::setLibrarySort,
                        filter = shelfFilter, onFilter = { shelfFilter = it }, query = shelfQuery, onQuery = { shelfQuery = it },
                        columns = libraryColumns, state = shelfState, onTop = returnToTop,
                        open = openBook, manage = { managing = it }, import = { sourceDialog = true })
                    1 -> History(library.filter { it.book.inHistory }, openBook, vm::clearHistory, { tab = 0 })
                    else -> SettingsScreen(vm, settings, libraryColumns, vm::setLibraryColumns)
                }
            } }
        }
        loading?.let { loadingBook ->
            BackHandler { vm.closeReader() }
            AlertDialog(onDismissRequest = { vm.closeReader() }, title = { Text("正在打开") }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(loadingBook.title) } }, confirmButton = {}, dismissButton = { TextButton(onClick = { vm.closeReader() }) { Text("取消") } })
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
private fun StatusBarAppearance(background: Color) {
    val window = LocalActivity.current?.window
    val darkIcons = background.luminance() > .5f
    LaunchedEffect(window, darkIcons) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = darkIcons }
    }
}

@Composable
internal fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, book.coverPath) { value = withContext(Dispatchers.IO) { book.coverPath?.let { BitmapFactory.decodeFile(it) } } }
    Surface(modifier.aspectRatio(.72f), shape = RoundedCornerShape(9.dp), color = if (book.kind == "TEXT") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 80.dp
            val tiny = maxWidth < 56.dp
            Column(Modifier.fillMaxSize().padding(if (tiny) 6.dp else if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(if (book.kind == "TEXT") "TXT" else "ZIP", fontSize = if (tiny) 8.sp else 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(book.title.take(24), fontFamily = FontFamily.Serif, fontSize = if (tiny) 11.sp else if (compact) 14.sp else 20.sp, maxLines = if (tiny) 3 else 4, overflow = TextOverflow.Ellipsis)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .15f))
            }
        }
    }
}
internal fun progressLabel(row: LibraryBook): String = if (row.book.lastOpenedAt == 0L) "尚未阅读" else if (row.book.kind == "COMIC") "第 ${(row.position?.imageIndex ?: 0) + 1} 页" else "已读 ${((row.position?.progress ?: 0f) * 100).toInt()}%"

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
internal fun EmptyState(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(22.dp).size(38.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp)); action?.invoke()
    }
}
