package com.nclientnext.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.nclientnext.AppContainer
import com.nclientnext.data.AuthRequiredException
import com.nclientnext.data.CloudflareException
import com.nclientnext.data.GalleryItem
import com.nclientnext.data.SearchParams
import com.nclientnext.data.api.Hosts
import com.nclientnext.data.prefs.AvoidedMode
import androidx.compose.material3.Scaffold
import com.nclientnext.ui.theme.LocalGlass
import com.nclientnext.ui.theme.glassRim
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** App-wide navigation actions available to every screen. */
interface Navigator {
    fun gallery(id: Int)
    fun reader(id: Int, page: Int = -1)
    fun search(params: SearchParams)
    fun searchEditor(params: SearchParams = SearchParams())
    fun comments(id: Int)
    fun cloudflare()
    fun account()
    fun route(route: Any)
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

/** Privacy mode: blur all gallery covers/thumbnails (not reader pages). */
val LocalBlurCovers = staticCompositionLocalOf { false }

/** In discreet mode list titles are hidden too (the gallery page itself still shows it). */
@Composable
fun discreet(title: String): String = if (LocalBlurCovers.current) "••••••  hidden" else title

/** Space at the bottom taken by the floating nav pill + system nav bar. Lists add this as padding. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

/**
 * Scaffold for every screen: transparent so the aurora shows through, with a frosted
 * (backdrop-blurred) top bar that content scrolls underneath.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val haze = remember { HazeState() }
    val glass = LocalGlass.current
    val bottom = LocalBottomInset.current
    val cs = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.hazeEffect(haze, glass.barStyle)) {
                // Bars stay see-through even when content is scrolled under them
                topBar()
            }
        },
        snackbarHost = { Box(Modifier.padding(bottom = bottom)) { snackbarHost() } },
        floatingActionButton = { Box(Modifier.padding(bottom = bottom)) { floatingActionButton() } },
    ) { pad ->
        Box(Modifier.fillMaxSize().hazeSource(haze)) { com.nclientnext.ui.theme.AuroraBackground { content(pad) } }
    }
}

/** Padding for a list placed below other content: clears the floating nav pill. */
@Composable
fun bottomListPadding(horizontal: androidx.compose.ui.unit.Dp = 10.dp, top: androidx.compose.ui.unit.Dp = 10.dp): PaddingValues =
    PaddingValues(start = horizontal, end = horizontal, top = top, bottom = LocalBottomInset.current + 12.dp)

/**
 * Top bars are see-through (the blur behind them does the work). Set here per bar instead of
 * changing theme colours: menus share those colours and must stay solid to be readable.
 */
@Composable
fun glassBarColors() = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

/** Content padding for a list that scrolls under the glass top bar and above the nav pill. */
@Composable
fun listPadding(pad: PaddingValues, horizontal: androidx.compose.ui.unit.Dp = 10.dp, extraTop: androidx.compose.ui.unit.Dp = 10.dp): PaddingValues {
    val bottom = LocalBottomInset.current
    return PaddingValues(
        start = horizontal, end = horizontal,
        top = pad.calculateTopPadding() + extraTop,
        bottom = maxOf(bottom, pad.calculateBottomPadding()) + 12.dp,
    )
}

/** AsyncImage that rotates across nhentai's image mirrors when one fails. */
@Composable
fun MirrorImage(
    url: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    /** Heavy blur that works on all Android versions; value = darkening veil 0..1. */
    blur: Float? = null,
    /** Decode at full resolution (reader pages) instead of the view size. */
    original: Boolean = false,
    onSuccess: () -> Unit = {},
) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    val ctx = LocalPlatformContext.current
    val privacy = LocalBlurCovers.current && !original && url is String && "/galleries/" in url
    val blur = blur ?: if (privacy) 0.25f else null
    val model = remember(url, attempt, blur, original) {
        val data = if (url is String) Hosts.mirror(url, attempt) else url
        ImageRequest.Builder(ctx).data(data).crossfade(!original).apply {
            if (blur != null) transformations(BlurTransformation(darken = blur))
            if (original) size(coil3.size.Size.ORIGINAL)
        }.build()
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { if (url is String && attempt < 4) attempt++ },
        onSuccess = { onSuccess() },
    )
}

@Composable
fun GalleryCard(
    item: GalleryItem,
    avoided: Boolean,
    avoidedMode: AvoidedMode,
    favorite: Boolean = false,
    progress: Float? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.medium
    Card(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .glassRim(shape)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            val blur = avoided && avoidedMode == AvoidedMode.BLUR
            MirrorImage(
                url = item.thumbnail,
                modifier = Modifier.fillMaxSize(),
                blur = if (blur) 0.35f else null,
            )
            if (blur) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = Color.White)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp)
            ) {
                Text(
                    discreet(item.title),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.language?.let { if (it.emoji.isNotEmpty()) Badge(it.emoji) }
                if (item.pages > 0) Badge("${item.pages}p")
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Favorite, null, tint = Color(0xFFFF4F7A),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
                )
            }
            if (progress != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f), border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f))) {
        Text(
            text, color = Color.White, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

fun gridCells(columns: Int): GridCells = if (columns <= 0) GridCells.Adaptive(150.dp) else GridCells.Fixed(columns)

/**
 * Grid of galleries with infinite scrolling.
 * [header] items span the full width.
 */
@Composable
fun GalleryGrid(
    items: List<GalleryItem>,
    columns: Int,
    avoidedIds: Set<Int>,
    avoidedMode: AvoidedMode,
    favoriteIds: Set<Int> = emptySet(),
    loading: Boolean,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(10.dp),
    onLongClick: ((GalleryItem) -> Unit)? = null,
    header: LazyGridScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    val visible = remember(items, avoidedIds, avoidedMode) {
        if (avoidedMode == AvoidedMode.HIDE) items.filter { g -> g.tagIds.none { it in avoidedIds } } else items
    }
    val shouldLoad by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(state, endReached) {
        snapshotFlow { shouldLoad }.distinctUntilChanged().filter { it }.collect { if (!endReached) onLoadMore() }
    }
    LazyVerticalGrid(
        columns = gridCells(columns),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header()
        items(visible, key = { it.id }) { g ->
            GalleryCard(
                item = g,
                avoided = g.tagIds.any { it in avoidedIds },
                avoidedMode = avoidedMode,
                favorite = g.id in favoriteIds,
                onClick = { nav.gallery(g.id) },
                onLongClick = onLongClick?.let { { it(g) } },
            )
        }
        if (loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
fun ErrorState(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val (icon, title, body) = when (error) {
            is CloudflareException -> Triple(Icons.Outlined.Security, "Verification needed", "The site is asking for a browser check. Solve it once and the app will reuse it.")
            is AuthRequiredException -> Triple(Icons.Outlined.Security, "Sign in required", "Add your API key in Account to use online favorites.")
            else -> Triple(Icons.Outlined.CloudOff, "Couldn't load", error.message ?: error.javaClass.simpleName)
        }
        Icon(icon, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (error) {
                is CloudflareException -> Button(onClick = { nav.cloudflare() }) { Text("Open verification") }
                is AuthRequiredException -> Button(onClick = { nav.account() }) { Text("Add API key") }
                else -> {}
            }
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { content() }
}
