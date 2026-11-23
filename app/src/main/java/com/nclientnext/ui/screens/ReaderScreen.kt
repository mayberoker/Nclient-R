package com.nclientnext.ui.screens

import android.app.Activity
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.nclientnext.MainActivity
import com.nclientnext.data.prefs.ReaderMode
import com.nclientnext.ui.ReaderPage
import com.nclientnext.ui.ReaderViewModel
import com.nclientnext.ui.components.ErrorState
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.components.MirrorImage
import com.nclientnext.ui.components.GalleryPrefetcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(id: Int, page: Int) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: ReaderViewModel = viewModel(key = "reader-$id-$page") { ReaderViewModel(c, id, page) }
    val ui by vm.ui.collectAsState()
    val settings by c.settings.flow.collectAsState()
    var overlay by remember { mutableStateOf(false) }
    var modeOverride by remember { mutableStateOf<ReaderMode?>(null) }
    val mode = modeOverride ?: settings.readerMode

    ImmersiveMode(hidden = !overlay)
    if (settings.keepScreenOn) KeepScreenOn()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ui.error != null -> ErrorState(ui.error!!, onRetry = vm::load)
            ui.pages.isEmpty() -> Text("No pages", color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> {
                var current by remember { mutableIntStateOf(ui.startPage) }
                var jumpRequest by remember { mutableStateOf<Int?>(null) }
                val scope = rememberCoroutineScope()
                val ctx = LocalContext.current

                // Full-resolution prefetch of the whole gallery (or a window), nearest pages first.
                val prefetcher = remember(ui.pages) {
                    GalleryPrefetcher(
                        ctx,
                        ui.pages.map { (it.model as? String).orEmpty() },
                        parallel = settings.parallelDownloads.coerceIn(4, 8),
                        window = if (settings.preloadAll) null else settings.preloadPages,
                    ).also { it.focus = ui.startPage }
                }
                LaunchedEffect(prefetcher) { prefetcher.start(this) }
                LaunchedEffect(current) {
                    prefetcher.focus = current
                    if (!settings.preloadAll) prefetcher.poke(scope)
                    delay(500)
                    vm.onPage(current)
                }

                val step: (Int) -> Unit = { delta -> jumpRequest = (current + delta).coerceIn(0, ui.pages.lastIndex) }
                VolumeKeys(enabled = settings.volumeKeys) { forward -> step(if (forward) 1 else -1) }

                val onTap: (Offset, IntSize) -> Unit = { pos, size ->
                    val third = size.width / 3f
                    val rtl = mode == ReaderMode.RTL
                    when {
                        !settings.tapZones || mode == ReaderMode.VERTICAL || mode == ReaderMode.WEBTOON -> overlay = !overlay
                        pos.x < third -> { step(if (rtl) 1 else -1); overlay = false }
                        pos.x > third * 2 -> { step(if (rtl) -1 else 1); overlay = false }
                        else -> overlay = !overlay
                    }
                }

                when (mode) {
                    ReaderMode.LTR, ReaderMode.RTL -> PagedReader(ui.pages, ui.startPage, rtl = mode == ReaderMode.RTL, jumpRequest = jumpRequest,
                        onJumpHandled = { jumpRequest = null }, onPage = { current = it }, onTap = onTap)
                    ReaderMode.VERTICAL, ReaderMode.WEBTOON -> ScrollReader(ui.pages, current, gap = mode == ReaderMode.VERTICAL, jumpRequest = jumpRequest,
                        onJumpHandled = { jumpRequest = null }, onPage = { current = it }, onTap = onTap)
                }

                AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                    Row(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).statusBarsPadding().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                        Text(ui.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        var modeMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { modeMenu = true }) { Icon(Icons.Outlined.ViewCarousel, "Reading mode", tint = Color.White) }
                            DropdownMenu(modeMenu, { modeMenu = false }) {
                                ReaderMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label) },
                                        leadingIcon = { if (m == mode) Icon(Icons.Filled.Check, null) },
                                        onClick = {
                                            modeMenu = false; modeOverride = m
                                            scope.launch { c.settings.setReaderMode(m) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("${current + 1} / ${ui.pages.size}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                        var sliderValue by remember(current) { mutableFloatStateOf(current.toFloat()) }
                        CompositionLocalProvider(LocalLayoutDirection provides if (mode == ReaderMode.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = { jumpRequest = sliderValue.toInt() },
                                valueRange = 0f..ui.pages.lastIndex.coerceAtLeast(1).toFloat(),
                                steps = (ui.pages.size - 2).coerceIn(0, 400),
                            )
                        }
                    }
                }
                if (!overlay) {
                    Text(
                        "${current + 1}/${ui.pages.size}",
                        color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedReader(
    pages: List<ReaderPage>, start: Int, rtl: Boolean, jumpRequest: Int?, onJumpHandled: () -> Unit,
    onPage: (Int) -> Unit, onTap: (Offset, IntSize) -> Unit,
) {
    val pager = rememberPagerState(initialPage = start) { pages.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager) { androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect(onPage) }
    LaunchedEffect(jumpRequest) {
        jumpRequest?.let {
            if (kotlin.math.abs(it - pager.currentPage) <= 1) pager.animateScrollToPage(it) else pager.scrollToPage(it)
            onJumpHandled()
        }
    }
    HorizontalPager(
        state = pager,
        reverseLayout = rtl,
        // Keep neighbours decoded in memory so a swipe shows the page instantly
        beyondViewportPageCount = 2,
        userScrollEnabled = !zoomed,
        key = { pages[it].index },
        modifier = Modifier.fillMaxSize(),
    ) { i ->
        ZoomableBox(
            onTap = onTap,
            onZoomChanged = { if (pager.currentPage == i) zoomed = it },
            resetKey = pager.currentPage,
            modifier = Modifier.fillMaxSize(),
        ) {
            PageImage(pages[i], Modifier.fillMaxSize(), ContentScale.Fit)
        }
    }
}

@Composable
private fun ScrollReader(
    pages: List<ReaderPage>, start: Int, gap: Boolean, jumpRequest: Int?, onJumpHandled: () -> Unit,
    onPage: (Int) -> Unit, onTap: (Offset, IntSize) -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val first by remember { derivedStateOf { list.firstVisibleItemIndex } }
    LaunchedEffect(first) { onPage(first) }
    LaunchedEffect(jumpRequest) {
        jumpRequest?.let {
            if (kotlin.math.abs(it - first) <= 1) list.animateScrollToItem(it) else list.scrollToItem(it)
            onJumpHandled()
        }
    }
    ZoomableBox(onTap = onTap, onZoomChanged = {}, verticalPassThrough = true, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (gap) 6.dp else 0.dp),
            contentPadding = PaddingValues(vertical = if (gap) 8.dp else 0.dp),
        ) {
            itemsIndexed(pages, key = { _, p -> p.index }) { _, p ->
                val ratio = if (p.width > 0 && p.height > 0) p.width / p.height.toFloat() else null
                PageImage(
                    p,
                    if (ratio != null) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    ContentScale.FillWidth,
                )
            }
        }
    }
}

@Composable
private fun PageImage(page: ReaderPage, modifier: Modifier, scale: ContentScale) {
    var loaded by remember(page.model) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        MirrorImage(page.model, Modifier.fillMaxSize(), contentScale = scale, original = true, onSuccess = { loaded = true })
        if (!loaded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.6f))
                Text("${page.index + 1}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * Pinch-to-zoom + double-tap zoom container.
 * When [verticalPassThrough] is true, single-finger vertical drags are left for the child (scrolling list).
 */
@Composable
fun ZoomableBox(
    onTap: (Offset, IntSize) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    verticalPassThrough: Boolean = false,
    resetKey: Any? = null,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1) / 2f
        val maxY = if (verticalPassThrough) 0f else size.height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(resetKey) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) }

    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap(it, size) },
                    onDoubleTap = { tap ->
                        if (scale > 1.05f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) }
                        else {
                            val s = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = s
                            offset = clamp((center - tap) * (s - 1), s)
                            onZoomChanged(true)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val panUse = if (verticalPassThrough && !multi) Offset(pan.x, 0f) else pan
                            offset = clamp(offset + panUse, newScale)
                            if (newScale != scale) { scale = newScale; onZoomChanged(scale > 1.01f) }
                            if (multi || !verticalPassThrough) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.01f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) }
                }
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    ) { content() }
}

@Composable
private fun ImmersiveMode(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) ctrl.hide(WindowInsetsCompat.Type.systemBars()) else ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun VolumeKeys(enabled: Boolean, onStep: (forward: Boolean) -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(enabled) {
        val act = ctx as? MainActivity
        if (enabled && act != null) {
            act.keyHandler = { code ->
                when (code) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> { onStep(true); true }
                    KeyEvent.KEYCODE_VOLUME_UP -> { onStep(false); true }
                    else -> false
                }
            }
        }
        onDispose { act?.keyHandler = null }
    }
}
