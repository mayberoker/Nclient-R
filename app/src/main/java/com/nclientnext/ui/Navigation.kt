package com.nclientnext.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nclientnext.AppContainer
import com.nclientnext.data.SearchParams
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.components.Navigator
import com.nclientnext.ui.screens.AccountScreen
import com.nclientnext.ui.screens.BookmarksScreen
import com.nclientnext.ui.screens.BrowseScreen
import com.nclientnext.ui.screens.CloudflareScreen
import com.nclientnext.ui.screens.CommentsScreen
import com.nclientnext.ui.screens.DownloadsScreen
import com.nclientnext.ui.screens.FavoritesScreen
import com.nclientnext.ui.screens.GalleryScreen
import com.nclientnext.ui.screens.HistoryScreen
import com.nclientnext.ui.screens.MoreScreen
import com.nclientnext.ui.screens.ReaderScreen
import com.nclientnext.ui.screens.SearchEditorScreen
import com.nclientnext.ui.screens.SettingsScreen
import com.nclientnext.ui.screens.TagsScreen
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nclientnext.ui.components.LocalBottomInset
import com.nclientnext.ui.theme.LocalGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object Routes {
    @Serializable data object Home
    @Serializable data object Favorites
    @Serializable data object Downloads
    @Serializable data object History
    @Serializable data object More
    @Serializable data object Bookmarks
    @Serializable data object Tags
    @Serializable data object Settings
    @Serializable data object Account
    @Serializable data object Cloudflare
    @Serializable data class Gallery(val id: Int)
    @Serializable data class Reader(val id: Int, val page: Int = -1)
    @Serializable data class Comments(val id: Int)
    @Serializable data class Search(val params: String)
    @Serializable data class SearchEditor(val params: String)
}

private data class TopLevel(val route: Any, val klass: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.Home, Routes.Home::class, "Browse", Icons.Outlined.Explore, Icons.Filled.Explore),
    TopLevel(Routes.Favorites, Routes.Favorites::class, "Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    TopLevel(Routes.Downloads, Routes.Downloads::class, "Downloads", Icons.Outlined.Download, Icons.Filled.Download),
    TopLevel(Routes.History, Routes.History::class, "History", Icons.Outlined.History, Icons.Filled.History),
    TopLevel(Routes.More, Routes.More::class, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

private class NavigatorImpl(private val nav: NavHostController, private val c: AppContainer) : Navigator {
    override fun gallery(id: Int) = nav.navigate(Routes.Gallery(id))
    override fun reader(id: Int, page: Int) = nav.navigate(Routes.Reader(id, page))
    override fun search(params: SearchParams) = nav.navigate(Routes.Search(c.repository.encodeParams(params))) {
        // Re-running a search from the editor replaces the editor
        popUpTo<Routes.SearchEditor> { inclusive = true }
    }
    override fun searchEditor(params: SearchParams) = nav.navigate(Routes.SearchEditor(c.repository.encodeParams(params)))
    override fun comments(id: Int) = nav.navigate(Routes.Comments(id))
    override fun cloudflare() = nav.navigate(Routes.Cloudflare) { launchSingleTop = true }
    override fun account() = nav.navigate(Routes.Account) { launchSingleTop = true }
    override fun route(route: Any) = nav.navigate(route) { launchSingleTop = true }
    override fun back() { if (!nav.popBackStack()) Unit }
}

@Composable
fun AppNavigation(container: AppContainer, deepLink: Any?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val navigator = remember(nav) { NavigatorImpl(nav, container) }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = topLevel.any { t -> dest?.hasRoute(t.klass) == true }

    LaunchedEffect(deepLink) {
        if (deepLink != null) { nav.navigate(deepLink); onDeepLinkHandled() }
    }

    val haze = remember { HazeState() }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Floating pill (68dp) + its 12dp margin + system nav bar; screens pad their lists by this.
    val bottomInset = if (showBar) navBarInset + 68.dp + 12.dp else navBarInset

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalContainer provides container,
        LocalBottomInset provides bottomInset,
        com.nclientnext.ui.components.LocalBlurCovers provides container.settings.flow.collectAsState().value.blurCovers,
    ) {
        UpdateDialog(container)
        // NOTE: no Scaffold / consumeWindowInsets here. Consuming the status-bar inset at this level
        // made every screen's top bar draw underneath the clock and battery icons.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.Home,
                    enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 12 } },
                ) {
                    composable<Routes.Home> { BrowseScreen(null) }
                    composable<Routes.Favorites> { FavoritesScreen() }
                    composable<Routes.Downloads> { DownloadsScreen() }
                    composable<Routes.History> { HistoryScreen() }
                    composable<Routes.More> { MoreScreen() }
                    composable<Routes.Bookmarks> { BookmarksScreen() }
                    composable<Routes.Tags> { TagsScreen() }
                    composable<Routes.Settings> { SettingsScreen() }
                    composable<Routes.Account> { AccountScreen() }
                    composable<Routes.Cloudflare> { CloudflareScreen() }
                    composable<Routes.Gallery> { GalleryScreen(it.toRoute<Routes.Gallery>().id) }
                    composable<Routes.Reader> {
                        val r = it.toRoute<Routes.Reader>()
                        ReaderScreen(r.id, r.page)
                    }
                    composable<Routes.Comments> { CommentsScreen(it.toRoute<Routes.Comments>().id) }
                    composable<Routes.Search> {
                        val raw = it.toRoute<Routes.Search>().params
                        BrowseScreen(container.repository.decodeParams(raw), paramsKey = raw)
                    }
                    composable<Routes.SearchEditor> {
                        SearchEditorScreen(container.repository.decodeParams(it.toRoute<Routes.SearchEditor>().params))
                    }
                }
            }
            AnimatedVisibility(
                showBar,
                enter = slideInVertically { it * 2 } + fadeIn(),
                exit = slideOutVertically { it * 2 } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassNavPill(
                    haze = haze,
                    selected = { t -> dest?.hierarchy?.any { it.hasRoute(t.klass) } == true },
                    onSelect = { t ->
                        nav.navigate(t.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GlassNavPill(haze: HazeState, selected: (TopLevel) -> Boolean, onSelect: (TopLevel) -> Unit) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(34.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            .hazeEffect(haze, glass.barStyle)
            .background(glass.sheen, shape)
            .border(1.dp, glass.rim, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        topLevel.forEach { t ->
            val sel = selected(t)
            val bg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary.copy(alpha = if (glass.dark) 0.22f else 0.16f) else Color.Transparent,
                label = "navbg",
            )
            val fg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "navfg",
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(bg)
                    .then(if (sel) Modifier.border(1.dp, glass.rim, RoundedCornerShape(28.dp)) else Modifier)
                    .clickable { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (sel) t.selectedIcon else t.icon, t.label, tint = fg, modifier = Modifier.size(22.dp))
                Text(t.label, color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}


/** Tells the user when a newer Nclient-R release is on GitHub (checked once per launch). */
@Composable
private fun UpdateDialog(container: AppContainer) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var update by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.nclientnext.data.AppUpdate?>(null) }
    LaunchedEffect(Unit) {
        update = runCatching { com.nclientnext.data.Updates.check(container.api) }.getOrNull()
    }
    val u = update ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { update = null },
        title = { Text("Nclient-R ${u.version} is here") },
        text = {
            Text(
                u.notes.lines().filter { it.isNotBlank() }.take(10).joinToString("\n").ifBlank { "A new version is ready to download." },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u.url)))
                update = null
            }) { Text("Download") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { update = null }) { Text("Later") } },
    )
}
