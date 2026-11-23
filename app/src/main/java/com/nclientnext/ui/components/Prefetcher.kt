package com.nclientnext.ui.components

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.nclientnext.data.api.Hosts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Downloads full-resolution pages into the image disk cache ahead of the reader so pages
 * never buffer. Workers always pick the not-yet-cached page nearest *ahead* of [focus]
 * (then behind it), so jumping around re-prioritises without cancelling downloads in flight.
 */
class GalleryPrefetcher(
    private val context: Context,
    private val urls: List<String>,
    private val parallel: Int = 6,
    /** Only fetch this many pages ahead of the focus; null = whole gallery. */
    private val window: Int? = null,
) {
    @Volatile var focus: Int = 0
    private val done = BooleanArray(urls.size)
    private val inFlight = BooleanArray(urls.size)
    private val lock = Any()
    private val jobs = mutableListOf<Job>()
    private val loader get() = SingletonImageLoader.get(context)

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        repeat(parallel) {
            jobs += scope.launch {
                while (true) {
                    val i = next() ?: break
                    runCatching { fetch(urls[i]) }
                    synchronized(lock) { inFlight[i] = false; done[i] = true }
                }
            }
        }
    }

    /** Restart idle workers after the focus moved (needed when a window is set). */
    fun poke(scope: CoroutineScope) {
        jobs.removeAll { !it.isActive }
        if (jobs.size < parallel) repeat(parallel - jobs.size) {
            jobs += scope.launch {
                while (true) {
                    val i = next() ?: break
                    runCatching { fetch(urls[i]) }
                    synchronized(lock) { inFlight[i] = false; done[i] = true }
                }
            }
        }
    }

    private fun next(): Int? = synchronized(lock) {
        val f = focus.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val ahead = window ?: urls.size
        // forward first, then a few behind, then everything else behind
        val order = sequence {
            for (i in f until minOf(urls.size, f + ahead + 1)) yield(i)
            for (i in f - 1 downTo maxOf(0, f - 3)) yield(i)
            if (window == null) for (i in f - 4 downTo 0) yield(i)
        }
        order.firstOrNull { !done[it] && !inFlight[it] }?.also { inFlight[it] = true }
    }

    private suspend fun fetch(url: String) {
        if (url.isBlank()) return
        val disk = loader.diskCache
        if (disk != null) {
            val snap = disk.openSnapshot(url)
            if (snap != null) { snap.close(); return }
        }
        for (attempt in 0 until 4) {
            val req = ImageRequest.Builder(context)
                .data(Hosts.mirror(url, attempt))
                // Original bytes go to the disk cache; decode tiny so prefetching costs no memory.
                .size(Size(32, 32))
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            if (loader.execute(req) is SuccessResult) return
        }
    }
}
