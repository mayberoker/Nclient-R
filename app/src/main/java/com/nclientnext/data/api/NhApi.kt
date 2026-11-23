package com.nclientnext.data.api

import android.webkit.CookieManager
import com.nclientnext.BuildConfig
import com.nclientnext.data.AuthRequiredException
import com.nclientnext.data.CloudflareException
import com.nclientnext.data.Comment
import com.nclientnext.data.GalleryDetail
import com.nclientnext.data.GalleryItem
import com.nclientnext.data.HttpException
import com.nclientnext.data.PageInfo
import com.nclientnext.data.SearchPage
import com.nclientnext.data.SortType
import com.nclientnext.data.Tag
import com.nclientnext.data.TagType
import com.nclientnext.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Hosts {
    const val DOMAIN = "nhentai.net"
    const val BASE = "https://$DOMAIN/"
    const val API = "${BASE}api/v2/"
    const val TAGS_DB = "https://raw.githubusercontent.com/maxwai/NClientV3/main/data/tags.json"
    const val TAGS_VERSION = "https://raw.githubusercontent.com/maxwai/NClientV3/main/data/tagsVersion"

    fun image(path: String, page: Boolean): String {
        if (path.isBlank()) return ""
        if (path.startsWith("http")) return path
        return "https://${if (page) "i1" else "t1"}.$DOMAIN/${path.trimStart('/')}"
    }

    /** nhentai serves the same files from i1..i4 / t1..t4. Rotate when one mirror fails. */
    fun mirror(url: String, attempt: Int): String {
        if (attempt == 0) return url
        val n = (attempt % 4) + 1
        return url.replace(Regex("""://([it])\d\."""), "://$1$n.")
    }

    fun galleryUrl(id: Int) = "${BASE}g/$id/"
}

/** Shares cookies with WebView so a Cloudflare challenge solved in-app also unlocks OkHttp. */
class WebViewCookieJar : CookieJar {
    private val cm: CookieManager? = runCatching { CookieManager.getInstance() }.getOrNull()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = cm ?: return
        cookies.forEach { m.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = cm?.getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

class NhApi(private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val defaultUa = "NClient-R/${BuildConfig.VERSION_NAME} (Android)"

    private val headerInterceptor = Interceptor { chain ->
        val s = settings.current
        val req = chain.request()
        val b = req.newBuilder()
        if (req.header("User-Agent") == null) b.header("User-Agent", s.cfUserAgent ?: defaultUa)
        val isApi = req.url.host.endsWith(Hosts.DOMAIN) && req.url.encodedPath.startsWith("/api/v2/")
        if (isApi && req.header("Authorization") == null && !s.apiKey.isNullOrBlank()) {
            b.header("Authorization", "Key ${s.apiKey}")
        }
        val resp = chain.proceed(b.build())
        if (isApi && !s.apiKey.isNullOrBlank() && req.header("Authorization") == null) {
            if (resp.code == 401) kotlinx.coroutines.runBlocking { settings.setApiKeyValid(false) }
        }
        resp
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .addInterceptor(headerInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 12 })
        .retryOnConnectionFailure(true)
        .build()

    // ---------- low level ----------

    private suspend fun get(url: String, auth: String? = null): JsonElement = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(url)
        if (auth != null) rb.header("Authorization", auth)
        client.newCall(rb.build()).await().use { resp ->
            val body = resp.body?.string().orEmpty()
            checkResponse(resp, body)
            json.parseToJsonElement(body)
        }
    }

    private suspend fun send(url: String, method: String): Response = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).method(method, ByteArray(0).toRequestBody()).build()
        client.newCall(req).await()
    }

    private fun checkResponse(resp: Response, body: String) {
        if (resp.isSuccessful) return
        val looksLikeCf = resp.header("cf-mitigated") != null ||
            ((resp.code == 403 || resp.code == 503) && body.contains("<html", true) &&
                (body.contains("cloudflare", true) || body.contains("Just a moment", true)))
        if (looksLikeCf) throw CloudflareException()
        if (resp.code == 401) throw AuthRequiredException()
        val msg = runCatching { json.parseToJsonElement(body).obj?.str("error") ?: json.parseToJsonElement(body).obj?.str("detail") }
            .getOrNull()
        throw HttpException(resp.code, msg ?: "HTTP ${resp.code}")
    }

    // ---------- endpoints ----------

    suspend fun galleries(page: Int): SearchPage = parseList(get("${Hosts.API}galleries?page=$page"), page)

    suspend fun search(query: String, page: Int, sort: SortType?): SearchPage {
        val q = URLEncoder.encode(query.ifBlank { "-nclientnext" }, "UTF-8")
        val sortParam = sort?.param?.let { "&sort=$it" } ?: ""
        return parseList(get("${Hosts.API}search?query=$q&page=$page$sortParam"), page)
    }

    suspend fun gallery(id: Int): GalleryDetail =
        parseDetail(get("${Hosts.API}galleries/$id?include=related,favorite").obj!!)

    suspend fun randomId(): Int = get("${Hosts.API}galleries/random").obj?.int("id")
        ?: throw HttpException(0, "No random gallery returned")

    suspend fun randomFavoriteId(): Int = get("${Hosts.API}favorites/random").obj?.int("id")
        ?: throw HttpException(0, "No favorites")

    suspend fun favorites(page: Int, query: String?): SearchPage {
        val q = if (query.isNullOrBlank()) "" else "&q=" + URLEncoder.encode(query, "UTF-8")
        return parseList(get("${Hosts.API}favorites?page=$page$q"), page)
    }

    /** @return the new favorite state */
    suspend fun setFavorite(id: Int, favorite: Boolean): Boolean {
        send("${Hosts.API}galleries/$id/favorite", if (favorite) "POST" else "DELETE").use { resp ->
            val body = resp.body?.string().orEmpty()
            checkResponse(resp, body)
        }
        return favorite
    }

    suspend fun comments(id: Int): List<Comment> {
        val root = get("${Hosts.API}galleries/$id/comments")
        val arr = (root as? JsonArray) ?: root.obj?.get("result") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.obj ?: return@mapNotNull null
            val poster = o["poster"]?.obj
            Comment(
                id = o.int("id") ?: 0,
                username = poster?.str("username") ?: "Anonymous",
                avatar = poster?.str("avatar_url")?.let { a ->
                    if (a.startsWith("http")) a else "https://i.${Hosts.DOMAIN}/${a.trimStart('/')}"
                },
                body = o.str("body").orEmpty(),
                date = (o.long("post_date") ?: 0L) * 1000,
            )
        }
    }

    data class TagPage(val tags: List<Tag>, val numPages: Int, val total: Int)

    /** Official tag directory: /api/v2/tags/{type}?page=N, 120 per page, most popular first. */
    suspend fun tags(type: TagType, page: Int): TagPage {
        val o = get("${Hosts.API}tags/${type.key}?page=$page").obj ?: return TagPage(emptyList(), 0, 0)
        val list = (o["result"] as? JsonArray)?.mapNotNull { el ->
            val t = el.obj ?: return@mapNotNull null
            Tag(t.int("id") ?: return@mapNotNull null, t.str("name").orEmpty(), TagType.byKey(t.str("type")).takeIf { it != TagType.UNKNOWN } ?: type, t.int("count") ?: 0)
        } ?: emptyList()
        return TagPage(list, o.int("num_pages") ?: 1, o.int("total") ?: list.size)
    }

    /** Checks an API key without saving it. */
    suspend fun validateKey(key: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${Hosts.API}favorites?page=1").header("Authorization", "Key ${key.trim()}").build()
        client.newCall(req).await().use { resp ->
            if (resp.isSuccessful) return@withContext true
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) {
                runCatching { checkResponse(resp, body) }.exceptionOrNull()?.let { if (it is CloudflareException) throw it }
                return@withContext false
            }
            checkResponse(resp, body)
            false
        }
    }

    suspend fun rawText(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).await().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    /** Streams a file (page image) to [out]. */
    suspend fun download(url: String, out: java.io.File) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in 0 until 4) {
            try {
                val req = Request.Builder().url(Hosts.mirror(url, attempt)).build()
                client.newCall(req).await().use { resp ->
                    if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
                    val tmp = java.io.File(out.parentFile, out.name + ".part")
                    resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
                    if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
                }
                return@withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Download failed")
    }

    // ---------- parsing ----------

    private fun parseList(root: JsonElement, page: Int): SearchPage {
        val o = root.obj ?: return SearchPage(emptyList(), page, page)
        val result = o["result"] as? JsonArray ?: JsonArray(emptyList())
        return SearchPage(
            items = result.mapNotNull { it.obj?.let(::parseItem) },
            page = page,
            totalPages = o.int("num_pages") ?: page,
        )
    }

    private fun parseItem(o: JsonObject): GalleryItem {
        val titleObj = o["title"]?.obj
        val english = o.str("english_title") ?: titleObj?.str("english") ?: ""
        val pretty = titleObj?.str("pretty") ?: ""
        val japanese = o.str("japanese_title") ?: titleObj?.str("japanese") ?: ""
        val thumb = when (val t = o["thumbnail"]) {
            is JsonPrimitive -> t.content
            is JsonObject -> t.str("path")
            else -> null
        } ?: o["cover"]?.obj?.str("path") ?: ""
        val tagIds = (o["tag_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            ?: (o["tags"] as? JsonArray)?.mapNotNull { it.obj?.int("id") }
            ?: emptyList()
        return GalleryItem(
            id = o.int("id") ?: 0,
            mediaId = o.str("media_id") ?: "",
            title = english.ifBlank { pretty }.ifBlank { japanese },
            titleJapanese = japanese,
            thumbnail = Hosts.image(thumb, page = false),
            pages = o.int("num_pages") ?: 0,
            tagIds = tagIds,
        )
    }

    private fun parseDetail(o: JsonObject): GalleryDetail {
        if (o["error"] != null && o["id"] == null) throw HttpException(404, o.str("error") ?: "Gallery not found")
        val t = o["title"]?.obj
        val pages = (o["pages"] as? JsonArray)?.mapIndexedNotNull { i, el ->
            val p = el.obj ?: return@mapIndexedNotNull null
            val path = p.str("path") ?: return@mapIndexedNotNull null
            PageInfo(
                index = i,
                url = Hosts.image(path, page = true),
                thumbnail = Hosts.image(p.str("thumbnail") ?: path, page = p.str("thumbnail") == null),
                width = p.int("width") ?: 0,
                height = p.int("height") ?: 0,
            )
        } ?: emptyList()
        val tags = (o["tags"] as? JsonArray)?.mapNotNull { el ->
            val tg = el.obj ?: return@mapNotNull null
            val type = tg["type"].let { ty ->
                (ty as? JsonPrimitive)?.intOrNull?.let { TagType.byId(it) } ?: TagType.byKey((ty as? JsonPrimitive)?.content)
            }
            Tag(tg.int("id") ?: return@mapNotNull null, tg.str("name").orEmpty(), type, tg.int("count") ?: 0)
        } ?: emptyList()
        val related = (o["related"] as? JsonArray)?.mapNotNull { it.obj?.let(::parseItem) } ?: emptyList()
        val coverPath = o["cover"]?.obj?.str("path")
        val thumbPath = o["thumbnail"]?.let { (it as? JsonPrimitive)?.content ?: it.obj?.str("path") }
        return GalleryDetail(
            id = o.int("id") ?: 0,
            mediaId = o.str("media_id") ?: "",
            titlePretty = t?.str("pretty") ?: "",
            titleEnglish = t?.str("english") ?: "",
            titleJapanese = t?.str("japanese") ?: "",
            cover = Hosts.image(coverPath ?: thumbPath ?: "", page = false),
            thumbnail = Hosts.image(thumbPath ?: coverPath ?: "", page = false),
            pages = pages,
            tags = tags,
            favorites = o.int("num_favorites") ?: 0,
            uploadDate = (o.long("upload_date") ?: 0L) * 1000,
            related = related,
            isFavorited = (o["is_favorited"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
}

// ---------- JSON helpers ----------
internal val JsonElement.obj: JsonObject? get() = this as? JsonObject
internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }
internal fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }
