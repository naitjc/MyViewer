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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import com.chen.myviewer.*
import com.chen.myviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate

private val SettingsSectionSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })

@Composable
fun ViewerApp(vm: MainViewModel) {
    val sources: com.chen.myviewer.source.SourceViewModel = viewModel()
    val savedSources: com.chen.myviewer.source.SourceViewModel = viewModel(key = "saved-online")
    val novels: com.chen.myviewer.novel.NovelViewModel = viewModel()
    MyViewerTheme {
        val library by vm.library.collectAsStateWithLifecycle()
        val folders by vm.folders.collectAsStateWithLifecycle()
        val refreshingFolders by vm.refreshingFolders.collectAsStateWithLifecycle()
        val checkingUpdates by vm.checkingUpdates.collectAsStateWithLifecycle()
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
        var savedNovelId by rememberSaveable { mutableStateOf<String?>(null) }
        var savedOnlineBookId by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(savedNovelId) {
            savedNovelId?.let { id ->
                if (novels.detail.value?.book?.id != id) vm.repository.dao.book(id)?.let(novels::showSaved)
            }
        }
        LaunchedEffect(savedOnlineBookId) {
            savedOnlineBookId?.let { id ->
                if (savedSources.state.value.detail?.book?.id != id && savedSources.state.value.busy == null) {
                    vm.repository.dao.book(id)?.let(savedSources::showSaved)
                }
            }
        }
        val sourceCatalogState = rememberLazyListState()
        val sourceSearchState = rememberLazyListState()
        val sourceDetailState = rememberLazyListState()
        val savedOnlineDetailState = rememberLazyListState()
        val sourceState by sources.state.collectAsStateWithLifecycle()
        val savedSourceState by savedSources.state.collectAsStateWithLifecycle()
        var appliedSearchSession by rememberSaveable { mutableLongStateOf(sourceState.searchSession) }
        LaunchedEffect(sourceState.searchSession) {
            if (appliedSearchSession != sourceState.searchSession) {
                sourceSearchState.requestScrollToItem(0)
                appliedSearchSession = sourceState.searchSession
            }
        }
        var appliedDetailBookId by rememberSaveable { mutableStateOf<String?>(sourceState.detail?.book?.id) }
        LaunchedEffect(sourceState.detail?.book?.id) {
            val id = sourceState.detail?.book?.id
            if (id != null && appliedDetailBookId != id) {
                sourceDetailState.requestScrollToItem(0)
                appliedDetailBookId = id
            }
        }
        var appliedSavedDetailBookId by rememberSaveable { mutableStateOf<String?>(savedSourceState.detail?.book?.id) }
        LaunchedEffect(savedSourceState.detail?.book?.id) {
            val id = savedSourceState.detail?.book?.id
            if (id != null && appliedSavedDetailBookId != id) {
                savedOnlineDetailState.requestScrollToItem(0)
                appliedSavedDetailBookId = id
            }
        }
        var shelfFilter by rememberSaveable { mutableStateOf("全部") }
        var shelfCompletion by rememberSaveable { mutableStateOf<Boolean?>(null) }
        var shelfQuery by rememberSaveable { mutableStateOf("") }
        var expandedSettings by rememberSaveable(stateSaver = SettingsSectionSaver) { mutableStateOf<List<String>>(emptyList()) }
        var lastShelfTap by remember { mutableLongStateOf(-1L) }
        val shelfState = rememberLazyGridState()
        val shelfConfiguration = "${librarySort.name}|$libraryColumns|$shelfFilter|$shelfCompletion|$shelfQuery"
        var appliedShelfConfiguration by rememberSaveable { mutableStateOf(shelfConfiguration) }
        LaunchedEffect(shelfConfiguration) {
            if (appliedShelfConfiguration != shelfConfiguration) {
                shelfState.requestScrollToItem(0)
                appliedShelfConfiguration = shelfConfiguration
            }
        }
        val returnToTop: () -> Unit = { focus.clearFocus(); keyboard?.hide(); shelfState.requestScrollToItem(0) }
        val openBook: (Book) -> Unit = {
            focus.clearFocus(); keyboard?.hide()
            if (it.storage == "NOVEL") { novels.showSaved(it); savedNovelId = it.id; tab = 0 }
            else if (it.storage == "ONLINE") {
                if (it.unreadChapters > 0) vm.clearUnreadChapters(it.id)
                savedSources.showSaved(it); savedOnlineBookId = it.id
            } else vm.open(it)
        }
        var sourceDialog by remember { mutableStateOf(false) }
        var recursive by rememberSaveable { mutableStateOf(true) }
        var managing by remember { mutableStateOf<Book?>(null) }
        var renaming by remember { mutableStateOf<Book?>(null) }
        var renameInput by remember { mutableStateOf("") }
        var batchMode by rememberSaveable { mutableStateOf(false) }
        var selectedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var relinking by remember { mutableStateOf<Book?>(null) }
        BackHandler(batchMode) { batchMode = false; selectedIds = emptyList() }
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
            else if (savedNovelId != null) SavedNovelScreen(novels, vm) { savedNovelId = null; novels.back() }
            else Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = { if (tab != 1 && savedOnlineBookId == null) TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Text(
                            listOf("书库", "发现", "历史", "设置")[tab],
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = listOf("书库", "发现", "历史", "设置")[tab]; heading() }
                        )
                    },
                    actions = {
                        if (tab == 0) {
                            if (folders.isNotEmpty()) {
                                IconButton(
                                    onClick = { vm.refreshAllFolders() },
                                    enabled = !refreshingFolders,
                                    modifier = Modifier.semantics { contentDescription = "刷新文件夹" }
                                ) {
                                    if (refreshingFolders) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.Sync, "刷新文件夹")
                                }
                            }
                            if (library.any { it.book.onShelf && it.book.storage == "ONLINE" }) {
                                IconButton(
                                    onClick = { vm.checkOnlineMangaUpdates() },
                                    enabled = !checkingUpdates,
                                    modifier = Modifier.semantics { contentDescription = "检查漫画更新" }
                                ) {
                                    if (checkingUpdates) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.Refresh, "检查漫画更新")
                                }
                            }
                            FilledTonalButton(
                                onClick = { sourceDialog = true },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.padding(end = 10.dp).heightIn(min = 36.dp).semantics { contentDescription = "导入书籍" }
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导入", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                ) },
                bottomBar = { Column {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        val outlinedIcons = listOf(Icons.Outlined.AutoStories, Icons.Outlined.Public, Icons.Outlined.History, Icons.Outlined.Tune)
                        val selectedIcons = listOf(Icons.Rounded.AutoStories, Icons.Rounded.Public, Icons.Rounded.History, Icons.Rounded.Tune)
                        listOf("书库", "发现", "历史", "设置").forEachIndexed { index, label ->
                            val isSelected = tab == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    focus.clearFocus(); keyboard?.hide()
                                    val closingSavedOnline = savedOnlineBookId != null
                                    if (closingSavedOnline) {
                                        savedOnlineBookId = null
                                        savedSources.back()
                                    }
                                    if (index == 0 && !closingSavedOnline) {
                                        val now = SystemClock.uptimeMillis()
                                        if (lastShelfTap >= 0 && now - lastShelfTap <= doubleTapTimeout) returnToTop()
                                        lastShelfTap = now
                                    } else lastShelfTap = -1
                                    tab = index
                                },
                                modifier = if (index == 0) Modifier.semantics { customActions = listOf(CustomAccessibilityAction("回到书库顶部") { savedOnlineBookId = null; savedSources.back(); tab = 0; returnToTop(); true }) } else Modifier,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = { Icon(if (isSelected) selectedIcons[index] else outlinedIcons[index], label) },
                                label = { Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) }
                            )
                        }
                    }
                } },
            ) { padding -> Box(Modifier.padding(padding)) {
                if (savedOnlineBookId != null) {
                    SavedOnlineMangaScreen(
                        vm = savedSources,
                        reader = vm,
                        listState = savedOnlineDetailState,
                        backLabel = if (tab == 2) "返回历史" else "返回书库",
                        onBack = { savedOnlineBookId = null; savedSources.back() }
                    )
                } else Crossfade(targetState = tab, animationSpec = tween(180), label = "tab") { currentTab ->
                    when (currentTab) {
                        0 -> Bookshelf(
                            books = library.filter { it.book.onShelf }, sort = librarySort, onSort = vm::setLibrarySort,
                            filter = shelfFilter, onFilter = { shelfFilter = it }, completion = shelfCompletion, onCompletion = { shelfCompletion = it }, query = shelfQuery, onQuery = { shelfQuery = it },
                            columns = libraryColumns, state = shelfState, onTop = returnToTop,
                            open = openBook, manage = { managing = it }, import = { sourceDialog = true },
                            batchMode = batchMode,
                            selectedIds = selectedIds.toSet(),
                            onToggleSelect = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                            onSelectAll = { ids -> selectedIds = ids },
                            onExitBatch = { batchMode = false; selectedIds = emptyList() },
                            onBatchComplete = { completed -> vm.setCompletedBatch(selectedIds.toSet(), completed); batchMode = false; selectedIds = emptyList() },
                            onBatchRemove = { vm.hideBatch(selectedIds.toSet()); batchMode = false; selectedIds = emptyList() },
                        )
                        1 -> SourceScreen(
                            vm = sources,
                            reader = vm,
                            catalogState = sourceCatalogState,
                            searchState = sourceSearchState,
                            detailState = sourceDetailState,
                        )
                        2 -> {
                            val today by produceState(LocalDate.now().toString()) {
                                while (isActive) { delay(30000); value = LocalDate.now().toString() }
                            }
                            val totals by remember(today) { vm.readingTotals(today) }.collectAsStateWithLifecycle(ReadingTotals())
                            val bookTimes by vm.bookReadingTimes.collectAsStateWithLifecycle()
                            History(library.filter { it.book.inHistory }, openBook, vm::clearHistory, { tab = 0 }, totals, bookTimes, vm::removeFromHistory)
                        }
                        else -> SettingsScreen(vm, settings, libraryColumns, vm::setLibraryColumns, expandedSettings) { section ->
                            expandedSettings = if (section in expandedSettings) expandedSettings - section else expandedSettings + section
                        }
                    }
                }
            } }
        }
        loading?.let { loadingBook ->
            BackHandler { vm.closeReader() }
            AlertDialog(onDismissRequest = { vm.closeReader() }, title = { Text("正在打开") }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(loadingBook.title) } }, confirmButton = {}, dismissButton = { TextButton(onClick = { vm.closeReader() }) { Text("取消") } })
        }
        if (sourceDialog) AlertDialog(onDismissRequest = { sourceDialog = false }, title = { Text("导入本地书籍") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("支持 TXT 小说和 ZIP 漫画。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            ImportOptionTile(
                icon = Icons.Outlined.InsertDriveFile,
                title = "选择文件，可多选",
                caption = "TXT 小说或 ZIP 漫画",
                onClick = { sourceDialog = false; files.launch(arrayOf("*/*")) }
            )
            ImportOptionTile(
                icon = Icons.Outlined.FolderOpen,
                title = "选择文件夹扫描",
                caption = "批量导入文件夹中的书籍",
                onClick = { sourceDialog = false; folder.launch(null) }
            )
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(recursive, { recursive = it }); Text("扫描子文件夹") }
        } }, confirmButton = { TextButton(onClick = { sourceDialog = false }) { Text("关闭") } })
        importing?.let { ImportDialog(it, vm) }
        if (managing != null) {
            val book = managing!!
            val subtitle = if (book.storage == "NOVEL") "已保存的小说" else if (book.storage == "ONLINE") "在线漫画" else "${if (book.kind == "TEXT") "TXT 小说" else "ZIP 漫画"} · ${if (book.storage == "COPY") "应用内副本" else "直接读取"}"
            ModalBottomSheet(
                onDismissRequest = { managing = null },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BookCover(book, Modifier.width(48.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ListItem(
                        headlineContent = { Text(if (book.pinned) "取消置顶" else "置顶到书架") },
                        leadingContent = { Icon(Icons.Outlined.PushPin, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { vm.setPinned(book, !book.pinned); managing = null }
                    )
                    ListItem(
                        headlineContent = { Text(if (book.completed) "取消已读完标记" else "标记为已读完") },
                        leadingContent = { Icon(if (book.completed) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { vm.setCompleted(book, !book.completed); managing = null }
                    )
                    ListItem(
                        headlineContent = { Text("重命名") },
                        leadingContent = { Icon(Icons.Outlined.Edit, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { renameInput = book.title; renaming = book; managing = null }
                    )
                    ListItem(
                        headlineContent = { Text("批量管理") },
                        leadingContent = { Icon(Icons.Outlined.Checklist, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { batchMode = true; selectedIds = listOf(book.id); managing = null }
                    )
                    if (book.storage !in setOf("ONLINE", "NOVEL")) {
                        ListItem(
                            headlineContent = { Text("重新定位原文件，改为直接读取") },
                            leadingContent = { Icon(Icons.Outlined.FindInPage, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { relinking = book; managing = null; relink.launch(arrayOf("*/*")) }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ListItem(
                        headlineContent = { Text("移出书架", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { vm.hide(book); managing = null }
                    )
                    Text(
                        "移出书架后仍保留文件、进度、书签和历史。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }
        }
        renaming?.let { book ->
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("重命名书籍") },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        label = { Text("书名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { vm.rename(book, renameInput); renaming = null },
                        enabled = renameInput.trim().isNotEmpty()
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { renaming = null }) { Text("取消") }
                }
            )
        }
        if (error != null) AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("暂时无法打开") }, text = { Text(error!!) }, confirmButton = {
            failedBook?.takeIf { it.storage != "ONLINE" }?.let { failed -> TextButton(onClick = { vm.error.value = null; relinking = failed; relink.launch(arrayOf("*/*")) }) { Text("重新定位文件") } }
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

private data class CoverCloth(val start: Color, val end: Color, val ink: Color)

private val LightCloths = listOf(
    CoverCloth(Color(0xFFDCE7DC), Color(0xFFCCDCCD), Color(0xFF1F3527)),
    CoverCloth(Color(0xFFECE4D5), Color(0xFFDFD4C0), Color(0xFF382F22)),
    CoverCloth(Color(0xFFDCE4E8), Color(0xFFCCD7DD), Color(0xFF213139)),
    CoverCloth(Color(0xFFEDE0D6), Color(0xFFE0CEBF), Color(0xFF3D271C)),
    CoverCloth(Color(0xFFE3E6D6), Color(0xFFD4D9C4), Color(0xFF2B3321)),
    CoverCloth(Color(0xFFE5E0E6), Color(0xFFD7D0DA), Color(0xFF312835)),
)

private val DarkCloths = listOf(
    CoverCloth(Color(0xFF24382D), Color(0xFF1B2B22), Color(0xFFD4E7DA)),
    CoverCloth(Color(0xFF332C24), Color(0xFF26201A), Color(0xFFE8DFD3)),
    CoverCloth(Color(0xFF232E36), Color(0xFF1A232A), Color(0xFFD2E0E8)),
    CoverCloth(Color(0xFF382923), Color(0xFF2A1E19), Color(0xFFECDAD2)),
    CoverCloth(Color(0xFF2B3123), Color(0xFF21261A), Color(0xFFDCE2D0)),
    CoverCloth(Color(0xFF2F2933), Color(0xFF231E27), Color(0xFFE2DAE6)),
)

@Composable
internal fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val cloth = remember(book.id, dark) {
        val index = (book.id.hashCode() and Int.MAX_VALUE) % LightCloths.size
        if (dark) DarkCloths[index] else LightCloths[index]
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, book.coverPath, book.localPath) {
        value = withContext(Dispatchers.IO) {
            if (book.storage == "ONLINE") {
                val repository = (context.applicationContext as ViewerApplication).sources
                try { repository.title(book).manga.cover?.let { repository.cover(it) } }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { null }
            } else book.coverPath?.let { BitmapFactory.decodeFile(it) }
        }
    }
    Surface(
        modifier.aspectRatio(.72f),
        shape = RoundedCornerShape(8.dp),
        color = cloth.start,
        shadowElevation = 1.dp,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(cloth.start, cloth.end)))) {
            val w = maxWidth.value
            val titleFontSize = (w * 0.15f).coerceIn(9f, 18f)
            val titleLineHeight = titleFontSize * 1.3f
            val contentPad = (w * 0.09f).coerceIn(5f, 14f).dp
            val typeFontSize = (w * 0.07f).coerceIn(7f, 10f).sp
            val tiny = w < 56f
            val typeLabel = if (book.storage == "NOVEL") "小说" else if (book.kind == "TEXT") "TXT" else if (book.storage == "ONLINE") "在线" else "ZIP"
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.06f).background(cloth.ink.copy(alpha = 0.08f)))
            Box(Modifier.padding(start = 3.dp).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = if (dark) 0.06f else 0.35f)))
            Column(
                Modifier.fillMaxSize().padding(contentPad),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    typeLabel,
                    fontSize = typeFontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = cloth.ink.copy(alpha = 0.55f)
                )
                Text(
                    book.title.take(24),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleFontSize.sp,
                    lineHeight = titleLineHeight.sp,
                    color = cloth.ink,
                    maxLines = if (w < 64f) 3 else 4,
                    overflow = TextOverflow.Ellipsis
                )
                Column(verticalArrangement = Arrangement.spacedBy(if (tiny) 2.dp else 3.dp)) {
                    HorizontalDivider(color = cloth.ink.copy(alpha = 0.18f), thickness = 1.dp)
                    Box(Modifier.fillMaxWidth(0.36f).height(if (tiny) 1.5.dp else 2.dp).background(cloth.ink.copy(alpha = 0.25f), RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}
internal fun progressLabel(row: LibraryBook): String = when {
    row.book.completed -> "已读完"
    row.book.lastOpenedAt == 0L -> "尚未阅读"
    row.book.kind == "COMIC" -> "第 ${(row.position?.imageIndex ?: 0) + 1} 页"
    else -> "已读 ${((row.position?.progress ?: 0f) * 100).toInt()}%"
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
internal fun EmptyState(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        ) {
            Surface(
                modifier = Modifier.padding(10.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(icon, null, Modifier.padding(18.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        action?.invoke()
    }
}

@Composable
private fun ImportOptionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
