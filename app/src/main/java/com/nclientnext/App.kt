package com.nclientnext

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nclientnext.data.Repository
import com.nclientnext.data.api.NhApi
import com.nclientnext.data.db.AppDatabase
import com.nclientnext.data.prefs.SettingsStore
import com.nclientnext.download.DownloadManager
import com.nclientnext.export.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app, scope)
    val api = NhApi(settings)
    val db = AppDatabase.create(app)
    val repository = Repository(api, db, settings)
    val downloads = DownloadManager(app, repository)
    val exporter = Exporter(app, repository, downloads)
    val v3 = com.nclientnext.export.NClientV3Compat(app, repository)
}

class App : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.downloads.createChannel()
        container.scope.launch {
            // Refresh the tag database at most once every 3 days
            val s = container.settings.current
            if (System.currentTimeMillis() - s.tagsSyncedAt > 3L * 24 * 3600 * 1000) {
                runCatching { container.repository.syncTags() }
            }
            runCatching { container.downloads.resumeAllPending() }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.api.client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.35).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(2048L * 1024 * 1024) // 2 GB so whole galleries stay cached
                    .build()
            }
            .crossfade(true)
            .build()
}
