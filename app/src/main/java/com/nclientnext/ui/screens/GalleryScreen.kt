package com.nclientnext.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.nclientnext.ui.components.GlassScaffold
import com.nclientnext.ui.components.glassBarColors
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nclientnext.data.SearchParams
import com.nclientnext.data.Tag
import com.nclientnext.data.TagStatus
import com.nclientnext.data.TagType
import com.nclientnext.data.api.Hosts
import com.nclientnext.data.db.DownloadState
import com.nclientnext.ui.GalleryViewModel
import com.nclientnext.ui.components.ErrorState
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.components.MirrorImage
import java.text.DateFormat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.nclientnext.ui.theme.glass
import com.nclientnext.ui.theme.glassRim
import java.util.Date

@Composable
fun GalleryScreen(id: Int) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val vm: GalleryViewModel = viewModel(key = "gallery-$id") { GalleryViewModel(c, id) }
    val ui by vm.ui.collectAsState()
    val localFav by vm.localFavorite.collectAsState()
    val history by vm.history.collectAsState()
    val download by vm.download.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var tagSheet by remember { mutableStateOf<Tag?>(null) }
    var rangeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    // Warm the first pages (or the resume page) while you look at the details, so Read opens instantly
    LaunchedEffect(ui.detail?.id, ui.offline) {
        val d = ui.detail ?: return@LaunchedEffect
        if (ui.offline) return@LaunchedEffect
        com.nclientnext.ui.components.GalleryPrefetcher(ctx, d.pages.map { it.url }, parallel = 4, window = 5)
            .also { it.focus = (history?.lastPage ?: 0).coerceIn(0, (d.pages.size - 1).coerceAtLeast(0)) }
            .start(this)
    }

    GlassScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(colors = glassBarColors(), 
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("#$id", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { share(ctx, ui.detail?.title(settings.titleType) ?: "", id) }) { Icon(Icons.Outlined.Share, "Share") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Open in browser") }, leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) },
                                onClick = { menu = false; ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Hosts.galleryUrl(id)))) })
                            DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menu = false; copy(ctx, Hosts.galleryUrl(id)) })
                            DropdownMenuItem(text = { Text("Copy title") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menu = false; copy(ctx, ui.detail?.title(settings.titleType) ?: "") })
                            DropdownMenuItem(text = { Text("Download page range…") }, leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                onClick = { menu = false; rangeDialog = true })
                            DropdownMenuItem(text = { Text("Refresh") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = { menu = false; vm.load(force = true) })
                        }
                    }
                },
            )
        },
    ) { pad ->
        val d = ui.detail
        when {
            d == null && ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            d == null -> ErrorState(ui.error ?: Exception("Unknown error"), onRetry = { vm.load(true) }, modifier = Modifier.padding(pad))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                    Box {
                        // Heavily blurred + darkened cover (works on every Android version) that fades into black,
                        // so the title and tags on top are always readable.
                        MirrorImage(d.cover, Modifier.fillMaxWidth().height(420.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.7f }
                            .drawWithContent {
                                drawContent()
                                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.5f), Color.Transparent)), blendMode = BlendMode.DstIn)
                            },
                            blur = 0.5f)
                        Column(Modifier.padding(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp)) {
                            Row {
                                MirrorImage(
                                    d.cover,
                                    Modifier.width(140.dp).aspectRatio(0.7f).glassRim(MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium)
                                        .clickable { nav.reader(id, 0) },
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.title(settings.titleType), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 6, overflow = TextOverflow.Ellipsis)
                                    if (d.titleJapanese.isNotBlank() && settings.titleType != com.nclientnext.data.TitleType.JAPANESE) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(d.titleJapanese, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        buildString {
                                            append("${d.pages.size} pages")
                                            if (d.favorites > 0) append(" · ♥ ${d.favorites}")
                                            if (d.uploadDate > 0) append("\n" + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.uploadDate)))
                                        },
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (ui.offline) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("Offline copy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            val h = history
                            val resume = h != null && h.lastPage > 0 && h.lastPage < d.pages.size - 1
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { nav.reader(id, if (resume) h!!.lastPage else 0) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (resume) "Continue p.${h!!.lastPage + 1}" else "Read")
                                }
                                FilledTonalIcon(if (localFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favorite", tint = if (localFav) Color(0xFFFF4F7A) else null) { vm.toggleLocalFavorite() }
                                if (c.repository.hasAccount) {
                                    FilledTonalIcon(if (ui.onlineFavorite) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue, "Online favorite", enabled = !ui.onlineBusy) { vm.toggleOnlineFavorite() }
                                }
                                val dl = download
                                FilledTonalIcon(
                                    if (dl?.state == DownloadState.DONE) Icons.Outlined.DownloadDone else Icons.Outlined.Download, "Download",
                                    enabled = dl == null || dl.state == DownloadState.FAILED || dl.state == DownloadState.PAUSED,
                                ) { vm.download() }
                                FilledTonalIcon(Icons.AutoMirrored.Outlined.Comment, "Comments") { nav.comments(id) }
                            }
                            download?.let { dl ->
                                if (dl.state != DownloadState.DONE) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
                                    Text(
                                        when (dl.state) {
                                            DownloadState.FAILED -> "Download failed: ${dl.error ?: ""}"
                                            DownloadState.PAUSED -> "Paused · ${dl.downloadedPages}/${dl.totalPages}"
                                            else -> "Downloading ${dl.downloadedPages}/${dl.totalPages}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            TagGroups(d.tags, ui.tagStatuses, onClick = { t -> nav.search(SearchParams(include = listOf(t))) }, onLongClick = { tagSheet = it })
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "pages-title") {
                    Text("Pages", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
                itemsIndexed(d.pages, key = { i, _ -> "p$i" }) { i, p ->
                    Box(Modifier.padding(horizontal = 4.dp)) {
                        MirrorImage(
                            p.thumbnail,
                            Modifier.fillMaxWidth().aspectRatio(if (p.width > 0 && p.height > 0) p.width / p.height.toFloat() else 0.7f)
                                .glassRim(MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small).clickable { nav.reader(id, i) },
                        )
                        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                            Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                if (d.related.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "related") {
                        Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                            Text("More like this", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(d.related, key = { it.id }) { g ->
                                    Column(Modifier.width(110.dp).clickable { nav.gallery(g.id) }) {
                                        MirrorImage(g.thumbnail, Modifier.fillMaxWidth().aspectRatio(0.7f).glassRim(MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small))
                                        Text(com.nclientnext.ui.components.discreet(g.title), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    tagSheet?.let { tag ->
        val status = ui.tagStatuses[tag.id] ?: TagStatus.DEFAULT
        ModalBottomSheet(onDismissRequest = { tagSheet = null }, scrimColor = Color.Black.copy(alpha = 0.7f)) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tag.name, style = MaterialTheme.typography.titleLarge)
                Text("${tag.type.label} · ${tag.count} galleries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                SheetAction("Search this tag") { tagSheet = null; nav.search(SearchParams(include = listOf(tag))) }
                SheetAction("Search excluding this tag") { tagSheet = null; nav.search(SearchParams(exclude = listOf(tag))) }
                SheetAction(if (status == TagStatus.ACCEPTED) "✓ Always included (tap to clear)" else "Always include in every search") {
                    vm.setTagStatus(tag, if (status == TagStatus.ACCEPTED) TagStatus.DEFAULT else TagStatus.ACCEPTED); tagSheet = null
                }
                SheetAction(if (status == TagStatus.AVOIDED) "✓ Avoided (tap to clear)" else "Avoid this tag everywhere") {
                    vm.setTagStatus(tag, if (status == TagStatus.AVOIDED) TagStatus.DEFAULT else TagStatus.AVOIDED); tagSheet = null
                }
                SheetAction("Copy name") { copy(ctx, tag.name); tagSheet = null }
            }
        }
    }

    if (rangeDialog) {
        val total = ui.detail?.pages?.size ?: 0
        var from by remember { mutableStateOf("1") }
        var to by remember { mutableStateOf(total.toString()) }
        AlertDialog(
            onDismissRequest = { rangeDialog = false },
            title = { Text("Download pages") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(from, { from = it.filter(Char::isDigit) }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it.filter(Char::isDigit) }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = (from.toIntOrNull() ?: 1).coerceIn(1, total) - 1
                    val b = (to.toIntOrNull() ?: total).coerceIn(1, total) - 1
                    vm.download(minOf(a, b)..maxOf(a, b)); rangeDialog = false
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { rangeDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp))
}

@Composable
private fun FilledTonalIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, desc, tint = tint ?: androidx.compose.material3.LocalContentColor.current)
    }
}

@Composable
fun TagGroups(tags: List<Tag>, statuses: Map<Int, TagStatus>, onClick: (Tag) -> Unit, onLongClick: (Tag) -> Unit) {
    val grouped = tags.groupBy { it.type }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TagType.displayOrder.forEach { type ->
            val list = grouped[type] ?: return@forEach
            Row {
                Text(type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp).padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    list.sortedByDescending { it.count }.forEach { t ->
                        val st = statuses[t.id] ?: TagStatus.DEFAULT
                        val bg = when (st) {
                            TagStatus.ACCEPTED -> Color.White.copy(alpha = 0.35f)
                            TagStatus.AVOIDED -> MaterialTheme.colorScheme.errorContainer
                            TagStatus.DEFAULT -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val pill = RoundedCornerShape(50)
                        Surface(
                            color = if (st == TagStatus.DEFAULT) Color.Transparent else bg.copy(alpha = 0.55f), shape = pill,
                            modifier = Modifier.glass(pill).combinedClickable(onClick = { onClick(t) }, onLongClick = { onLongClick(t) }),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name, style = MaterialTheme.typography.labelLarge)
                                if (t.count > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(compact(t.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fK".format(n / 1_000f)
    else -> n.toString()
}

fun share(ctx: Context, title: String, id: Int) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\n${Hosts.galleryUrl(id)}")
    }
    ctx.startActivity(Intent.createChooser(i, "Share"))
}

fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("text", text))
}
