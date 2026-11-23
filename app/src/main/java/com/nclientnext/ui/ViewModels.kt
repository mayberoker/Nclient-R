package com.nclientnext.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nclientnext.AppContainer
import com.nclientnext.data.Comment
import com.nclientnext.data.GalleryDetail
import com.nclientnext.data.GalleryItem
import com.nclientnext.data.SearchPage
import com.nclientnext.data.SearchParams
import com.nclientnext.data.Tag
import com.nclientnext.data.TagStatus
import com.nclientnext.data.db.DownloadEntity
import com.nclientnext.data.db.HistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PagedState(
    val items: List<GalleryItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
) {
    val endReached get() = page >= totalPages && page > 0
}

/** Infinite-scroll loader shared by every gallery list. */
class PagedLoader(private val scope: CoroutineScope, private val fetch: suspend (Int) -> SearchPage) {
    private val _state = MutableStateFlow(PagedState())
    val state: StateFlow<PagedState> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh(pull: Boolean = false) {
        job?.cancel()
        _state.update { it.copy(loading = !pull, refreshing = pull, error = null) }
        job = scope.launch {
            runCatching { fetch(1) }
                .onSuccess { r -> _state.value = PagedState(r.items, 1, r.totalPages.coerceAtLeast(1)) }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.endReached || s.page == 0 || job?.isActive == true) return
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            runCatching { fetch(s.page + 1) }
                .onSuccess { r ->
                    _state.update { cur ->
                        val seen = cur.items.mapTo(HashSet()) { it.id }
                        cur.copy(
                            items = cur.items + r.items.filter { seen.add(it.id) },
                            page = r.page,
                            totalPages = r.totalPages.coerceAtLeast(r.page),
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }

    /** Jump directly to a page (replaces list). */
    fun jumpTo(page: Int) {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, items = emptyList()) }
        job = scope.launch {
            runCatching { fetch(page) }
                .onSuccess { r -> _state.value = PagedState(r.items, r.page, r.totalPages.coerceAtLeast(r.page)) }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
class BrowseViewModel(private val c: AppContainer, val params: SearchParams?) : ViewModel() {
    private val repo = c.repository
    var currentParams: SearchParams? = params
        private set
    val loader = PagedLoader(viewModelScope) { repo.browse(currentParams, it) }
    val avoided = repo.avoidedTagIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favorites = c.db.favorites().ids().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _popular = MutableStateFlow<List<GalleryItem>>(emptyList())
    val popular = _popular.asStateFlow()
    /** (included, avoided) counts for the Tags button. */
    val filterCounts = c.db.tags().filtered()
        .map { l -> l.count { it.status == TagStatus.ACCEPTED.ordinal } to l.count { it.status == TagStatus.AVOIDED.ordinal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0)

    init {
        reload()
        // Re-run the search whenever tag filters change (e.g. after visiting the Tags screen)
        viewModelScope.launch {
            c.db.tags().filtered()
                .map { l -> l.map { it.id to it.status }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(400)
                .collect { loader.refresh() }
        }
        // ...and when the language flag changes
        viewModelScope.launch {
            c.settings.flow.map { it.language }.distinctUntilChanged().drop(1).collect { reload() }
        }
    }

    fun reload(pull: Boolean = false) {
        loader.refresh(pull)
        if (params == null && c.settings.current.showPopularRow) {
            viewModelScope.launch { runCatching { repo.popularToday() }.onSuccess { _popular.value = it } }
        }
    }

    fun setSort(sort: com.nclientnext.data.SortType) {
        currentParams = (currentParams ?: SearchParams()).copy(sort = sort)
        loader.refresh()
    }

    fun saveBookmark(title: String) = viewModelScope.launch { repo.saveBookmark(title, currentParams ?: SearchParams()) }

    fun toggleFavorite(item: GalleryItem) = viewModelScope.launch {
        repo.setLocalFavorite(item, item.id !in favorites.value)
    }
}

data class GalleryUi(
    val detail: GalleryDetail? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
    val offline: Boolean = false,
    val onlineFavorite: Boolean = false,
    val onlineBusy: Boolean = false,
    val message: String? = null,
    val tagStatuses: Map<Int, TagStatus> = emptyMap(),
)

class GalleryViewModel(private val c: AppContainer, val id: Int) : ViewModel() {
    private val repo = c.repository
    private val _ui = MutableStateFlow(GalleryUi())
    val ui = _ui.asStateFlow()
    val localFavorite = repo.isLocalFavorite(id).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val history = c.db.history().get(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val download = c.db.downloads().observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { load() }

    fun load(force: Boolean = false) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        runCatching { repo.gallery(id, force) }
            .onSuccess { d -> _ui.update { it.copy(detail = d, loading = false, offline = false, onlineFavorite = d.isFavorited) }; loadStatuses(d) }
            .onFailure { e ->
                val local = c.downloads.localDetail(id)
                if (local != null) _ui.update { it.copy(detail = local, loading = false, offline = true) }
                else _ui.update { it.copy(loading = false, error = e) }
            }
    }

    private suspend fun loadStatuses(d: GalleryDetail) {
        val map = c.db.tags().filteredNow().associate { it.id to it.tagStatus }
        _ui.update { it.copy(tagStatuses = d.tags.associate { t -> t.id to (map[t.id] ?: TagStatus.DEFAULT) }) }
    }

    fun toggleLocalFavorite() = viewModelScope.launch {
        val d = _ui.value.detail ?: return@launch
        repo.setLocalFavorite(d.toItem(c.settings.current.titleType), !localFavorite.value)
    }

    fun toggleOnlineFavorite() = viewModelScope.launch {
        val target = !_ui.value.onlineFavorite
        _ui.update { it.copy(onlineBusy = true) }
        runCatching { repo.setOnlineFavorite(id, target) }
            .onSuccess { r -> _ui.update { it.copy(onlineFavorite = r, onlineBusy = false, message = if (r) "Added to online favorites" else "Removed from online favorites") } }
            .onFailure { e -> _ui.update { it.copy(onlineBusy = false, message = e.message) } }
    }

    fun download(range: IntRange? = null) = viewModelScope.launch {
        val d = _ui.value.detail ?: return@launch
        c.downloads.enqueue(d, range)
        _ui.update { it.copy(message = "Download queued") }
    }

    fun setTagStatus(tag: Tag, status: TagStatus) = viewModelScope.launch {
        repo.setTagStatus(tag, status)
        _ui.update { it.copy(tagStatuses = it.tagStatuses + (tag.id to status), message = when (status) {
            TagStatus.ACCEPTED -> "Always include \"${tag.name}\""
            TagStatus.AVOIDED -> "Avoiding \"${tag.name}\""
            TagStatus.DEFAULT -> "Filter removed for \"${tag.name}\""
        }) }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}

data class ReaderPage(val index: Int, val model: Any, val width: Int, val height: Int)

data class ReaderUi(
    val title: String = "",
    val pages: List<ReaderPage> = emptyList(),
    val startPage: Int = 0,
    val loading: Boolean = true,
    val error: Throwable? = null,
)

class ReaderViewModel(private val c: AppContainer, val id: Int, private val requestedPage: Int) : ViewModel() {
    private val repo = c.repository
    private val _ui = MutableStateFlow(ReaderUi())
    val ui = _ui.asStateFlow()
    private var detail: GalleryDetail? = null

    init { load() }

    fun load() = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        val local = c.downloads.localDetail(id)
        val localFiles = c.downloads.localPages(id)
        val d = local ?: runCatching { repo.gallery(id) }.getOrElse { e -> _ui.update { it.copy(loading = false, error = e) }; return@launch }
        detail = d
        val pages = d.pages.mapIndexed { i, p -> ReaderPage(i, localFiles[i] ?: p.url, p.width, p.height) }
        val start = when {
            requestedPage >= 0 -> requestedPage
            else -> c.db.history().getNow(id)?.lastPage ?: 0
        }.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        _ui.value = ReaderUi(d.title(c.settings.current.titleType), pages, start, loading = false)
    }

    fun onPage(page: Int) = viewModelScope.launch { detail?.let { repo.recordRead(it, page) } }
}

class CommentsViewModel(private val c: AppContainer, val id: Int) : ViewModel() {
    val comments = MutableStateFlow<List<Comment>?>(null)
    val error = MutableStateFlow<Throwable?>(null)
    init { load() }
    fun load() = viewModelScope.launch {
        error.value = null
        runCatching { c.api.comments(id) }.onSuccess { comments.value = it }.onFailure { error.value = it }
    }
}

class FavoritesViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    @OptIn(ExperimentalCoroutinesApi::class)
    val local = query.flatMapLatest { c.db.favorites().all(it) }
        .map { l -> l.map { it.toItem() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val avoided = c.repository.avoidedTagIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val online = PagedLoader(viewModelScope) { c.repository.onlineFavorites(it, query.value.ifBlank { null }) }
    val importProgress = MutableStateFlow<Int?>(null)
    val message = MutableStateFlow<String?>(null)

    fun importOnline() = viewModelScope.launch {
        importProgress.value = 0
        runCatching { c.repository.importOnlineFavorites { importProgress.value = it } }
            .onSuccess { message.value = "Imported $it favorites" }
            .onFailure { message.value = it.message }
        importProgress.value = null
    }

    suspend fun randomLocal(): Int? = c.db.favorites().random()?.id
    suspend fun randomOnline(): Int? = runCatching { c.api.randomFavoriteId() }.getOrNull()
}

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<HistoryEntity>> = query.flatMapLatest { c.db.history().all(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun delete(id: Int) = viewModelScope.launch { c.db.history().delete(id) }
    fun clear() = viewModelScope.launch { c.db.history().clear() }
}

class DownloadsViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<DownloadEntity>> = c.db.downloads().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableStateFlow<String?>(null)
    fun pause(id: Int) = viewModelScope.launch { c.downloads.pause(id) }
    fun resume(id: Int) = viewModelScope.launch { c.downloads.resume(id) }
    fun delete(id: Int) = viewModelScope.launch { c.downloads.delete(id) }
    fun exportCbz(id: Int, uri: android.net.Uri) = viewModelScope.launch {
        message.value = "Exporting CBZ…"
        message.value = runCatching { c.exporter.exportCbz(id, uri) }.fold({ "CBZ saved" }, { "Export failed: ${it.message}" })
    }
    fun exportPdf(id: Int, uri: android.net.Uri) = viewModelScope.launch {
        message.value = "Creating PDF…"
        message.value = runCatching { c.exporter.exportPdf(id, uri) }.fold({ "PDF saved" }, { "Export failed: ${it.message}" })
    }
}

class SearchViewModel(private val c: AppContainer, initial: SearchParams) : ViewModel() {
    val params = MutableStateFlow(initial)
    val text = MutableStateFlow(initial.query)
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<Tag>> = text.flatMapLatest { q ->
        val last = q.substringAfterLast(' ').trim()
        if (last.length < 2) flowOf(emptyList()) else kotlinx.coroutines.flow.flow { emit(c.repository.suggestTags(last)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addTag(tag: Tag, exclude: Boolean) {
        val words = text.value.trimEnd().split(' ').toMutableList()
        if (words.isNotEmpty()) words.removeAt(words.lastIndex)
        text.value = words.joinToString(" ").let { if (it.isEmpty()) "" else "$it " }
        params.update { p ->
            if (exclude) p.copy(exclude = (p.exclude + tag).distinctBy { it.id }, include = p.include.filter { it.id != tag.id })
            else p.copy(include = (p.include + tag).distinctBy { it.id }, exclude = p.exclude.filter { it.id != tag.id })
        }
    }

    fun toggleTag(tag: Tag) = params.update { p ->
        if (p.include.any { it.id == tag.id }) p.copy(include = p.include.filter { it.id != tag.id }, exclude = p.exclude + tag)
        else p.copy(exclude = p.exclude.filter { it.id != tag.id }, include = p.include + tag)
    }

    fun removeTag(tag: Tag) = params.update { p ->
        p.copy(include = p.include.filter { it.id != tag.id }, exclude = p.exclude.filter { it.id != tag.id })
    }

    fun build(): SearchParams = params.value.copy(query = text.value.trim())
}

data class TagSync(val done: Int, val total: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModel(private val c: AppContainer) : ViewModel() {
    val type = MutableStateFlow(com.nclientnext.data.TagType.TAG)
    val query = MutableStateFlow("")
    /** -1 = everything, otherwise a TagStatus ordinal. */
    val statusFilter = MutableStateFlow(-1)
    val alphabetical = MutableStateFlow(false)
    val limit = MutableStateFlow(300)

    val accepted = c.db.tags().byStatus(TagStatus.ACCEPTED.ordinal).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val avoided = c.db.tags().byStatus(TagStatus.AVOIDED.ordinal).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items = kotlinx.coroutines.flow.combine(type, query, statusFilter, alphabetical, limit) { t, q, st, a, l ->
        // Included/avoided views show every type so you can review all choices at once
        arrayOf(if (st >= 0) -1 else t.id, q.trim(), st, a, l)
    }.flatMapLatest { (t, q, st, a, l) ->
        c.db.tags().directory(t as Int, q as String, st as Int, a as Boolean, l as Int)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val localCount = type.flatMapLatest { c.db.tags().countType(it.id) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val remoteTotals = MutableStateFlow<Map<com.nclientnext.data.TagType, Int>>(emptyMap())
    val sync = MutableStateFlow<Map<com.nclientnext.data.TagType, TagSync>>(emptyMap())
    val message = MutableStateFlow<String?>(null)
    private val jobs = mutableMapOf<com.nclientnext.data.TagType, Job>()

    init {
        viewModelScope.launch {
            type.collect { t ->
                limit.value = 300
                if (t !in remoteTotals.value) runCatching { c.repository.tagTotal(t) }.onSuccess { total ->
                    remoteTotals.update { it + (t to total) }
                    // Small directories (tags, parodies, languages, categories) download automatically
                    val local = c.db.tags().countType(t.id).first()
                    if (total <= 6000 && local < total * 0.97) download(t)
                }
            }
        }
    }

    fun loadMore() { limit.update { it + 300 } }

    /** Downloads the complete directory for a tag type from nhentai. */
    fun download(t: com.nclientnext.data.TagType = type.value) {
        if (jobs[t]?.isActive == true) return
        jobs[t] = viewModelScope.launch {
            sync.update { it + (t to TagSync(0, 1)) }
            runCatching { c.repository.syncTagType(t) { d, total -> sync.update { it + (t to TagSync(d, total)) } } }
                .onSuccess { total -> remoteTotals.update { it + (t to total) }; message.value = "All ${t.label.lowercase()} downloaded" }
                .onFailure { message.value = "Couldn't download ${t.label.lowercase()}: ${it.message}" }
            sync.update { it - t }
        }
    }

    fun set(tag: Tag, status: TagStatus) = viewModelScope.launch { c.repository.setTagStatus(tag, status) }
    fun clearAll() = viewModelScope.launch { c.db.tags().clearStatuses() }
}
