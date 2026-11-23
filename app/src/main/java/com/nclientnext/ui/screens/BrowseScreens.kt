package com.nclientnext.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.nclientnext.ui.components.GlassScaffold
import com.nclientnext.ui.components.glassBarColors
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.OutlinedButton
import com.nclientnext.ui.Routes
import com.nclientnext.ui.components.listPadding
import com.nclientnext.ui.theme.glass
import com.nclientnext.ui.theme.glassRim
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nclientnext.data.Language
import com.nclientnext.data.SearchParams
import com.nclientnext.data.SortType
import com.nclientnext.data.Tag
import com.nclientnext.ui.BrowseViewModel
import com.nclientnext.ui.SearchViewModel
import com.nclientnext.ui.components.EmptyState
import com.nclientnext.ui.components.ErrorState
import com.nclientnext.ui.components.GalleryGrid
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.components.MirrorImage
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(params: SearchParams?, paramsKey: String = "home") {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: BrowseViewModel = viewModel(key = "browse-$paramsKey") { BrowseViewModel(c, params) }
    val state by vm.loader.state.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val avoided by vm.avoided.collectAsState()
    val favs by vm.favorites.collectAsState()
    val popular by vm.popular.collectAsState()
    val filters by vm.filterCounts.collectAsState()
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyGridState()
    var sortMenu by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    var bookmarkDialog by remember { mutableStateOf(false) }
    var jumpDialog by remember { mutableStateOf(false) }
    var randomBusy by remember { mutableStateOf(false) }
    val isHome = params == null
    val sort = vm.currentParams?.sort ?: settings.defaultSort

    GlassScaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(colors = glassBarColors(), 
                scrollBehavior = scroll,
                navigationIcon = {
                    if (!isHome) IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    if (isHome) Text(androidx.compose.ui.text.buildAnnotatedString {
                        append("Nclient-")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        append("R"); pop()
                    })
                    else Column {
                        Text(params!!.describe(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.totalPages > 1) Text("Page ${state.page} of ${state.totalPages}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    // Language: flag only
                    Box {
                        IconButton(onClick = { langMenu = true }) {
                            Text(settings.language.icon, fontSize = 20.sp)
                        }
                        DropdownMenu(langMenu, onDismissRequest = { langMenu = false }) {
                            Language.entries.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l.icon, fontSize = 24.sp) },
                                    trailingIcon = { if (l == settings.language) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { langMenu = false; scope.launch { c.settings.setLanguage(l); gridState.scrollToItem(0) } },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { nav.route(Routes.Tags) }) {
                        val n = filters.first + filters.second
                        BadgedBox(badge = { if (n > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$n") } }) {
                            Icon(Icons.Outlined.Sell, "Tags")
                        }
                    }
                    if (isHome) IconButton(onClick = { nav.searchEditor() }) { Icon(Icons.Outlined.Search, "Search") }
                    else {
                        IconButton(onClick = { nav.searchEditor(params!!) }) { Icon(Icons.Outlined.Tune, "Edit search") }
                        IconButton(onClick = { bookmarkDialog = true }) { Icon(Icons.Outlined.BookmarkAdd, "Bookmark search") }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "Sort") }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortType.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    leadingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                                    onClick = { sortMenu = false; vm.setSort(s); scope.launch { gridState.scrollToItem(0) } },
                                )
                            }
                        }
                    }
                    if (!isHome) IconButton(onClick = { jumpDialog = true }) { Icon(Icons.Outlined.Pin, "Go to page") }
                    if (isHome) IconButton(enabled = !randomBusy, onClick = {
                        randomBusy = true
                        scope.launch {
                            runCatching { c.repository.randomId() }
                                .onSuccess { nav.gallery(it) }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                            randomBusy = false
                        }
                    }) { Icon(Icons.Outlined.Casino, "Random") }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.reload(pull = true) },
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState, isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = pad.calculateTopPadding()),
                )
            },
        ) {
            when {
                state.items.isEmpty() && state.error != null -> ErrorState(state.error!!, onRetry = { vm.reload() }, modifier = Modifier.padding(pad))
                state.items.isEmpty() && !state.loading && !state.refreshing && state.page > 0 ->
                    EmptyState(Icons.Outlined.SearchOff, "No results", "Try different tags or remove some filters.", Modifier.padding(pad)) {
                        OutlinedButton(onClick = { nav.route(Routes.Tags) }) { Text("Review tag filters") }
                    }
                else -> GalleryGrid(
                    items = state.items,
                    columns = settings.gridColumns,
                    avoidedIds = avoided,
                    avoidedMode = settings.avoidedMode,
                    favoriteIds = favs,
                    loading = state.loading,
                    endReached = state.endReached || !settings.infiniteScroll,
                    onLoadMore = vm.loader::loadMore,
                    state = gridState,
                    contentPadding = listPadding(pad),
                    onLongClick = { g ->
                        vm.toggleFavorite(g)
                        scope.launch { snackbar.showSnackbar(if (g.id in favs) "Removed from favorites" else "Added to favorites") }
                    },
                    header = {
                        if (isHome && popular.isNotEmpty() && settings.showPopularRow) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "popular") {
                                Column {
                                    Text("Popular today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(popular, key = { it.id }) { g ->
                                            Column(Modifier.width(120.dp).clickable { nav.gallery(g.id) }) {
                                                MirrorImage(
                                                    g.thumbnail,
                                                    Modifier.fillMaxWidth().aspectRatio(0.7f).glassRim(MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium),
                                                )
                                                Text(com.nclientnext.ui.components.discreet(g.title), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Latest", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    },
                )
            }
            if (state.error != null && state.items.isNotEmpty()) {
                androidx.compose.runtime.LaunchedEffect(state.error) {
                    val r = snackbar.showSnackbar(state.error?.message ?: "Error", actionLabel = "Retry")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.loader.loadMore()
                }
            }
        }
    }

    if (bookmarkDialog) {
        var name by remember { mutableStateOf(params?.describe() ?: "") }
        AlertDialog(
            onDismissRequest = { bookmarkDialog = false },
            title = { Text("Bookmark this search") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.saveBookmark(name); bookmarkDialog = false; scope.launch { snackbar.showSnackbar("Bookmark saved") } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { bookmarkDialog = false }) { Text("Cancel") } },
        )
    }
    if (jumpDialog) {
        var page by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { jumpDialog = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    page, { page = it.filter(Char::isDigit) },
                    label = { Text("Page (1–${state.totalPages})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    page.toIntOrNull()?.let { vm.loader.jumpTo(it.coerceIn(1, state.totalPages.coerceAtLeast(1))) }
                    jumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { jumpDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SearchEditorScreen(initial: SearchParams) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val vm: SearchViewModel = viewModel { SearchViewModel(c, initial) }
    val params by vm.params.collectAsState()
    val text by vm.text.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    var minPages by rememberSaveable { mutableStateOf(initial.minPages?.toString() ?: "") }
    var maxPages by rememberSaveable { mutableStateOf(initial.maxPages?.toString() ?: "") }
    var days by rememberSaveable { mutableStateOf(initial.uploadedWithinDays?.toString() ?: "") }
    var sort by rememberSaveable { mutableStateOf(initial.sort) }
    var language by rememberSaveable { mutableStateOf(initial.language) }

    fun submit() {
        val p = vm.build().copy(
            minPages = minPages.toIntOrNull(), maxPages = maxPages.toIntOrNull(),
            uploadedWithinDays = days.toIntOrNull(), sort = sort, language = language,
        )
        val q = p.query.removePrefix("#")
        if (q.isNotEmpty() && q.all(Char::isDigit) && p.include.isEmpty() && p.exclude.isEmpty()) {
            nav.gallery(q.toInt()); return
        }
        nav.search(p)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    GlassScaffold(
        topBar = {
            TopAppBar(colors = glassBarColors(), 
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    androidx.compose.material3.TextField(
                        value = text,
                        onValueChange = { vm.text.value = it },
                        placeholder = { Text("Search, tags, or gallery ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { vm.text.value = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = ::submit, icon = { Icon(Icons.Outlined.Search, null) }, text = { Text("Search") }, modifier = Modifier.imePadding())
        },
    ) { pad ->
        if (suggestions.isNotEmpty()) {
            LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding()) {
                items(suggestions, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.name) },
                        supportingContent = { Text("${t.type.label} · ${t.count} galleries") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.addTag(t, exclude = true) }) { Icon(Icons.Filled.Remove, "Exclude") }
                                IconButton(onClick = { vm.addTag(t, exclude = false) }) { Icon(Icons.Filled.Add, "Include") }
                            }
                        },
                        modifier = Modifier.clickable { vm.addTag(t, exclude = false) },
                    )
                }
            }
            return@GlassScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (params.include.isNotEmpty() || params.exclude.isNotEmpty()) {
                Section("Tags (tap to flip include / exclude)") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        params.include.forEach { t -> TagInputChip(t, false, onClick = { vm.toggleTag(t) }, onRemove = { vm.removeTag(t) }) }
                        params.exclude.forEach { t -> TagInputChip(t, true, onClick = { vm.toggleTag(t) }, onRemove = { vm.removeTag(t) }) }
                    }
                }
            } else {
                Text(
                    "Type a tag, artist, parody or character to get suggestions. Tip: enter a number to open that gallery directly.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Section("Sort") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(sort == null, onClick = { sort = null }, label = { Text("Default") })
                    SortType.entries.forEach { s -> FilterChip(sort == s, onClick = { sort = s }, label = { Text(s.label) }) }
                }
            }
            Section("Language") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == null, onClick = { language = null }, label = { Text("Default") })
                    Language.entries.forEach { l -> FilterChip(language == l, onClick = { language = l }, label = { Text(l.label) }) }
                }
            }
            Section("Pages") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(minPages, { minPages = it.filter(Char::isDigit) }, label = { Text("Min") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(maxPages, { maxPages = it.filter(Char::isDigit) }, label = { Text("Max") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
            Section("Uploaded") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "Any time", "1" to "Today", "7" to "This week", "30" to "This month", "365" to "This year").forEach { (v, l) ->
                        FilterChip(days == v, onClick = { days = v }, label = { Text(l) })
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun TagInputChip(tag: Tag, excluded: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClick,
        label = { Text((if (excluded) "− " else "+ ") + tag.name) },
        trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.clickable(onClick = onRemove)) },
        colors = if (excluded) InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else InputChipDefaults.inputChipColors(),
    )
}


@Composable
fun GlassPill(onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .glass(RoundedCornerShape(50), strong = highlighted)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
