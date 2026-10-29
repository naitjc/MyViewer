@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.chen.myviewer.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.ReaderSession
import com.chen.myviewer.data.ReaderSettings
import com.chen.myviewer.reader.comic.ComicArchive
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
    var sheet by remember { mutableStateOf<String?>(null) }
    var textView by remember { mutableStateOf<TextReaderView?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val token = session.position.sessionId
    val automatic = remember(token) { AutoReadingState() }
    val pauseAutomatic: () -> Unit = { automatic.stop(); textView?.flush(); vm.flushPosition() }
    val close: () -> Unit = { pauseAutomatic(); vm.closeReader() }
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

    Box(Modifier.fillMaxSize().background(if (session.book.kind == "COMIC") Color.Black else MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing).pointerInput(automatic) {
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
                view.configure(session.text, settings, session.preferences.mode, session.position.textOffset)
            }, modifier = Modifier.fillMaxSize())
            LaunchedEffect(session.jump) { if (session.jump > 0) textView?.goTo(session.jumpOffset) }
            AutoReadingEffect(automatic, session.preferences.mode, settings,
                ready = { textView?.readyForAutoReading == true },
                nextPage = { textView?.nextAutomatically() ?: true },
                scroll = { textView?.scrollAutomatically(it) ?: true },
                finished = { vm.notice.value = "已到末尾，自动阅读已停止" },
                flush = { textView?.flush() })
        } else if (session.comic != null) ComicContent(vm, session, settings, automatic, { pauseAutomatic(); menu = !menu })

        if (menu) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = .97f), shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.Outlined.ArrowBack, "返回书架") }
                    Text(session.book.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { sheet = "settings" }) { Icon(Icons.Outlined.Tune, "阅读设置") }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = .98f), shadowElevation = 5.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextButton(onClick = { sheet = "jump" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (session.text != null) "${session.text.chapterAt(session.position.textOffset)} · ${(session.position.progress * 100).toInt()}%" else "第 ${session.position.imageIndex + 1} / ${session.comic!!.images.size} 页 · 点击跳转")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (session.comic != null) menu = false; vm.mode(if (session.preferences.mode == "PAGE") "SCROLL" else "PAGE") }) { Text(if (session.preferences.mode == "PAGE") "切换连续" else "切换翻页") }
                        IconButton(onClick = { sheet = "automatic" }) { Icon(Icons.Outlined.PlayCircleOutline, "自动阅读") }
                        if (session.text != null) {
                            IconButton(onClick = { sheet = "chapters" }) { Icon(Icons.Outlined.FormatListBulleted, "章节目录") }
                            IconButton(onClick = { sheet = "bookmarks" }) { Icon(Icons.Outlined.Bookmarks, "手动书签") }
                        } else {
                            if (session.preferences.mode == "PAGE") TextButton(onClick = { vm.direction(if (session.preferences.direction == "LTR") "RTL" else "LTR") }) { Text(if (session.preferences.direction == "LTR") "从左到右" else "从右到左") }
                            IconButton(onClick = { vm.seekComic(session.position.imageIndex - 1) }) { Icon(Icons.Outlined.SkipPrevious, "上一页") }
                            IconButton(onClick = { vm.seekComic(session.position.imageIndex + 1) }) { Icon(Icons.Outlined.SkipNext, "下一页") }
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (menu) 140.dp else 16.dp))
    }
    sheet?.let { active ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding()) {
                when (active) {
                    "automatic" -> AutoReadingSettings(settings, session.preferences.mode, vm::updateSettings) {
                        menu = false; sheet = null; automatic.start()
                    }
                    "settings" -> {
                        Text("阅读设置", style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                            ReadingSettings(settings, vm::updateSettings)
                            if (session.text != null) {
                                Spacer(Modifier.height(16.dp)); Text("TXT 编码 · 当前 ${session.text.encoding}")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("AUTO" to "自动", "UTF-8" to "UTF-8", "GB18030" to "GB18030", "UTF-16LE" to "UTF-16LE").forEach { (id, label) ->
                                    FilterChip(session.book.encoding == id, { textView?.flush(); sheet = null; vm.encoding(id) }, label = { Text(label) })
                                } }
                            }
                        }
                    }
                    "chapters" -> {
                        Text("章节目录", style = MaterialTheme.typography.titleLarge)
                        val chapters = session.text?.chapters.orEmpty()
                        if (chapters.isEmpty()) Text("未识别到章节标题，可以继续阅读全文。", Modifier.padding(vertical = 24.dp))
                        else LazyColumn(Modifier.heightIn(max = 520.dp)) { items(chapters, key = { it.offset }) { chapter ->
                            TextButton(onClick = { vm.seekText(chapter.offset); sheet = null; menu = false }, modifier = Modifier.fillMaxWidth()) { Text(chapter.title, Modifier.fillMaxWidth()) }
                        } }
                    }
                    "bookmarks" -> {
                        val bookmarks by remember(session.book.id) { vm.repository.dao.bookmarks(session.book.id) }.collectAsStateWithLifecycle(emptyList())
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("手动书签", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { textView?.flush(); vm.addBookmark() }) { Text("＋ 保存当前位置") } }
                        if (bookmarks.isEmpty()) Text("还没有手动书签。上次阅读位置会另外自动保存。", Modifier.padding(vertical = 24.dp))
                        else LazyColumn(Modifier.heightIn(max = 520.dp)) { items(bookmarks, key = { it.id }) { bookmark ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { vm.seekText(bookmark.offset); sheet = null; menu = false }.padding(vertical = 14.dp)) { Text(bookmark.chapter); Text(bookmark.snippet, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                                IconButton(onClick = { vm.deleteBookmark(bookmark.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除书签") }
                            }; HorizontalDivider()
                        } }
                    }
                    "jump" -> {
                        var value by remember { mutableFloatStateOf(if (session.text != null) session.position.progress else session.position.imageIndex.toFloat() / (session.comic!!.images.size - 1).coerceAtLeast(1)) }
                        Text("阅读进度", style = MaterialTheme.typography.titleLarge)
                        Text(if (session.text != null) "${(value * 100).toInt()}%" else "第 ${(value * (session.comic!!.images.size - 1)).toInt() + 1} 页", Modifier.padding(top = 20.dp))
                        Slider(value, { value = it })
                        Button(onClick = {
                            if (session.text != null) vm.seekText((value * (session.text.text.length - 1).coerceAtLeast(0)).toInt())
                            else vm.seekComic((value * (session.comic!!.images.size - 1)).toInt())
                            sheet = null; menu = false
                        }, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("跳转") }
                    }
                }
            }
        }
    }
}

private data class ComicFile(val page: ComicPage? = null, val size: ImageSize? = null, val error: String? = null) {
    val ratio: Float get() = size?.let { it.width.toFloat() / it.height } ?: .7f
}
private data class ComicViewport(val index: Int, val top: Int, val bottom: Int)

@Composable
private fun rememberComicFile(archive: ComicArchive, index: Int): State<ComicFile> {
    val cached = remember(archive, index) { archive.cachedPage(index) }
    return key(archive, index) { produceState(ComicFile(cached, cached?.size ?: archive.cachedSize(index)), archive, index) {
        try {
            val size = withContext(Dispatchers.IO) { archive.size(index) }
            if (value.page == null) value = ComicFile(size = size)
            value = ComicFile(withContext(Dispatchers.IO) { archive.page(index) }, size)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { value = value.copy(error = e.message ?: "图片读取失败") }
    } }
}

private suspend fun prefetchComic(archive: ComicArchive, visible: List<Int>, forward: Boolean = true) = withContext(Dispatchers.IO) {
    if (visible.isEmpty()) return@withContext
    val first = visible.min(); val last = visible.max()
    // Load three pages in the travel direction and one behind, including when scrolling back up.
    val nearby = (visible + (1..3).map { if (forward) last + it else first - it } + listOf(if (forward) first - 1 else last + 1)).filter { it in archive.images.indices }.distinct()
    for (index in nearby) { ensureActive(); runCatching { archive.page(index) } }
    // Dimensions are cheap header reads; stable item heights avoid re-layout as full images arrive.
    for (index in (first - if (forward) 2 else 8).coerceAtLeast(0)..(last + if (forward) 8 else 2).coerceAtMost(archive.images.lastIndex)) {
        ensureActive(); runCatching { archive.size(index) }
    }
}

@Composable
private fun ComicContent(vm: MainViewModel, session: ReaderSession, settings: ReaderSettings, automatic: AutoReadingState, menu: () -> Unit) {
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
            val index = vm.reader.value?.takeIf { it.position.sessionId == token }?.position?.imageIndex ?: archive.images.lastIndex
            if (index >= archive.images.lastIndex) false else { vm.seekComic(index + 1); true }
        }, finished = { vm.notice.value = "已到末尾，自动阅读已停止" }, flush = vm::flushPosition)
        LaunchedEffect(archive, session.position.imageIndex) { prefetchComic(archive, listOf(session.position.imageIndex)) }
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { ComicImageView(it) }, update = { view ->
                view.interactive = true; view.tapToTurn = true
                view.rtl = session.preferences.direction == "RTL"
                view.onMenu = menu
                view.onPage = { next -> vm.reader.value?.takeIf { it.position.sessionId == token && it.preferences.mode == "PAGE" }?.let { vm.seekComic(it.position.imageIndex + if (next) 1 else -1) } }
                view.onFocus = { ratio -> if (vm.reader.value?.position?.sessionId == token) vm.comicPosition(session.position.imageIndex, ratio, false) }
                view.onGestureEnd = { if (vm.reader.value?.position?.sessionId == token) vm.flushPosition() }
                view.onError = { vm.notice.value = it }
                image.page?.let { view.setPage(it) } ?: view.clearPage()
            }, modifier = Modifier.fillMaxSize())
            if (image.page == null) ComicPlaceholder(image.error, Modifier.fillMaxSize())
            Text("${session.position.imageIndex + 1} / ${archive.images.size}", Modifier.align(Alignment.BottomEnd).padding(16.dp), color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
        }
    } else {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = session.position.imageIndex)
        val zoom = remember(archive) { ComicCanvasState() }
        val sizes = remember(archive) { mutableStateMapOf<Int, Int>() }
        val imageViews = remember(archive) { mutableMapOf<Int, ComicImageView>() }
        val viewports = remember(archive) { mutableMapOf<Int, ComicViewport>() }
        var restored by remember { mutableStateOf(false) }
        AutoReadingEffect(automatic, "SCROLL", settings,
            ready = { restored && archive.cachedPage(state.firstVisibleItemIndex) != null },
            scroll = { distance ->
                if (state.canScrollForward) state.scrollBy(distance / zoom.scale)
                state.canScrollForward
            }, finished = { vm.notice.value = "已到末尾，自动阅读已停止" }, flush = {
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
        ComicZoomSurface(zoom, menu, overflow = { state.dispatchRawDelta(it) }) {
            LazyColumn(state = state, userScrollEnabled = !zoom.zoomed, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(archive.images, key = { _, it -> it.name }) { index, _ ->
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
                    } else ComicPlaceholder(image.error, itemModifier)
                }
            }
        }
    }
}

@Composable
private fun ComicPlaceholder(error: String?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { if (error != null) Text(error, Modifier.padding(24.dp), color = Color.LightGray) else CircularProgressIndicator() }
}
