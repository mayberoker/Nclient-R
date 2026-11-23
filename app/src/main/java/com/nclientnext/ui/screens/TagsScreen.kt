package com.nclientnext.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nclientnext.data.SearchParams
import com.nclientnext.data.TagStatus
import com.nclientnext.data.TagType
import com.nclientnext.data.db.TagEntity
import com.nclientnext.ui.TagsViewModel
import com.nclientnext.ui.components.EmptyState
import com.nclientnext.ui.components.GlassScaffold
import com.nclientnext.ui.components.glassBarColors
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.components.listPadding
import com.nclientnext.ui.theme.LocalGlass
import com.nclientnext.ui.theme.glass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Included = white (glass), excluded = red: the only accent colour in Nclient-R. */
private val Include = Color.White

private val tagTabs = listOf(
    TagType.TAG, TagType.ARTIST, TagType.CHARACTER, TagType.PARODY,
    TagType.GROUP, TagType.LANGUAGE, TagType.CATEGORY,
)

/**
 * Every tag nhentai has, grouped by type. Each one can be set to
 * Include (only show galleries with it), Neutral, or Exclude (hide galleries with it).
 */
@Composable
fun TagsScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: TagsViewModel = viewModel { TagsViewModel(c) }
    val type by vm.type.collectAsState()
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val status by vm.statusFilter.collectAsState()
    val alpha by vm.alphabetical.collectAsState()
    val accepted by vm.accepted.collectAsState()
    val avoided by vm.avoided.collectAsState()
    val localCount by vm.localCount.collectAsState()
    val totals by vm.remoteTotals.collectAsState()
    val sync by vm.sync.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmReset by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(type, status, query, alpha) { list.scrollToItem(0) }
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > list.layoutInfo.totalItemsCount - 30 } }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadMore() } }

    GlassScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(colors = glassBarColors(), 
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Tags")
                        Text(
                            if (accepted.isEmpty() && avoided.isEmpty()) "Tap a tag to include or exclude it"
                            else "${accepted.size} included · ${avoided.size} excluded",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.alphabetical.value = !alpha }) {
                        Icon(if (alpha) Icons.Outlined.TrendingUp else Icons.Outlined.SortByAlpha, if (alpha) "Sort by popularity" else "Sort A–Z")
                    }
                    TextButton(onClick = { confirmReset = true }, enabled = accepted.isNotEmpty() || avoided.isNotEmpty()) { Text("Reset") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            state = list,
            contentPadding = listPadding(pad, horizontal = 12.dp, extraTop = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchPill(query, { vm.query.value = it }, "Search ${if (status >= 0) "your filters" else type.label.lowercase()}")
                    // Status filter
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip("All", status == -1, null) { vm.statusFilter.value = -1 }
                        StatusChip("Included ${accepted.size}", status == TagStatus.ACCEPTED.ordinal, Include) { vm.statusFilter.value = TagStatus.ACCEPTED.ordinal }
                        StatusChip("Excluded ${avoided.size}", status == TagStatus.AVOIDED.ordinal, MaterialTheme.colorScheme.error) { vm.statusFilter.value = TagStatus.AVOIDED.ordinal }
                    }
                    if (status < 0) {
                        // Type tabs
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tagTabs.forEach { t -> StatusChip(t.label, t == type, MaterialTheme.colorScheme.primary) { vm.type.value = t } }
                        }
                        SyncBanner(
                            type = type, local = localCount, total = totals[type],
                            progress = sync[type]?.let { it.done / it.total.coerceAtLeast(1).toFloat() },
                            onDownload = { vm.download() },
                        )
                    }
                    Text(
                        "Included tags are required in every search. Excluded tags hide galleries everywhere.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.height(320.dp)) {
                        if (sync[type] != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else EmptyState(
                            Icons.Outlined.Sell,
                            if (status >= 0) "Nothing here yet" else "No tags found",
                            if (status >= 0) "Switch to All and tap a tag to include or exclude it." else "Try a different search or download the full list.",
                        )
                    }
                }
            }
            items(items, key = { it.id }) { t ->
                TagRow(
                    t,
                    showType = status >= 0,
                    onSet = { st -> vm.set(t.toTag(), st) },
                    onSearch = { nav.search(SearchParams(include = listOf(t.toTag()))) },
                )
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset all tags?") },
        text = { Text("Every tag goes back to neutral (${accepted.size} included, ${avoided.size} excluded).") },
        confirmButton = { TextButton(onClick = { vm.clearAll(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SyncBanner(type: TagType, local: Int, total: Int?, progress: Float?, onDownload: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        progress != null -> "Downloading ${type.label.lowercase()}… ${(progress * 100).toInt()}%"
                        total == null -> "%,d ${type.label.lowercase()} on device".format(local)
                        local >= total * 0.97 -> "All %,d ${type.label.lowercase()}".format(total)
                        else -> "%,d of %,d ${type.label.lowercase()}".format(local, total)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (progress == null && total != null && local < total * 0.97) {
                    Text("Download the full list to see every one", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (progress == null && (total == null || local < total * 0.97)) {
                TextButton(onClick = onDownload) {
                    Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Get all")
                }
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun TagRow(t: TagEntity, showType: Boolean, onSet: (TagStatus) -> Unit, onSearch: () -> Unit) {
    val st = t.tagStatus
    val accent by animateColorAsState(
        when (st) {
            TagStatus.ACCEPTED -> Include.copy(alpha = 0.12f)
            TagStatus.AVOIDED -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
            TagStatus.DEFAULT -> Color.Transparent
        }, label = "tagaccent",
    )
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .glass(shape)
            .background(accent, shape)
            // Tap the row to cycle: neutral → include → exclude → neutral
            .clickable {
                onSet(
                    when (st) {
                        TagStatus.DEFAULT -> TagStatus.ACCEPTED
                        TagStatus.ACCEPTED -> TagStatus.AVOIDED
                        TagStatus.AVOIDED -> TagStatus.DEFAULT
                    }
                )
            }
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (showType) TagType.byId(t.type).label + " · " else "") + "%,d galleries".format(t.count),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Browse this tag", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        TriToggle(st, onSet)
    }
}

/** Exclude / neutral / include segmented glass pill. */
@Composable
fun TriToggle(status: TagStatus, onSet: (TagStatus) -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(glass.tint).border(1.dp, glass.rim, RoundedCornerShape(50)).padding(3.dp),
    ) {
        Segment(Icons.Filled.Block, "Exclude", status == TagStatus.AVOIDED, MaterialTheme.colorScheme.error) { onSet(TagStatus.AVOIDED) }
        Segment(Icons.Filled.Remove, "Neutral", status == TagStatus.DEFAULT, MaterialTheme.colorScheme.onSurfaceVariant) { onSet(TagStatus.DEFAULT) }
        Segment(Icons.Filled.Check, "Include", status == TagStatus.ACCEPTED, Include) { onSet(TagStatus.ACCEPTED) }
    }
}

@Composable
private fun Segment(icon: ImageVector, desc: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.9f) else Color.Transparent, label = "seg")
    val fg = if (selected) (if (color == Include) Color.Black else Color.White) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Box(
        Modifier.width(40.dp).fillMaxHeight().clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = fg, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun StatusChip(label: String, selected: Boolean, color: Color?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val c = color ?: MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .height(36.dp)
            .glass(shape, strong = selected)
            .background(if (selected) c.copy(alpha = 0.22f) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SearchPill(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(48.dp).glass(RoundedCornerShape(50)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Clear") }
    }
}
