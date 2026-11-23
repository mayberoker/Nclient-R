package com.nclientnext.export

import android.content.Context
import android.net.Uri
import com.nclientnext.data.Repository
import com.nclientnext.data.SearchParams
import com.nclientnext.data.SortType
import com.nclientnext.data.db.BookmarkEntity
import com.nclientnext.data.db.FavoriteEntity
import com.nclientnext.data.db.HistoryEntity
import com.nclientnext.data.db.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes NClientV3 (and NClientV2) backups: a zip containing `Database.json`,
 * a dump of its SQLite tables (Gallery, Favorite, GalleryTags, Tags, History, Resume, Bookmark...).
 */
class NClientV3Compat(private val context: Context, private val repo: Repository) {

    // NClientV3 TagStatus order is DEFAULT, AVOIDED, ACCEPTED; ours is DEFAULT, ACCEPTED, AVOIDED.
    private fun v3ToOurStatus(s: Int) = when (s) { 1 -> 2; 2 -> 1; else -> 0 }
    private fun ourToV3Status(s: Int) = when (s) { 1 -> 2; 2 -> 1; else -> 0 }

    data class Result(
        val favorites: Int, val history: Int, val tags: Int, val bookmarks: Int,
        val statusLists: Int = 0, val damaged: Boolean = false, val recovered: Int = 0,
    ) {
        override fun toString() = buildString {
            append("Imported $favorites favorite galleries")
            if (statusLists > 0) append(" (incl. $statusLists from status lists)")
            append(", $bookmarks bookmarks, $history history, $tags tag filters")
            if (damaged) append(". The backup file was damaged; everything readable was recovered")
            if (recovered > 0) append(" ($recovered favorites rebuilt from the saved-galleries table)")
            if (favorites == 0) append(". No local favorites in this backup: if your NClientV3 favorites are on your nhentai account, add your API key under Account, then Favorites ⋮ → Import online favorites")
        }
    }

    /** True if the file looks like a zip (NClientV3 backup) rather than our JSON backup. */
    fun isV3Backup(uri: Uri): Boolean = context.contentResolver.openInputStream(uri)?.use { s ->
        val head = ByteArray(2); s.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    } ?: false

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val (dbJson, damaged) = readDatabaseJson(bytes)
            ?: throw IllegalArgumentException("No Database.json found in this file. Pick the .zip made by NClientV3 → Settings → Export")
        val (tables, complete) = parseTolerant(dbJson)
        if (tables.isEmpty()) throw IllegalArgumentException("The backup is too damaged to read. Export it again from NClientV3")
        fun table(name: String): List<JsonObject> = tables[name].orEmpty()

        val galleries = table("Gallery").associateBy { it.int("idGallery") }
        val bridge = table("GalleryTags").groupBy({ it.int("id_gallery") }, { it.int("id_tag") })
        val v3Tags = table("Tags")
        val resume = table("Resume").associate { it.int("id_gallery") to it.int("page") }

        // Tags: bring over names (for autocomplete) and include/exclude choices
        val tagEntities = v3Tags.map { TagEntity(it.int("idTag"), it.str("name"), it.int("type"), it.int("count")) }
        tagEntities.chunked(2000).forEach { repo.db.tags().merge(it) }
        var tagFilters = 0
        v3Tags.filter { it.int("status") != 0 }.forEach {
            repo.db.tags().setStatusOrInsert(TagEntity(it.int("idTag"), it.str("name"), it.int("type"), it.int("count")), v3ToOurStatus(it.int("status")))
            tagFilters++
        }

        // Favorites
        val favs = table("Favorite").mapNotNull { f ->
            val id = f.int("id_gallery").takeIf { it > 0 } ?: return@mapNotNull null
            val g = galleries[id]
            val (count, thumb) = parsePages(g)
            FavoriteEntity(
                id = id,
                title = g?.let(::title) ?: "#$id",
                thumbnail = thumb,
                pages = count,
                tagIds = bridge[id].orEmpty().joinToString(","),
                addedAt = toMillis(f.long("time")),
            )
        }
        // NClientV3 "status" lists (Reading, Completed, custom...) are saved galleries too
        val favIds = favs.map { it.id }.toMutableSet()
        // If the backup broke off before the Favorite table finished, rebuild favorites from the Gallery
        // table: NClientV3 only keeps galleries there that are favorited, downloaded or in a status list,
        // and it is written first, so it survives a cut-off file.
        val recovered = if ("Favorite" !in complete) galleries.values.mapNotNull { g ->
            val id = g.int("idGallery").takeIf { it > 0 && favIds.add(it) } ?: return@mapNotNull null
            val (count, thumb) = parsePages(g)
            FavoriteEntity(id, title(g), thumb, count, bridge[id].orEmpty().joinToString(","))
        } else emptyList()
        val fromStatus = table("StatusManga").mapNotNull { st ->
            val id = st.int("gallery").takeIf { it > 0 && favIds.add(it) } ?: return@mapNotNull null
            val g = galleries[id]
            val (count, thumb) = parsePages(g)
            FavoriteEntity(id, g?.let(::title) ?: "#$id", thumb, count, bridge[id].orEmpty().joinToString(","), toMillis(st.long("time")))
        }
        repo.db.favorites().upsertAll(favs + fromStatus + recovered)

        // History (+ resume position)
        val hist = table("History").mapNotNull { h ->
            val id = h.int("id").takeIf { it > 0 } ?: return@mapNotNull null
            val g = galleries[id]
            val (count, thumb) = parsePages(g)
            val thumbUrl = h.str("thumbType").takeIf { it.startsWith("http") } ?: thumb
            HistoryEntity(
                id = id,
                title = h.str("title").ifBlank { g?.let(::title) ?: "#$id" },
                thumbnail = thumbUrl,
                lastPage = resume[id]?.coerceAtLeast(0) ?: 0,
                totalPages = count,
                tagIds = bridge[id].orEmpty().joinToString(","),
                updatedAt = toMillis(h.long("time")),
            )
        }
        repo.db.history().upsertAll(hist)

        // Bookmarks (saved searches)
        val existing = repo.db.bookmarks().allNow().map { it.paramsJson }.toSet()
        var bm = 0
        table("Bookmark").forEach { b ->
            val params = bookmarkToParams(b.str("url")) ?: return@forEach
            val json = repo.encodeParams(params)
            if (json !in existing) { repo.db.bookmarks().insert(BookmarkEntity(title = params.describe(), paramsJson = json)); bm++ }
        }
        Result(favs.size + fromStatus.size + recovered.size, hist.size, tagFilters, bm, fromStatus.size, damaged || "Favorite" !in complete, recovered.size)
    }

    /** Writes a zip NClientV3 can restore (Settings → Import). Note: NClientV3 replaces its tables on import. */
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val db = repo.db
        val favs = db.favorites().allNow()
        val hist = db.history().allNow()
        val filtered = db.tags().filteredNow()
        val referenced = (favs.flatMap { it.tagIds.ids() } + hist.flatMap { it.tagIds.ids() }).toSet()
        val tags = (filtered + referenced.mapNotNull { db.tags().get(it) }).distinctBy { it.id }
        val tagIds = tags.map { it.id }.toSet()

        fun galleryRow(id: Int, title: String, thumb: String, pages: Int) = buildJsonObject {
            put("idGallery", id); put("title_eng", title); put("title_jp", ""); put("title_pretty", title)
            put("favorite_count", 0); put("mediaId", mediaIdFrom(thumb))
            put("pages", "$pages;$thumb;$thumb;")
            put("upload", 0); put("maxW", 0); put("maxH", 0); put("minW", 0); put("minH", 0)
        }
        val galleryRows = (favs.map { galleryRow(it.id, it.title, it.thumbnail, it.pages) } +
            hist.filter { h -> favs.none { it.id == h.id } }.map { galleryRow(it.id, it.title, it.thumbnail, it.totalPages) })
        val bridgeRows = (favs.map { it.id to it.tagIds } + hist.map { it.id to it.tagIds }).flatMap { (g, ids) ->
            ids.ids().filter { it in tagIds }.map { t -> buildJsonObject { put("id_gallery", g); put("id_tag", t) } }
        }.distinct()

        val root = buildJsonObject {
            put("Gallery", JsonArray(galleryRows))
            put("Tags", JsonArray(tags.map { t ->
                buildJsonObject { put("idTag", t.id); put("name", t.name); put("type", t.type); put("count", t.count); put("status", ourToV3Status(t.status)); put("online", 0) }
            }))
            put("GalleryTags", JsonArray(bridgeRows))
            put("Favorite", JsonArray(favs.map { f -> buildJsonObject { put("id_gallery", f.id); put("time", f.addedAt) } }))
            put("History", JsonArray(hist.map { h ->
                buildJsonObject { put("id", h.id); put("mediaId", mediaIdFrom(h.thumbnail)); put("title", h.title); put("thumbType", h.thumbnail); put("time", h.updatedAt) }
            }))
            put("Resume", JsonArray(hist.filter { it.lastPage > 0 }.map { h -> buildJsonObject { put("id_gallery", h.id); put("page", h.lastPage) } }))
        }
        context.contentResolver.openOutputStream(target)!!.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry("Database.json"))
                zip.write(root.toString().toByteArray())
                zip.closeEntry()
            }
        }
    }

    // ---------- helpers ----------

    /**
     * Returns (Database.json text, damaged?). Tries the zip's central directory first, then a
     * streaming read that keeps whatever decompressed before the file breaks off (NClientV3 can
     * write zips that end early, which gives "Unexpected end of ZLIB input stream").
     */
    private fun readDatabaseJson(bytes: ByteArray): Pair<String, Boolean>? {
        val isZip = bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) {
            val text = bytes.decodeToString()
            return if (text.trimStart().startsWith("{")) text to false else null
        }
        // 1) Proper zip with central directory
        runCatching {
            val tmp = java.io.File(context.cacheDir, "v3import.zip").apply { writeBytes(bytes) }
            try {
                java.util.zip.ZipFile(tmp).use { zf ->
                    val e = zf.entries().asSequence().firstOrNull { it.name.endsWith("Database.json", true) } ?: return null
                    val out = java.io.ByteArrayOutputStream()
                    val ok = copyPartial(zf.getInputStream(e), out)
                    return out.toByteArray().decodeToString() to !ok
                }
            } finally { tmp.delete() }
        }
        // 2) Streaming read, salvaging partial data
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = runCatching { zip.nextEntry }.getOrNull() ?: break
                if (e.name.endsWith("Database.json", ignoreCase = true)) {
                    val out = java.io.ByteArrayOutputStream()
                    val ok = copyPartial(zip, out)
                    return out.toByteArray().decodeToString() to !ok
                }
            }
        }
        return null
    }

    /** Copies until EOF; on a corrupt stream keeps what was read. Returns false if it broke off. */
    private fun copyPartial(input: java.io.InputStream, out: java.io.OutputStream): Boolean {
        val buf = ByteArray(64 * 1024)
        return try {
            while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            true
        } catch (e: java.io.IOException) { false }
    }

    /**
     * Reads {"Table":[{...},...],...} and keeps every complete row, even if the text is cut off.
     */
    private fun parseTolerant(text: String): Pair<Map<String, List<JsonObject>>, Set<String>> {
        val out = LinkedHashMap<String, MutableList<JsonObject>>()
        val complete = HashSet<String>()
        try {
            val r = android.util.JsonReader(java.io.StringReader(text)).apply { isLenient = true }
            r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName()
                if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) { r.skipValue(); continue }
                val list = out.getOrPut(name) { mutableListOf() }
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    r.beginObject()
                    val m = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
                    while (r.hasNext()) {
                        val k = r.nextName()
                        m[k] = when (r.peek()) {
                            android.util.JsonToken.NULL -> { r.nextNull(); JsonNull }
                            android.util.JsonToken.BOOLEAN -> JsonPrimitive(r.nextBoolean())
                            android.util.JsonToken.NUMBER, android.util.JsonToken.STRING -> JsonPrimitive(r.nextString())
                            else -> { r.skipValue(); JsonNull }
                        }
                    }
                    r.endObject()
                    list += JsonObject(m)
                }
                r.endArray()
                complete += name
            }
        } catch (e: Exception) {
            // Truncated: keep the complete rows parsed so far
        }
        return out to complete
    }

    private fun title(g: JsonObject): String =
        g.str("title_eng").ifBlank { g.str("title_pretty") }.ifBlank { g.str("title_jp") }.ifBlank { "#${g.int("idGallery")}" }

    /** NClientV3 stores pages as "count;cover;thumb;page1;page2;..." (relative or absolute URLs). */
    private fun parsePages(g: JsonObject?): Pair<Int, String> {
        if (g == null) return 0 to ""
        val mediaId = g.int("mediaId")
        val parts = g.str("pages").split(';')
        val count = parts.getOrNull(0)?.toIntOrNull() ?: (parts.size - 3).coerceAtLeast(0)
        val thumbPart = parts.getOrNull(2).orEmpty().ifBlank { parts.getOrNull(1).orEmpty() }
        val thumb = when {
            thumbPart.startsWith("http") -> thumbPart
            thumbPart.startsWith("/") && mediaId > 0 -> "https://t1.nhentai.net/galleries/$mediaId$thumbPart"
            mediaId > 0 -> "https://t1.nhentai.net/galleries/$mediaId/thumb.jpg"
            else -> ""
        }
        return count to thumb
    }

    private fun bookmarkToParams(url: String): SearchParams? {
        if (url.isBlank()) return null
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val sort = u.getQueryParameter("sort")?.let { s -> SortType.entries.firstOrNull { it.param == s } }
        // Uri already %-decodes; nhentai uses '+' for spaces
        val q = (u.getQueryParameter("query") ?: u.getQueryParameter("q"))?.replace('+', ' ')
        if (!q.isNullOrBlank()) return SearchParams(query = q.trim(), sort = sort)
        val seg = u.pathSegments
        val i = seg.indexOfFirst { it in setOf("tag", "artist", "parody", "character", "group", "language", "category") }
        if (i >= 0 && seg.size > i + 1) return SearchParams(query = "${seg[i]}:\"${seg[i + 1].replace('-', ' ')}\"", sort = sort)
        return null
    }

    private fun mediaIdFrom(thumb: String): Int = Regex("""/galleries/(\d+)/""").find(thumb)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    private fun toMillis(t: Long): Long = when { t <= 0 -> System.currentTimeMillis(); t < 100_000_000_000L -> t * 1000; else -> t }
    private fun String.ids() = split(',').mapNotNull { it.trim().toIntOrNull() }

    private fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
    private fun JsonObject.long(k: String): Long = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() } ?: 0L
    private fun JsonObject.int(k: String): Int = long(k).toInt()
}
