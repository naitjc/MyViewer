@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.chen.myviewer.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.ReaderSession
import com.chen.myviewer.data.ReaderSettings
import com.chen.myviewer.reader.comic.ComicDocument
import com.chen.myviewer.reader.comic.ComicImageView
import com.chen.myviewer.reader.comic.ComicPage
import com.chen.myviewer.reader.comic.ImageSize
import com.chen.myviewer.reader.text.TextReaderView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ReaderScreen(vm: MainViewModel, session: ReaderSession, settings: ReaderSettings, snackbar: SnackbarHostState) {
    var menu by remember { mutableStateOf(false) }
    var menuHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var sheet by remember { mutableStateOf<String?>(null) }
    var textView by remember { mutableStateOf<TextReaderView?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val token = session.position.sessionId
    var undoTarget by remember(token) { mutableStateOf<Pair<Int, String>?>(null) }
    val recordUndo: () -> Unit = {
        undoTarget = if (session.text != null) {
            session.position.textOffset to "${(session.position.progress * 100).toInt()}%"
        } else {
            session.position.imageIndex to "第 ${session.position.imageIndex + 1} 页"
        }
    }
    val performUndo: () -> Unit = {
        undoTarget?.let { (target, _) ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (session.text != null) vm.seekText(target) else vm.seekComic(target)
            undoTarget = null
        }
    }
    ReadingTimeEffect(vm, token)
    val automatic = remember(token) { AutoReadingState() }
    val pauseAutomatic: () -> Unit = { automatic.stop(); textView?.flush(); vm.flushPosition() }
    val close: () -> Unit = { pauseAutomatic(); vm.closeReader() }
    val finish: () -> Unit = { pauseAutomatic(); vm.finishReading(token) }
    BackHandler(onBack = close)
    LaunchedEffect(session.preferences.mode) { automatic.stop() }
    DisposableEffect(lifecycle, token) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) pauseAutomatic() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { automatic.stop(); lifecycle.lifecycle.removeObserver(observer) }
    }
    val window = (context as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); window?.let { it.attributes = it.attributes.apply { screenBrightness = originalBrightness } } }
    }
    LaunchedEffect(window, settings.brightness) {
        window?.let { if (it.attributes.screenBrightness != settings.brightness) it.attributes = it.attributes.apply { screenBrightness = settings.brightness } }
    }

    Box(Modifier.fillMaxSize().then(if (sheet != null) Modifier.clearAndSetSemantics { } else Modifier).background(if (session.book.kind == "COMIC") Color.Black else MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing).pointerInput(automatic) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (automatic.running) pauseAutomatic()
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        }
    }) {
        if (session.text != null) {
            AndroidView(factory = { TextReaderView(it).also { view -> textView = view } }, update = { view ->
                view.onMenu = { pauseAutomatic(); menu = !menu }
                view.onPosition = { offset, force -> if (vm.reader.value?.position?.sessionId == token) vm.textPosition(offset, force) }
                view.onBoundary = { vm.notice.value = it }
                view.onFinished = finish
                view.configure(session.text, settings, session.preferences.mode, session.position.textOffset)
            }, modifier = Modifier.fillMaxSize())
            LaunchedEffect(session.jump) { if (session.jump > 0) textView?.goTo(session.jumpOffset) }
            AutoReadingEffect(automatic, session.preferences.mode, settings,
                ready = { textView?.readyForAutoReading == true },
                nextPage = { textView?.nextAutomatically() ?: true },
                scroll = { textView?.scrollAutomatically(it) ?: true },
                finished = finish,
                flush = { textView?.flush() })
        } else if (session.comic != null) ComicContent(vm, session, settings, automatic, finish, { pauseAutomatic(); menu = !menu })

        val menuColorScheme = if (session.comic != null) darkColorScheme(
            primary = Color(0xFF8EC5A5),
            onPrimary = Color(0xFF0D2619),
            primaryContainer = Color(0xFF213C2E),
            onPrimaryContainer = Color(0xFFD3ECDC),
            surface = Color(0xFF171D1A),
            surfaceContainerLowest = Color(0xFF181F1C),
            onSurface = Color.White,
            outlineVariant = Color(0xFF2E3B34),
        ) else MaterialTheme.colorScheme

        AnimatedVisibility(
            visible = menu,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 2 },
            exit = fadeOut(tween(130)) + slideOutVertically(tween(130)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            MaterialTheme(colorScheme = menuColorScheme) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = .97f), contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 3.dp) {
                    Column {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架") }
                            Column(Modifier.weight(1f)) {
                                Text(session.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                session.chapter?.let { Text(it.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                                session.novelChapter?.let { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (session.text != null) IconButton(onClick = { sheet = "bookmarks" }) { Icon(Icons.Outlined.BookmarkBorder, "查看书签") }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = menu,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
            exit = fadeOut(tween(130)) + slideOutVertically(tween(130)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MaterialTheme(colorScheme = menuColorScheme) {
                Surface(
                    Modifier.fillMaxWidth().onSizeChanged { menuHeight = it.height },
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shadowElevation = 5.dp
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        session.onlineTitle?.let { title ->
                            val chapters = title.manga.chapters.orEmpty().asReversed()
                            val current = chapters.indexOfFirst { it.key == session.chapter?.key }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { pauseAutomatic(); vm.open(title.book, title, chapters[current - 1]) }, enabled = current > 0) { Text("上一章") }
                                TextButton(onClick = { sheet = "online-chapters" }) { Text("章节目录") }
                                TextButton(onClick = { pauseAutomatic(); vm.open(title.book, title, chapters[current + 1]) }, enabled = current >= 0 && current < chapters.lastIndex) { Text("下一章") }
                            }
                        }
                        session.novelTitle?.let { title ->
                            val chapters = title.novel.chapters
                            val current = chapters.indexOfFirst { it.url == session.novelChapter?.url }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { pauseAutomatic(); vm.open(title.book, novel = title, novelChapter = chapters[current - 1]) }, enabled = current > 0) { Text("上一章") }
                                TextButton(onClick = { sheet = "novel-chapters" }) { Text("章节目录") }
                                TextButton(onClick = { pauseAutomatic(); vm.open(title.book, novel = title, novelChapter = chapters[current + 1]) }, enabled = current >= 0 && current < chapters.lastIndex) { Text("下一章") }
                            }
                        }
                        if (session.text != null && session.novelTitle == null) {
                            val chapters = session.text.chapters
                            val current = chapters.indexOfLast { it.offset <= session.position.textOffset }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = {
                                    val target = chapters[current - 1].offset
                                    if (target != session.position.textOffset) recordUndo()
                                    vm.seekText(target)
                                }, enabled = current > 0) { Text("上一章") }
                                TextButton(onClick = { sheet = "chapters" }, modifier = Modifier.weight(1f)) { Text(session.text.chapterAt(session.position.textOffset), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                TextButton(onClick = {
                                    val target = chapters[current + 1].offset
                                    if (target != session.position.textOffset) recordUndo()
                                    vm.seekText(target)
                                }, enabled = current < chapters.lastIndex) { Text("下一章") }
                            }
                        }
                        ReaderProgress(session, undoLabel = undoTarget?.second, onUndo = performUndo) { value ->
                            if (session.text != null) {
                                val target = (value * (session.text.text.length - 1).coerceAtLeast(0)).toInt()
                                if (target != session.position.textOffset) recordUndo()
                                vm.seekText(target)
                            } else {
                                val target = (value * (session.comic!!.imageNames.size - 1)).toInt()
                                if (target != session.position.imageIndex) recordUndo()
                                vm.seekComic(target)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            if (session.text != null) {
                                ReaderAction(Icons.AutoMirrored.Outlined.FormatListBulleted, "目录", "章节目录") { sheet = if (session.novelTitle != null) "novel-chapters" else "chapters" }
                                ReaderAction(Icons.Outlined.Bookmarks, "书签", "手动书签") { sheet = "bookmarks" }
                            } else ReaderAction(Icons.Outlined.Numbers, "页码", "阅读进度") { sheet = "jump" }
                            ReaderAction(Icons.Outlined.PlayArrow, "自动阅读", "自动阅读") { sheet = "automatic" }
                            ReaderAction(Icons.Outlined.Settings, "设置", "阅读设置") { sheet = "settings" }
                        }
                    }
                }
            }
        }
        if (automatic.running) {
            FilledTonalButton(onClick = pauseAutomatic, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
                Icon(Icons.Outlined.Pause, null); Spacer(Modifier.width(8.dp)); Text("暂停自动阅读")
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (menu) with(density) { menuHeight.toDp() } + 8.dp else 16.dp))
    }
    sheet?.let { active ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = if (active == "settings") Color.Black.copy(alpha = 0.14f) else BottomSheetDefaults.ScrimColor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding()) {
                when (active) {
                    "novel-chapters" -> {
                        Text("章节目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        val title = session.novelTitle!!
                        LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(title.novel.chapters, key = { it.url }) { chapter ->
                                ChapterSheetRow(
                                    label = chapter.name,
                                    current = chapter.url == session.novelChapter?.url,
                                    enabled = !chapter.locked
                                ) {
                                    pauseAutomatic(); sheet = null; vm.open(title.book, novel = title, novelChapter = chapter)
                                }
                            }
                        }
                    }
                    "online-chapters" -> {
                        Text("章节目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        val title = session.onlineTitle!!
                        LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(title.manga.chapters.orEmpty(), key = { it.key }) { chapter ->
                                ChapterSheetRow(
                                    label = chapter.label,
                                    current = chapter.key == session.chapter?.key,
                                    enabled = !chapter.locked
                                ) {
                                    pauseAutomatic(); sheet = null; vm.open(title.book, title, chapter)
                                }
                            }
                        }
                    }
                    "automatic" -> AutoReadingSettings(settings, session.preferences.mode, vm::updateSettings) {
                        menu = false; sheet = null; automatic.start()
                    }
                    "settings" -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (session.text != null) "阅读设置" else "漫画设置", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { sheet = null }) { Icon(Icons.Outlined.Close, "关闭面板") }
                        }
                        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (session.text != null) {
                                ReadingSettings(settings, vm::updateSettings, session.preferences.mode, vm::mode, showPreview = false)
                                if (session.novelTitle == null) {
                                    var more by remember { mutableStateOf(false) }
                                    TextButton(onClick = { more = !more }) { Text("更多设置") }
                                    if (more) {
                                        Text("TXT 编码 · 当前 ${session.text.encoding}")
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("AUTO" to "自动", "UTF-8" to "UTF-8", "GB18030" to "GB18030", "UTF-16LE" to "UTF-16LE").forEach { (id, label) ->
                                            val selected = session.book.encoding == id
                                            FilterChip(selected, { textView?.flush(); sheet = null; vm.encoding(id) }, label = { Text(label) }, colors = shelfChipColors(), border = shelfChipBorder(selected))
                                        } }
                                    }
                                }
                            } else {
                                ReaderChoice("阅读方式", listOf("PAGE" to "翻页", "SCROLL" to "连续"), session.preferences.mode, vm::mode)
                                if (session.preferences.mode == "PAGE") ReaderChoice("阅读方向", listOf("LTR" to "从左到右", "RTL" to "从右到左"), session.preferences.direction, vm::direction)
                                BrightnessSettings(settings, vm::updateSettings)
                                HorizontalDivider()
                                TextButton(onClick = { sheet = "jump" }, modifier = Modifier.fillMaxWidth()) { Text("当前页 · ${session.position.imageIndex + 1} / ${session.comic!!.imageNames.size}") }
                                TextButton(onClick = { sheet = "automatic" }, modifier = Modifier.fillMaxWidth()) { Text("自动阅读") }
                            }
                        }
                    }
                    "chapters" -> {
                        Text("章节目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        val chapters = session.text?.chapters.orEmpty()
                        val currentIndex = chapters.indexOfLast { it.offset <= session.position.textOffset }
                        if (chapters.isEmpty()) Text("未识别到章节标题，可以继续阅读全文。", Modifier.padding(vertical = 24.dp))
                        else LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(chapters, key = { _, chapter -> chapter.offset }) { index, chapter ->
                                ChapterSheetRow(
                                    label = chapter.title,
                                    current = index == currentIndex
                                ) {
                                    if (chapter.offset != session.position.textOffset) recordUndo()
                                    vm.seekText(chapter.offset)
                                    sheet = null
                                    menu = false
                                }
                            }
                        }
                    }
                    "bookmarks" -> {
                        val bookmarks by remember(session.book.id, session.position.chapterKey) { vm.repository.dao.chapterBookmarks(session.book.id, session.position.chapterKey) }.collectAsStateWithLifecycle(emptyList())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("手动书签", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { textView?.flush(); vm.addBookmark() }) { Text("＋ 保存当前位置") }
                        }
                        if (bookmarks.isEmpty()) Text("还没有手动书签。上次阅读位置会另外自动保存。", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(bookmarks, key = { it.id }) { bookmark ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                            if (bookmark.offset != session.position.textOffset) recordUndo()
                                            vm.seekText(bookmark.offset); sheet = null; menu = false
                                        }.padding(vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(bookmark.chapter, fontWeight = FontWeight.Medium)
                                        Text(bookmark.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { vm.deleteBookmark(bookmark.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除书签", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        } }
                    }
                    "jump" -> {
                        var value by remember { mutableFloatStateOf(if (session.text != null) session.position.progress else session.position.imageIndex.toFloat() / (session.comic!!.imageNames.size - 1).coerceAtLeast(1)) }
                        Text("阅读进度", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (session.text != null) "${(value * 100).toInt()}%" else "第 ${(value * (session.comic!!.imageNames.size - 1)).toInt() + 1} 页", Modifier.padding(top = 16.dp))
                        ReaderSlider(value, { value = it }, valueLabel = if (session.text != null) "${(value * 100).toInt()}%" else "${(value * (session.comic!!.imageNames.size - 1)).toInt() + 1}", onFinished = {
                            if (session.text != null) {
                                val target = (value * (session.text.text.length - 1).coerceAtLeast(0)).toInt()
                                if (target != session.position.textOffset) recordUndo()
                                vm.seekText(target)
                            } else {
                                val target = (value * (session.comic!!.imageNames.size - 1)).toInt()
                                if (target != session.position.imageIndex) recordUndo()
                                vm.seekComic(target)
                            }
                        })
                    }
                }
            }
        }
    }
}

private data class ComicFile(val page: ComicPage? = null, val size: ImageSize? = null, val error: String? = null, val retry: (() -> Unit)? = null) {
    val ratio: Float get() = size?.let { it.width.toFloat() / it.height } ?: .7f
}
private data class ComicViewport(val index: Int, val top: Int, val bottom: Int)

@Composable
private fun rememberComicFile(archive: ComicDocument, index: Int): State<ComicFile> {
    var attempt by remember(archive, index) { mutableIntStateOf(0) }
    val cached = remember(archive, index) { archive.cachedPage(index) }
    return key(archive, index) { produceState(ComicFile(cached, cached?.size ?: archive.cachedSize(index)), archive, index, attempt) {
        try {
            val size = withContext(Dispatchers.IO) { archive.size(index) }
            if (value.page == null) value = ComicFile(size = size)
            value = ComicFile(withContext(Dispatchers.IO) { archive.page(index) }, size)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { value = value.copy(error = e.message ?: "图片读取失败", retry = { attempt++ }) }
    } }
}

private suspend fun prefetchComic(archive: ComicDocument, visible: List<Int>, forward: Boolean = true) = withContext(Dispatchers.IO) {
    if (visible.isEmpty()) return@withContext
    val first = visible.min(); val last = visible.max()
    // Load three pages in the travel direction and one behind, including when scrolling back up.
    val nearby = (visible + (1..3).map { if (forward) last + it else first - it } + listOf(if (forward) first - 1 else last + 1)).filter { it in archive.imageNames.indices }.distinct()
    for (index in nearby) { ensureActive(); runCatching { archive.page(index) } }
    if (!archive.eagerSizeReads) return@withContext
    // Dimensions are cheap header reads; stable item heights avoid re-layout as full images arrive.
    for (index in (first - if (forward) 2 else 8).coerceAtLeast(0)..(last + if (forward) 8 else 2).coerceAtMost(archive.imageNames.lastIndex)) {
        ensureActive(); runCatching { archive.size(index) }
    }
}

@Composable
private fun ComicContent(vm: MainViewModel, session: ReaderSession, settings: ReaderSettings, automatic: AutoReadingState, finish: () -> Unit, menu: () -> Unit) {
    val archive = session.comic!!
    val token = session.position.sessionId
    if (session.preferences.mode == "PAGE") {
        val image by rememberComicFile(archive, session.position.imageIndex)
        LaunchedEffect(image.error, automatic.running) {
            if (image.error != null && automatic.running) { automatic.stop(); vm.notice.value = "图片读取失败，自动阅读已暂停" }
        }
        AutoReadingEffect(automatic, "PAGE", settings, ready = {
            vm.reader.value?.takeIf { it.position.sessionId == token }?.position?.imageIndex?.let { archive.cachedPage(it) != null } == true
        }, nextPage = {
            val index = vm.reader.value?.takeIf { it.position.sessionId == token }?.position?.imageIndex ?: archive.imageNames.lastIndex
            if (index >= archive.imageNames.lastIndex) false else { vm.seekComic(index + 1); true }
        }, finished = finish, flush = vm::flushPosition)
        LaunchedEffect(archive, session.position.imageIndex) { prefetchComic(archive, listOf(session.position.imageIndex)) }
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { ComicImageView(it) }, update = { view ->
                view.interactive = true; view.tapToTurn = true
                view.rtl = session.preferences.direction == "RTL"
                view.onMenu = menu
                view.onPage = { next -> vm.reader.value?.takeIf { it.position.sessionId == token && it.preferences.mode == "PAGE" }?.let { vm.turnComic(next, token) } }
                view.onFocus = { ratio -> if (vm.reader.value?.position?.sessionId == token) vm.comicPosition(session.position.imageIndex, ratio, false) }
                view.onGestureEnd = { if (vm.reader.value?.position?.sessionId == token) vm.flushPosition() }
                view.onError = { vm.notice.value = it }
                image.page?.let { view.setPage(it) } ?: view.clearPage()
            }, modifier = Modifier.fillMaxSize())
            if (image.page == null) ComicPlaceholder(image.error, Modifier.fillMaxSize(), image.retry)
            Text("${session.position.imageIndex + 1} / ${archive.imageNames.size}", Modifier.align(Alignment.BottomEnd).padding(16.dp), color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
        }
    } else {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = session.position.imageIndex)
        val zoom = remember(archive) { ComicCanvasState() }
        val sizes = remember(archive) { mutableStateMapOf<Int, Int>() }
        val imageViews = remember(archive) { mutableMapOf<Int, ComicImageView>() }
        val viewports = remember(archive) { mutableMapOf<Int, ComicViewport>() }
        var restored by remember { mutableStateOf(false) }
        AutoReadingEffect(automatic, "SCROLL", settings,
            ready = { restored && archive.cachedPage(state.firstVisibleItemIndex) != null && (state.canScrollForward || archive.cachedPage(archive.imageNames.lastIndex) != null) },
            scroll = { distance ->
                if (!state.canScrollForward) false
                else { state.scrollBy(distance / zoom.scale); true }
            }, finished = finish, flush = {
                if (restored && vm.reader.value?.position?.sessionId == token) {
                    val index = state.firstVisibleItemIndex
                    val height = sizes[index] ?: state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 1
                    vm.comicPosition(index, (state.firstVisibleItemScrollOffset.toFloat() / height.coerceAtLeast(1)).coerceIn(0f, 1f), true)
                }
                vm.flushPosition()
            })
        LaunchedEffect(session.jump, archive) {
            restored = false
            val index = session.position.imageIndex; val ratio = session.position.imageRatio
            state.scrollToItem(index)
            if (ratio > 0f) {
                val height = withTimeoutOrNull(5000) { snapshotFlow { sizes[index] }.filterNotNull().first() }
                if (height != null) state.scrollToItem(index, (height * ratio).toInt())
            }
            restored = true
        }
        LaunchedEffect(state, archive) {
            snapshotFlow {
                val info = state.layoutInfo
                info.visibleItemsInfo.map { ComicViewport(it.index, (zoom.visibleTop(info.viewportEndOffset) - it.offset).toInt().coerceAtLeast(0), (zoom.visibleBottom(info.viewportEndOffset) - it.offset).toInt().coerceAtMost(it.size)) }
            }.collect { windows ->
                viewports.clear()
                for (window in windows) { viewports[window.index] = window; imageViews[window.index]?.updateViewport(window.top, window.bottom) }
            }
        }
        LaunchedEffect(state, archive) {
            var previous = state.firstVisibleItemIndex
            var forward = true
            snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }.distinctUntilChanged().collectLatest { indices ->
                indices.firstOrNull()?.let { first -> if (first != previous) { forward = first > previous; previous = first } }
                prefetchComic(archive, indices, forward)
            }
        }
        LaunchedEffect(state, archive) {
            snapshotFlow { Triple(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, state.isScrollInProgress) }.distinctUntilChanged().collect { (index, offset, scrolling) ->
                if (restored && vm.reader.value?.position?.sessionId == token) {
                    val height = sizes[index] ?: state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 1
                    vm.comicPosition(index, (offset.toFloat() / height.coerceAtLeast(1)).coerceIn(0f, 1f), !scrolling && !automatic.running)
                }
            }
        }
        ComicZoomSurface(zoom, menu, overflow = { state.dispatchRawDelta(it) },
            atEnd = { restored && !state.canScrollForward && state.layoutInfo.totalItemsCount == archive.imageNames.size && archive.cachedPage(archive.imageNames.lastIndex) != null },
            onBeyondEnd = finish) {
            LazyColumn(state = state, userScrollEnabled = !zoom.zoomed, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(archive.imageNames, key = { _, it -> it }) { index, _ ->
                    val image by rememberComicFile(archive, index)
                    LaunchedEffect(image.error, automatic.running) {
                        if (image.error != null && automatic.running) { automatic.stop(); vm.notice.value = "图片读取失败，自动阅读已暂停" }
                    }
                    val itemModifier = Modifier.fillMaxWidth().then(if (image.size != null) Modifier.aspectRatio(image.ratio.coerceAtLeast(.005f)) else Modifier.height(300.dp)).onSizeChanged { if (image.size != null) sizes[index] = it.height }
                    if (image.page != null) {
                        AndroidView(factory = { ComicImageView(it).apply { interactive = false }.also { imageViews[index] = it } },
                            onRelease = { if (imageViews[index] === it) imageViews.remove(index) },
                            update = { view -> view.setPage(image.page!!); view.updateDisplayScale(zoom.scale); viewports[index]?.let { view.updateViewport(it.top, it.bottom) } },
                            modifier = itemModifier)
                    } else ComicPlaceholder(image.error, itemModifier, image.retry)
                }
            }
        }
    }
}

@Composable
private fun ComicPlaceholder(error: String?, modifier: Modifier, retry: (() -> Unit)?) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, Modifier.padding(24.dp), color = Color.LightGray)
            retry?.let { TextButton(onClick = it) { Text("重试图片") } }
        } else CircularProgressIndicator()
    }
}

@Composable
private fun ReaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.semantics { contentDescription = description }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChapterSheetRow(label: String, current: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                Modifier.weight(1f),
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    current -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (current) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReaderProgress(session: ReaderSession, undoLabel: String? = null, onUndo: () -> Unit = {}, seek: (Float) -> Unit) {
    val progress = if (session.text != null) session.position.progress else session.position.imageIndex.toFloat() / (session.comic!!.imageNames.size - 1).coerceAtLeast(1)
    var value by remember(session.position.sessionId, progress) { mutableFloatStateOf(progress) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("阅读进度", style = MaterialTheme.typography.bodySmall)
            if (undoLabel != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onUndo,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    modifier = Modifier.semantics { contentDescription = "撤回上次位置" }
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Outlined.Replay, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("撤回 · $undoLabel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (session.text != null) "${(value * 100).toInt()}%" else "${(value * (session.comic!!.imageNames.size - 1)).toInt() + 1} / ${session.comic.imageNames.size} 页", style = MaterialTheme.typography.bodySmall)
        }
        ReaderSlider(value, { value = it }, Modifier.fillMaxWidth().semantics { contentDescription = "阅读进度滑杆" }, valueLabel = "${(value * 100).toInt()}%", onFinished = { seek(value) })
    }
}
