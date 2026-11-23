package com.nclientnext.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import com.nclientnext.ui.components.GlassScaffold
import com.nclientnext.ui.components.glassBarColors
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nclientnext.data.db.DownloadState
import com.nclientnext.ui.CommentsViewModel
import com.nclientnext.ui.DownloadsViewModel
import com.nclientnext.ui.FavoritesViewModel
import com.nclientnext.ui.HistoryViewModel
import com.nclientnext.ui.components.EmptyState
import com.nclientnext.ui.components.ErrorState
import com.nclientnext.ui.components.GalleryGrid
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.components.MirrorImage
import com.nclientnext.ui.components.bottomListPadding
import com.nclientnext.ui.components.listPadding
import com.nclientnext.ui.theme.glassRim
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    SearchPill(value, onChange, placeholder, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
fun FavoritesScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: FavoritesViewModel = viewModel { FavoritesViewModel(c) }
    val settings by c.settings.flow.collectAsState()
    val local by vm.local.collectAsState()
    val online by vm.online.state.collectAsState()
    val avoided by vm.avoided.collectAsState()
    val query by vm.query.collectAsState()
    val importing by vm.importProgress.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(tab) { if (tab == 1 && online.page == 0 && c.repository.hasAccount) vm.online.refresh() }

    GlassScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(colors = glassBarColors(), 
                title = { Text("Favorites") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val id = if (tab == 0) vm.randomLocal() else vm.randomOnline()
                            if (id != null) nav.gallery(id) else snackbar.showSnackbar("No favorites yet")
                        }
                    }) { Icon(Icons.Outlined.Casino, "Random favorite") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Import online favorites to local") },
                                leadingIcon = { Icon(Icons.Outlined.CloudDownload, null) },
                                enabled = c.repository.hasAccount && importing == null,
                                onClick = { menu = false; vm.importOnline() },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("On device (${local.size})") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Online") })
            }
            importing?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Importing… $it", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
            }
            SearchField(query, { vm.query.value = it; if (tab == 1) vm.online.refresh() }, "Filter favorites")
            if (tab == 0) {
                if (local.isEmpty()) EmptyState(Icons.Outlined.FavoriteBorder, "No favorites yet", "Tap the heart on a gallery, or long-press a cover in any list.")
                else GalleryGrid(
                    items = local, columns = settings.gridColumns, avoidedIds = avoided, avoidedMode = settings.avoidedMode,
                    loading = false, endReached = true, onLoadMore = {}, contentPadding = bottomListPadding(),
                )
            } else {
                when {
                    !c.repository.hasAccount -> EmptyState(Icons.Outlined.FavoriteBorder, "Not connected", "Add your nhentai API key to sync online favorites.") {
                        OutlinedButton(onClick = { nav.account() }) { Text("Add API key") }
                    }
                    online.items.isEmpty() && online.error != null -> ErrorState(online.error!!, onRetry = { vm.online.refresh() })
                    online.items.isEmpty() && online.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    online.items.isEmpty() && online.page > 0 -> EmptyState(Icons.Outlined.FavoriteBorder, "No online favorites", "")
                    else -> GalleryGrid(
                        items = online.items, columns = settings.gridColumns, avoidedIds = avoided, avoidedMode = settings.avoidedMode,
                        loading = online.loading, endReached = online.endReached, onLoadMore = vm.online::loadMore,
                        contentPadding = bottomListPadding(),
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: DownloadsViewModel = viewModel { DownloadsViewModel(c) }
    val items by vm.items.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var exportTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }

    val cbzLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.comicbook+zip")) { uri ->
        val t = exportTarget; if (uri != null && t != null) vm.exportCbz(t.first, uri)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val t = exportTarget; if (uri != null && t != null) vm.exportPdf(t.first, uri)
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it) } }

    GlassScaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { TopAppBar(colors = glassBarColors(), title = { Text("Downloads") }) }) { pad ->
        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.DownloadForOffline, "No downloads", "Downloaded galleries can be read offline and exported as CBZ or PDF.", Modifier.padding(pad))
            return@GlassScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(pad, 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().glassRim().clip(MaterialTheme.shapes.medium).clickable { nav.gallery(d.id) }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MirrorImage(d.thumbnail, Modifier.size(64.dp, 90.dp).glassRim(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(com.nclientnext.ui.components.discreet(d.title), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            val label = when (d.state) {
                                DownloadState.DONE -> "${d.totalPages} pages · Ready offline"
                                DownloadState.PAUSED -> "Paused · ${d.downloadedPages}/${d.totalPages}"
                                DownloadState.FAILED -> "Failed · ${d.error ?: "error"}"
                                DownloadState.QUEUED -> "Queued · ${d.downloadedPages}/${d.totalPages}"
                                else -> "Downloading · ${d.downloadedPages}/${d.totalPages}"
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            if (d.state != DownloadState.DONE) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        when (d.state) {
                            DownloadState.RUNNING, DownloadState.QUEUED -> IconButton(onClick = { vm.pause(d.id) }) { Icon(Icons.Outlined.Pause, "Pause") }
                            DownloadState.PAUSED, DownloadState.FAILED -> IconButton(onClick = { vm.resume(d.id) }) { Icon(Icons.Outlined.PlayArrow, "Resume") }
                            else -> IconButton(onClick = { nav.reader(d.id) }) { Icon(Icons.Outlined.PlayArrow, "Read") }
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                val safe = d.title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
                                DropdownMenuItem(text = { Text("Export as CBZ") }, enabled = d.downloadedPages > 0, onClick = {
                                    menu = false; exportTarget = d.id to safe; cbzLauncher.launch("$safe.cbz")
                                })
                                DropdownMenuItem(text = { Text("Export as PDF") }, enabled = d.downloadedPages > 0, onClick = {
                                    menu = false; exportTarget = d.id to safe; pdfLauncher.launch("$safe.pdf")
                                })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = {
                                    menu = false; confirmDelete = d.id
                                })
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete download?") },
            text = { Text("The downloaded pages will be removed from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun HistoryScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(c) }
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val settings by c.settings.flow.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    GlassScaffold(topBar = {
        TopAppBar(colors = glassBarColors(), title = { Text("History") }, actions = {
            IconButton(onClick = { confirm = true }, enabled = items.isNotEmpty()) { Icon(Icons.Outlined.DeleteSweep, "Clear history") }
        })
    }) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            if (settings.incognito) {
                Text("Incognito is on — new reads aren't recorded.", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            SearchField(query, { vm.query.value = it }, "Search history")
            if (items.isEmpty()) {
                EmptyState(Icons.Outlined.History, "Nothing here yet", "Galleries you read show up here with your last page.")
                return@Column
            }
            LazyColumn(contentPadding = bottomListPadding(horizontal = 0.dp, top = 4.dp)) {
                items(items, key = { it.id }) { h ->
                    ListItem(
                        leadingContent = { MirrorImage(h.thumbnail, Modifier.size(52.dp, 74.dp).glassRim(RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) },
                        headlineContent = { Text(com.nclientnext.ui.components.discreet(h.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text("Page ${h.lastPage + 1} of ${h.totalPages} · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.updatedAt)))
                                LinearProgressIndicator(
                                    progress = { if (h.totalPages > 0) (h.lastPage + 1) / h.totalPages.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { nav.reader(h.id, h.lastPage) }) { Icon(Icons.Outlined.PlayArrow, "Continue") }
                                IconButton(onClick = { vm.delete(h.id) }) { Icon(Icons.Outlined.Delete, "Remove") }
                            }
                        },
                        modifier = Modifier.clickable { nav.gallery(h.id) },
                    )
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Clear history?") },
        text = { Text("Your reading history and resume positions will be removed.") },
        confirmButton = { TextButton(onClick = { vm.clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
fun BookmarksScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val items by c.db.bookmarks().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var rename by remember { mutableStateOf<Pair<Long, String>?>(null) }
    GlassScaffold(topBar = {
        TopAppBar(colors = glassBarColors(), title = { Text("Saved searches") }, navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.Bookmarks, "No saved searches", "Open any search result and tap the bookmark icon to save it here.", Modifier.padding(pad))
            return@GlassScaffold
        }
        LazyColumn(Modifier.padding(pad)) {
            items(items, key = { it.uid }) { b ->
                val p = remember(b.paramsJson) { c.repository.decodeParams(b.paramsJson) }
                ListItem(
                    headlineContent = { Text(b.title) },
                    supportingContent = {
                        Text(buildString {
                            append(p.describe())
                            p.sort?.let { append(" · ${it.label}") }
                            p.language?.let { append(" · ${it.label}") }
                        }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { rename = b.uid to b.title }) { Icon(Icons.Outlined.Edit, "Rename") }
                            IconButton(onClick = { scope.launch { c.db.bookmarks().delete(b.uid) } }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    },
                    modifier = Modifier.clickable { nav.search(p) },
                )
            }
        }
    }
    rename?.let { (uid, title) ->
        var t by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { scope.launch { c.db.bookmarks().rename(uid, t) }; rename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CommentsScreen(id: Int) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: CommentsViewModel = viewModel(key = "comments-$id") { CommentsViewModel(c, id) }
    val comments by vm.comments.collectAsState()
    val error by vm.error.collectAsState()
    GlassScaffold(topBar = {
        TopAppBar(colors = glassBarColors(), 
            title = { Text("Comments" + (comments?.let { " (${it.size})" } ?: "")) },
            navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        val list = comments
        when {
            error != null -> ErrorState(error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(Icons.Outlined.ChatBubbleOutline, "No comments", "Be the first on the website.", Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { cm ->
                    Row {
                        MirrorImage(cm.avatar, Modifier.size(40.dp).clip(CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cm.username, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                if (cm.date > 0) Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(cm.date)),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(cm.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
