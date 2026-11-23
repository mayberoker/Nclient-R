package com.nclientnext.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.nclientnext.data.Repository
import com.nclientnext.data.db.BookmarkEntity
import com.nclientnext.data.db.FavoriteEntity
import com.nclientnext.data.db.HistoryEntity
import com.nclientnext.download.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class BackupTag(val id: Int, val name: String, val type: Int, val count: Int, val status: Int)

@Serializable
data class BackupFavorite(val id: Int, val title: String, val thumbnail: String, val pages: Int, val tagIds: String, val addedAt: Long)

@Serializable
data class BackupHistory(val id: Int, val title: String, val thumbnail: String, val lastPage: Int, val totalPages: Int, val tagIds: String, val updatedAt: Long)

@Serializable
data class BackupBookmark(val title: String, val paramsJson: String, val createdAt: Long)

@Serializable
data class Backup(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val favorites: List<BackupFavorite> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
)

class Exporter(private val context: Context, private val repo: Repository, private val downloads: DownloadManager) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** Comic book archive: plain zip of the page images, readable by any comic reader. */
    suspend fun exportCbz(id: Int, target: Uri) = withContext(Dispatchers.IO) {
        val pages = downloads.localPages(id).toSortedMap()
        require(pages.isNotEmpty()) { "Nothing downloaded yet" }
        context.contentResolver.openOutputStream(target)!!.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.setLevel(0) // images are already compressed
                downloads.localDetail(id)?.let { d ->
                    zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                    zip.write(comicInfo(d.title(repo.settings.current.titleType), d.tags.joinToString(",") { it.name }, d.pages.size).toByteArray())
                    zip.closeEntry()
                }
                pages.values.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun exportPdf(id: Int, target: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        val pages = downloads.localPages(id).toSortedMap().values.toList()
        require(pages.isNotEmpty()) { "Nothing downloaded yet" }
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, f ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, opts)
                // keep memory bounded: downsample very large pages
                var sample = 1
                while (opts.outWidth / sample > 1600) sample *= 2
                val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@forEachIndexed
                val page = doc.startPage(PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create())
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                doc.finishPage(page)
                bmp.recycle()
                onProgress(i + 1, pages.size)
            }
            context.contentResolver.openOutputStream(target)!!.use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    suspend fun backup(target: Uri) = withContext(Dispatchers.IO) {
        val db = repo.db
        val b = Backup(
            favorites = db.favorites().allNow().map { BackupFavorite(it.id, it.title, it.thumbnail, it.pages, it.tagIds, it.addedAt) },
            history = db.history().allNow().map { BackupHistory(it.id, it.title, it.thumbnail, it.lastPage, it.totalPages, it.tagIds, it.updatedAt) },
            bookmarks = db.bookmarks().allNow().map { BackupBookmark(it.title, it.paramsJson, it.createdAt) },
            tags = db.tags().filteredNow().map { BackupTag(it.id, it.name, it.type, it.count, it.status) },
        )
        context.contentResolver.openOutputStream(target)!!.use { it.write(json.encodeToString(b).toByteArray()) }
    }

    /** Merges a backup into the current data. Returns a short summary. */
    suspend fun restore(source: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)!!.use { it.readBytes().decodeToString() }
        val b = json.decodeFromString<Backup>(text)
        val db = repo.db
        db.favorites().upsertAll(b.favorites.map { FavoriteEntity(it.id, it.title, it.thumbnail, it.pages, it.tagIds, it.addedAt) })
        db.history().upsertAll(b.history.map { HistoryEntity(it.id, it.title, it.thumbnail, it.lastPage, it.totalPages, it.tagIds, it.updatedAt) })
        val existing = db.bookmarks().allNow().map { it.paramsJson }.toSet()
        b.bookmarks.filter { it.paramsJson !in existing }.forEach {
            db.bookmarks().insert(BookmarkEntity(title = it.title, paramsJson = it.paramsJson, createdAt = it.createdAt))
        }
        b.tags.forEach {
            db.tags().setStatusOrInsert(com.nclientnext.data.db.TagEntity(it.id, it.name, it.type, it.count), it.status)
        }
        "Restored ${b.favorites.size} favorites, ${b.history.size} history entries, ${b.bookmarks.size} bookmarks, ${b.tags.size} tag filters"
    }

    private fun comicInfo(title: String, tags: String, pages: Int) = """<?xml version="1.0" encoding="utf-8"?>
<ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <Title>${title.xml()}</Title>
  <Tags>${tags.xml()}</Tags>
  <PageCount>$pages</PageCount>
  <Manga>YesAndRightToLeft</Manga>
</ComicInfo>"""

    private fun String.xml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
