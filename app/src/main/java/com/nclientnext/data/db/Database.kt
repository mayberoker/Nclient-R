package com.nclientnext.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.nclientnext.data.GalleryItem
import com.nclientnext.data.Tag
import com.nclientnext.data.TagStatus
import com.nclientnext.data.TagType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tags", indices = [Index("name"), Index("status")])
data class TagEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val type: Int,
    val count: Int,
    val status: Int = 0,
) {
    fun toTag() = Tag(id, name, TagType.byId(type), count)
    val tagStatus get() = TagStatus.entries[status]
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val thumbnail: String,
    val pages: Int,
    val tagIds: String,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toItem() = GalleryItem(id, "", title, "", thumbnail, pages, tagIds.split(',').mapNotNull { it.toIntOrNull() })

    companion object {
        fun from(item: GalleryItem) = FavoriteEntity(item.id, item.title, item.thumbnail, item.pages, item.tagIds.joinToString(","))
    }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val thumbnail: String,
    val lastPage: Int,
    val totalPages: Int,
    val tagIds: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toItem() = GalleryItem(id, "", title, "", thumbnail, totalPages, tagIds.split(',').mapNotNull { it.toIntOrNull() })
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val title: String,
    val paramsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

object DownloadState {
    const val QUEUED = 0
    const val RUNNING = 1
    const val PAUSED = 2
    const val DONE = 3
    const val FAILED = 4
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val thumbnail: String,
    val totalPages: Int,
    val downloadedPages: Int = 0,
    val state: Int = DownloadState.QUEUED,
    val dir: String,
    val detailJson: String,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (totalPages == 0) 0f else downloadedPages / totalPages.toFloat()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE name LIKE '%' || :q || '%' ORDER BY (name LIKE :q || '%') DESC, count DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 40): List<TagEntity>

    @Query("SELECT * FROM tags WHERE (:type < 0 OR type = :type) AND name LIKE '%' || :q || '%' ORDER BY count DESC LIMIT :limit")
    fun browse(q: String, type: Int, limit: Int = 300): Flow<List<TagEntity>>

    /** Tag directory query. type < 0 = any type, status < 0 = any status. */
    @Query(
        "SELECT * FROM tags WHERE (:type < 0 OR type = :type) AND name LIKE '%' || :q || '%' " +
            "AND (:status < 0 OR status = :status) " +
            "ORDER BY CASE WHEN :alpha = 1 THEN name END ASC, count DESC LIMIT :limit"
    )
    fun directory(type: Int, q: String, status: Int, alpha: Boolean, limit: Int): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM tags WHERE type = :type")
    fun countType(type: Int): Flow<Int>

    @Query("SELECT * FROM tags WHERE status = :status ORDER BY name")
    fun byStatus(status: Int): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE status != 0")
    fun filtered(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE status != 0")
    suspend fun filteredNow(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: Int): TagEntity?

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("UPDATE tags SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Int, status: Int): Int

    @Query("UPDATE tags SET status = 0")
    suspend fun clearStatuses()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tags: List<TagEntity>)

    @Query("UPDATE tags SET count = :count, name = :name WHERE id = :id")
    suspend fun updateInfo(id: Int, name: String, count: Int)

    /** Inserts/updates tag info while preserving the user's include/exclude status. */
    @Transaction
    suspend fun merge(tags: List<TagEntity>) {
        insertIgnore(tags)
        tags.forEach { updateInfo(it.id, it.name, it.count) }
    }

    @Transaction
    suspend fun setStatusOrInsert(tag: TagEntity, status: Int) {
        if (setStatus(tag.id, status) == 0) insertIgnore(listOf(tag.copy(status = status)))
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE title LIKE '%' || :q || '%' ORDER BY addedAt DESC")
    fun all(q: String = ""): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun allNow(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun isFavorite(id: Int): Flow<Boolean>

    @Query("SELECT id FROM favorites")
    fun ids(): Flow<List<Int>>

    @Upsert suspend fun upsert(f: FavoriteEntity)
    @Upsert suspend fun upsertAll(f: List<FavoriteEntity>)
    @Query("DELETE FROM favorites WHERE id = :id") suspend fun delete(id: Int)
    @Query("SELECT * FROM favorites ORDER BY RANDOM() LIMIT 1") suspend fun random(): FavoriteEntity?
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE title LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun all(q: String = ""): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    suspend fun allNow(): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE id = :id")
    fun get(id: Int): Flow<HistoryEntity?>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getNow(id: Int): HistoryEntity?

    @Upsert suspend fun upsert(h: HistoryEntity)
    @Upsert suspend fun upsertAll(h: List<HistoryEntity>)
    @Query("DELETE FROM history WHERE id = :id") suspend fun delete(id: Int)
    @Query("DELETE FROM history") suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun all(): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks") suspend fun allNow(): List<BookmarkEntity>
    @Insert suspend fun insert(b: BookmarkEntity): Long
    @Query("DELETE FROM bookmarks WHERE uid = :uid") suspend fun delete(uid: Long)
    @Query("UPDATE bookmarks SET title = :title WHERE uid = :uid") suspend fun rename(uid: Long, title: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun all(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE id = :id") fun observe(id: Int): Flow<DownloadEntity?>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: Int): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE state IN (0, 1)") suspend fun pending(): List<DownloadEntity>
    @Upsert suspend fun upsert(d: DownloadEntity)
    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id") suspend fun setState(id: Int, state: Int, error: String? = null)
    @Query("UPDATE downloads SET downloadedPages = :n WHERE id = :id") suspend fun setProgress(id: Int, n: Int)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: Int)
}

@Database(
    entities = [TagEntity::class, FavoriteEntity::class, HistoryEntity::class, BookmarkEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tags(): TagDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun downloads(): DownloadDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "nclientnext.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
