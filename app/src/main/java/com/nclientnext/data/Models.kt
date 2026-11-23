package com.nclientnext.data

import kotlinx.serialization.Serializable

enum class TagType(val id: Int, val key: String, val label: String) {
    UNKNOWN(0, "", "Other"),
    PARODY(1, "parody", "Parodies"),
    CHARACTER(2, "character", "Characters"),
    TAG(3, "tag", "Tags"),
    ARTIST(4, "artist", "Artists"),
    GROUP(5, "group", "Groups"),
    LANGUAGE(6, "language", "Languages"),
    CATEGORY(7, "category", "Categories");

    companion object {
        fun byId(id: Int) = entries.firstOrNull { it.id == id } ?: UNKNOWN
        fun byKey(key: String?) = entries.firstOrNull { it.key == key } ?: UNKNOWN
        /** Display order on the gallery page. */
        val displayOrder = listOf(PARODY, CHARACTER, TAG, ARTIST, GROUP, LANGUAGE, CATEGORY, UNKNOWN)
    }
}

enum class TagStatus { DEFAULT, ACCEPTED, AVOIDED }

@Serializable
data class Tag(
    val id: Int,
    val name: String,
    val type: TagType,
    val count: Int = 0,
) {
    /** nhentai search syntax, e.g. `artist:"name"` or `-tag:"name"`. */
    fun query(exclude: Boolean = false) = (if (exclude) "-" else "") + "${type.key}:\"$name\""
}

enum class Language(val label: String, val tagId: Int?, val flag: String, val emoji: String) {
    ALL("All languages", null, "", ""),
    ENGLISH("English", 12227, "EN", "🇬🇧"),
    JAPANESE("Japanese", 6346, "JP", "🇯🇵"),
    CHINESE("Chinese", 29963, "CN", "🇨🇳");

    /** Icon for the language button; a globe for "all". */
    val icon: String get() = emoji.ifEmpty { "🌐" }

    companion object {
        fun fromTagIds(ids: Collection<Int>): Language? =
            entries.firstOrNull { it.tagId != null && it.tagId in ids }
    }
}

enum class SortType(val label: String, val param: String?) {
    RECENT("Recent", null),
    POPULAR_TODAY("Popular today", "popular-today"),
    POPULAR_WEEK("Popular this week", "popular-week"),
    POPULAR_MONTH("Popular this month", "popular-month"),
    POPULAR_ALL("Popular all time", "popular");
}

enum class TitleType { PRETTY, ENGLISH, JAPANESE }

/** Lightweight gallery used in lists/grids. */
@Serializable
data class GalleryItem(
    val id: Int,
    val mediaId: String = "",
    val title: String,
    val titleJapanese: String = "",
    val thumbnail: String,
    val pages: Int = 0,
    val tagIds: List<Int> = emptyList(),
) {
    val language: Language? get() = Language.fromTagIds(tagIds)
}

@Serializable
data class PageInfo(
    val index: Int,
    val url: String,
    val thumbnail: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    val extension: String get() = url.substringAfterLast('.', "jpg").substringBefore('?').take(5)
}

@Serializable
data class GalleryDetail(
    val id: Int,
    val mediaId: String,
    val titlePretty: String,
    val titleEnglish: String,
    val titleJapanese: String,
    val cover: String,
    val thumbnail: String,
    val pages: List<PageInfo>,
    val tags: List<Tag>,
    val favorites: Int = 0,
    val uploadDate: Long = 0,
    val related: List<GalleryItem> = emptyList(),
    val isFavorited: Boolean = false,
) {
    fun title(type: TitleType): String = when (type) {
        TitleType.PRETTY -> titlePretty.ifBlank { titleEnglish }
        TitleType.ENGLISH -> titleEnglish.ifBlank { titlePretty }
        TitleType.JAPANESE -> titleJapanese.ifBlank { titleEnglish }
    }.ifBlank { "#$id" }

    val language: Language? get() = Language.fromTagIds(tags.map { it.id })

    fun toItem(type: TitleType = TitleType.ENGLISH) = GalleryItem(
        id = id, mediaId = mediaId, title = title(type), titleJapanese = titleJapanese,
        thumbnail = thumbnail, pages = pages.size, tagIds = tags.map { it.id },
    )
}

data class Comment(
    val id: Int,
    val username: String,
    val avatar: String?,
    val body: String,
    val date: Long,
)

data class SearchPage(
    val items: List<GalleryItem>,
    val page: Int,
    val totalPages: Int,
)

/** Everything that defines a search. Serializable so it can be bookmarked and passed through navigation. */
@Serializable
data class SearchParams(
    val query: String = "",
    val include: List<Tag> = emptyList(),
    val exclude: List<Tag> = emptyList(),
    val sort: SortType? = null,
    val language: Language? = null,
    val minPages: Int? = null,
    val maxPages: Int? = null,
    /** Only galleries uploaded within the last N days. */
    val uploadedWithinDays: Int? = null,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && include.isEmpty() && exclude.isEmpty() &&
            minPages == null && maxPages == null && uploadedWithinDays == null

    fun describe(): String {
        val parts = buildList {
            if (query.isNotBlank()) add(query)
            include.forEach { add(it.name) }
            exclude.forEach { add("-${it.name}") }
        }
        return parts.joinToString(" ").ifBlank { "All galleries" }
    }
}

class CloudflareException : Exception("Cloudflare verification required")
class AuthRequiredException : Exception("An API key is required for this action")
class HttpException(val code: Int, msg: String) : Exception(msg)
