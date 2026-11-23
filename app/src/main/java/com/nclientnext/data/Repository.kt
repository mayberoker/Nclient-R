package com.nclientnext.data

import com.nclientnext.data.api.Hosts
import com.nclientnext.data.api.NhApi
import com.nclientnext.data.db.AppDatabase
import com.nclientnext.data.db.BookmarkEntity
import com.nclientnext.data.db.FavoriteEntity
import com.nclientnext.data.db.HistoryEntity
import com.nclientnext.data.db.TagEntity
import com.nclientnext.data.prefs.AvoidedMode
import com.nclientnext.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class Repository(
    val api: NhApi,
    val db: AppDatabase,
    val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Small in-memory cache so going back and forth between screens doesn't refetch. */
    private val detailCache = object : LinkedHashMap<Int, GalleryDetail>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, GalleryDetail>?) = size > 40
    }

    // ---------- browsing ----------

    /**
     * Runs a search. Global tag filters (accepted/avoided), the language filter and
     * page/date ranges are folded into the nhentai query string.
     */
    suspend fun browse(params: SearchParams?, page: Int): SearchPage {
        val s = settings.current
        val p = params ?: SearchParams()
        val sort = p.sort ?: s.defaultSort
        val filters = db.tags().filteredNow()
        val tokens = mutableListOf<String>()
        if (p.query.isNotBlank()) tokens += p.query.trim()
        p.include.forEach { tokens += it.query() }
        p.exclude.forEach { tokens += it.query(exclude = true) }
        filters.filter { it.status == TagStatus.ACCEPTED.ordinal }.forEach { tokens += it.toTag().query() }
        if (s.avoidedMode == AvoidedMode.REMOVE) {
            filters.filter { it.status == TagStatus.AVOIDED.ordinal }.forEach { tokens += it.toTag().query(true) }
        }
        val lang = p.language ?: s.language
        if (lang != Language.ALL) tokens += "language:\"${lang.name.lowercase()}\""
        p.minPages?.let { tokens += "pages:>=$it" }
        p.maxPages?.let { tokens += "pages:<=$it" }
        p.uploadedWithinDays?.let { tokens += "uploaded:<${it}d" }

        val distinct = tokens.distinct()
        return if (distinct.isEmpty() && sort == SortType.RECENT) api.galleries(page)
        else api.search(distinct.joinToString(" "), page, sort.takeIf { it != SortType.RECENT })
    }

    suspend fun gallery(id: Int, force: Boolean = false): GalleryDetail {
        if (!force) synchronized(detailCache) { detailCache[id] }?.let { return it }
        val d = api.gallery(id)
        synchronized(detailCache) { detailCache[id] = d }
        return d
    }

    fun cached(id: Int): GalleryDetail? = synchronized(detailCache) { detailCache[id] }

    /** Trending today, respecting the language flag and tag filters. */
    suspend fun popularToday(): List<GalleryItem> = browse(SearchParams(sort = SortType.POPULAR_TODAY), 1).items.take(12)

    suspend fun randomId(): Int = api.randomId()

    // ---------- tag filters ----------

    val avoidedTagIds: Flow<Set<Int>> = db.tags().byStatus(TagStatus.AVOIDED.ordinal).map { l -> l.map { it.id }.toSet() }

    suspend fun setTagStatus(tag: Tag, status: TagStatus) =
        db.tags().setStatusOrInsert(TagEntity(tag.id, tag.name, tag.type.id, tag.count), status.ordinal)

    suspend fun tagStatus(id: Int): TagStatus = db.tags().get(id)?.tagStatus ?: TagStatus.DEFAULT

    suspend fun suggestTags(q: String): List<Tag> =
        if (q.isBlank()) emptyList() else db.tags().search(q.trim()).map { it.toTag() }

    /** Downloads the community-maintained tag list used for autocomplete. Returns number of tags. */
    suspend fun syncTags(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val remote = api.rawText(Hosts.TAGS_VERSION).trim().toIntOrNull() ?: -1
        if (!force && remote != -1 && remote == settings.current.tagsVersion && db.tags().count() > 0) return@withContext 0
        val root = Json.parseToJsonElement(api.rawText(Hosts.TAGS_DB)).jsonArray
        // Format is either [[id,name,count,type],...] or [[[...]],...] (grouped by type)
        val flat = ArrayList<TagEntity>(root.size)
        fun add(a: JsonArray) {
            if (a.size >= 4 && a[0] is JsonPrimitive) {
                flat += TagEntity(a[0].jsonPrimitive.int, a[1].jsonPrimitive.content, a[3].jsonPrimitive.int, a[2].jsonPrimitive.int)
            } else a.forEach { (it as? JsonArray)?.let(::add) }
        }
        add(root)
        flat.chunked(2000).forEach { db.tags().merge(it) }
        settings.setTagsVersion(remote)
        flat.size
    }

    /**
     * Pulls every tag of [type] from nhentai's tag directory into the local database
     * (keeps the user's include/avoid choices). Pages are fetched in parallel.
     */
    suspend fun syncTagType(type: TagType, onProgress: (done: Int, total: Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val first = api.tags(type, 1)
        db.tags().merge(first.tags.map { TagEntity(it.id, it.name, it.type.id, it.count) })
        val pages = first.numPages
        val done = java.util.concurrent.atomic.AtomicInteger(1)
        onProgress(1, pages)
        val sem = kotlinx.coroutines.sync.Semaphore(6)
        kotlinx.coroutines.coroutineScope {
            (2..pages).map { p ->
                async {
                    sem.withPermit {
                        val page = retry3 { api.tags(type, p) }
                        db.tags().merge(page.tags.map { TagEntity(it.id, it.name, it.type.id, it.count) })
                        onProgress(done.incrementAndGet(), pages)
                    }
                }
            }.awaitAll()
        }
        first.total
    }

    suspend fun tagTotal(type: TagType): Int = api.tags(type, 1).also { pg ->
        db.tags().merge(pg.tags.map { TagEntity(it.id, it.name, it.type.id, it.count) })
    }.total

    private suspend fun <T> retry3(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(3) { i ->
            try { return block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { last = e; kotlinx.coroutines.delay(500L * (i + 1)) }
        }
        throw last!!
    }

    // ---------- favorites ----------

    fun isLocalFavorite(id: Int) = db.favorites().isFavorite(id)

    suspend fun setLocalFavorite(item: GalleryItem, fav: Boolean) {
        if (fav) db.favorites().upsert(FavoriteEntity.from(item)) else db.favorites().delete(item.id)
    }

    val hasAccount: Boolean get() = !settings.current.apiKey.isNullOrBlank()

    suspend fun setOnlineFavorite(id: Int, fav: Boolean): Boolean {
        if (!hasAccount) throw AuthRequiredException()
        val r = api.setFavorite(id, fav)
        synchronized(detailCache) { detailCache[id]?.let { detailCache[id] = it.copy(isFavorited = r) } }
        return r
    }

    suspend fun onlineFavorites(page: Int, q: String?): SearchPage {
        if (!hasAccount) throw AuthRequiredException()
        return api.favorites(page, q)
    }

    /** Copies every online favorite into the local favorites list. */
    suspend fun importOnlineFavorites(onProgress: (Int) -> Unit): Int {
        var page = 1
        var total = 0
        while (true) {
            val res = onlineFavorites(page, null)
            db.favorites().upsertAll(res.items.map(FavoriteEntity::from))
            total += res.items.size
            onProgress(total)
            if (page >= res.totalPages || res.items.isEmpty()) break
            page++
        }
        return total
    }

    // ---------- history ----------

    suspend fun recordRead(detail: GalleryDetail, page: Int) {
        if (settings.current.incognito) return
        db.history().upsert(
            HistoryEntity(
                id = detail.id,
                title = detail.title(settings.current.titleType),
                thumbnail = detail.thumbnail,
                lastPage = page,
                totalPages = detail.pages.size,
                tagIds = detail.tags.joinToString(",") { it.id.toString() },
            )
        )
    }

    // ---------- bookmarks ----------

    suspend fun saveBookmark(title: String, params: SearchParams) =
        db.bookmarks().insert(BookmarkEntity(title = title, paramsJson = json.encodeToString(params)))

    fun decodeParams(s: String): SearchParams = runCatching { json.decodeFromString<SearchParams>(s) }.getOrDefault(SearchParams())
    fun encodeParams(p: SearchParams): String = json.encodeToString(p)
    fun encodeDetail(d: GalleryDetail): String = json.encodeToString(d)
    fun decodeDetail(s: String): GalleryDetail? = runCatching { json.decodeFromString<GalleryDetail>(s) }.getOrNull()
}
