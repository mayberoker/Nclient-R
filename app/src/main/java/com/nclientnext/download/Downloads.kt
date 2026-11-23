package com.nclientnext.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nclientnext.App
import com.nclientnext.data.GalleryDetail
import com.nclientnext.data.Repository
import com.nclientnext.data.db.DownloadEntity
import com.nclientnext.data.db.DownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

const val DOWNLOAD_CHANNEL = "downloads"

class DownloadManager(private val context: Context, private val repo: Repository) {
    private val wm get() = WorkManager.getInstance(context)
    private val dao get() = repo.db.downloads()

    val root: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    fun dirFor(id: Int) = File(root, id.toString())

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(DOWNLOAD_CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    suspend fun enqueue(detail: GalleryDetail, pages: IntRange? = null) {
        val dir = dirFor(detail.id).apply { mkdirs() }
        val selected = if (pages == null) detail else detail.copy(pages = detail.pages.filterIndexed { i, _ -> i in pages })
        File(dir, "meta.json").writeText(repo.encodeDetail(selected))
        val existing = dao.get(detail.id)
        dao.upsert(
            DownloadEntity(
                id = detail.id,
                title = detail.title(repo.settings.current.titleType),
                thumbnail = detail.thumbnail,
                totalPages = selected.pages.size,
                downloadedPages = existing?.downloadedPages ?: 0,
                state = DownloadState.QUEUED,
                dir = dir.absolutePath,
                detailJson = repo.encodeDetail(selected),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
        )
        start(detail.id)
    }

    private fun start(id: Int) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("download")
            .build()
        wm.enqueueUniqueWork("download-$id", ExistingWorkPolicy.KEEP, req)
    }

    suspend fun pause(id: Int) {
        wm.cancelUniqueWork("download-$id")
        dao.setState(id, DownloadState.PAUSED)
    }

    suspend fun resume(id: Int) {
        dao.setState(id, DownloadState.QUEUED)
        start(id)
    }

    suspend fun delete(id: Int) {
        wm.cancelUniqueWork("download-$id")
        dao.delete(id)
        dirFor(id).deleteRecursively()
    }

    suspend fun resumeAllPending() = dao.pending().forEach { start(it.id) }

    /** Local files for a downloaded gallery, by page index. */
    fun localPages(id: Int): Map<Int, File> {
        val dir = dirFor(id)
        if (!dir.isDirectory) return emptyMap()
        return dir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") && it.name != "meta.json" }
            .mapNotNull { f -> f.nameWithoutExtension.toIntOrNull()?.let { it - 1 to f } }
            .toMap()
    }

    fun localDetail(id: Int): GalleryDetail? =
        File(dirFor(id), "meta.json").takeIf { it.exists() }?.let { repo.decodeDetail(it.readText()) }

    companion object {
        /** Limits how many galleries download at the same time. */
        val galleryLimiter = Semaphore(2)
        fun fileName(index: Int, ext: String) = "%04d.%s".format(index + 1, ext.ifBlank { "jpg" })
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val app = ctx.applicationContext as App
    private val repo get() = app.container.repository
    private val manager get() = app.container.downloads

    override suspend fun doWork(): Result {
        val id = inputData.getInt("id", -1)
        val dao = repo.db.downloads()
        val entity = dao.get(id) ?: return Result.failure()
        val detail = repo.decodeDetail(entity.detailJson) ?: return Result.failure()

        return DownloadManager.galleryLimiter.withPermit {
            try {
                runCatching { setForeground(foreground(entity.title, entity.downloadedPages, entity.totalPages)) }
                dao.setState(id, DownloadState.RUNNING)
                val dir = File(entity.dir).apply { mkdirs() }
                val done = AtomicInteger(manager.localPages(id).size)
                dao.setProgress(id, done.get())
                val pageLimiter = Semaphore(repo.settings.current.parallelDownloads.coerceIn(1, 8))
                coroutineScope {
                    detail.pages.mapIndexed { i, page ->
                        async {
                            val out = File(dir, DownloadManager.fileName(i, page.extension))
                            if (out.exists() && out.length() > 0) return@async
                            pageLimiter.withPermit {
                                repo.api.download(page.url, out)
                                val n = done.incrementAndGet()
                                dao.setProgress(id, n)
                                runCatching { setForeground(foreground(entity.title, n, detail.pages.size)) }
                            }
                        }
                    }.awaitAll()
                }
                dao.setProgress(id, detail.pages.size)
                dao.setState(id, DownloadState.DONE)
                notifyDone(id, entity.title)
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.setState(id, DownloadState.FAILED, e.message)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    private fun foreground(title: String, done: Int, total: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$done / $total pages")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        val nid = 10_000 + (inputData.getInt("id", 0) % 50_000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(nid, n)
    }

    private fun notifyDone(id: Int, title: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        nm.notify(70_000 + id % 50_000, n)
    }
}
