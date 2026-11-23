package com.nclientnext.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nclientnext.data.Language
import com.nclientnext.data.SortType
import com.nclientnext.data.TitleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AvoidedMode(val label: String) {
    BLUR("Blur cover"), HIDE("Hide from lists"), REMOVE("Exclude in searches")
}
enum class ReaderMode(val label: String) {
    LTR("Left to right"), RTL("Right to left (manga)"), VERTICAL("Vertical scroll"), WEBTOON("Webtoon (no gaps)")
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 0, // 0 = adaptive
    val language: Language = Language.ALL,
    val defaultSort: SortType = SortType.RECENT,
    val avoidedMode: AvoidedMode = AvoidedMode.REMOVE,
    val titleType: TitleType = TitleType.ENGLISH,
    val readerMode: ReaderMode = ReaderMode.RTL,
    val keepScreenOn: Boolean = true,
    val volumeKeys: Boolean = true,
    val preloadPages: Int = 10,
    /** Download every page of a gallery in the background while reading. */
    val preloadAll: Boolean = true,
    val tapZones: Boolean = true,
    val incognito: Boolean = false,
    val secureScreen: Boolean = false,
    /** Privacy: blur every cover and thumbnail in lists (pages in the reader stay sharp). */
    val blurCovers: Boolean = false,
    val pinHash: String? = null,
    val biometric: Boolean = false,
    val apiKey: String? = null,
    val apiKeyValid: Boolean = false,
    val cfUserAgent: String? = null,
    val tagsVersion: Int = -1,
    val tagsSyncedAt: Long = 0,
    val parallelDownloads: Int = 6,
    val showPopularRow: Boolean = true,
    val infiniteScroll: Boolean = true,
) {
    val locked: Boolean get() = pinHash != null
}

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val dynamic = booleanPreferencesKey("dynamic")
        val grid = intPreferencesKey("grid")
        val language = stringPreferencesKey("language")
        val sort = stringPreferencesKey("sort")
        val avoided = stringPreferencesKey("avoided")
        val title = stringPreferencesKey("title")
        val reader = stringPreferencesKey("reader")
        val screenOn = booleanPreferencesKey("screen_on")
        val volume = booleanPreferencesKey("volume")
        val preload = intPreferencesKey("preload")
        val preloadAll = booleanPreferencesKey("preload_all")
        val calcIcon = booleanPreferencesKey("calc_icon")
        val tapZones = booleanPreferencesKey("tap_zones")
        val incognito = booleanPreferencesKey("incognito")
        val secure = booleanPreferencesKey("secure")
        val blurCovers = booleanPreferencesKey("blur_covers")
        val pin = stringPreferencesKey("pin")
        val biometric = booleanPreferencesKey("biometric")
        val apiKey = stringPreferencesKey("api_key")
        val apiKeyValid = booleanPreferencesKey("api_key_valid")
        val cfUa = stringPreferencesKey("cf_ua")
        val tagsVersion = intPreferencesKey("tags_version")
        val tagsSynced = longPreferencesKey("tags_synced")
        val parallel = intPreferencesKey("parallel")
        val popularRow = booleanPreferencesKey("popular_row")
        val infinite = booleanPreferencesKey("infinite")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, def: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def

    private fun Preferences.toSettings() = AppSettings(
        theme = enum(K.theme, ThemeMode.SYSTEM),
        amoled = this[K.amoled] ?: false,
        dynamicColor = this[K.dynamic] ?: true,
        gridColumns = this[K.grid] ?: 0,
        language = enum(K.language, Language.ALL),
        defaultSort = enum(K.sort, SortType.RECENT),
        avoidedMode = enum(K.avoided, AvoidedMode.REMOVE),
        titleType = enum(K.title, TitleType.ENGLISH),
        readerMode = enum(K.reader, ReaderMode.RTL),
        keepScreenOn = this[K.screenOn] ?: true,
        volumeKeys = this[K.volume] ?: true,
        preloadPages = this[K.preload] ?: 10,
        preloadAll = this[K.preloadAll] ?: true,
        tapZones = this[K.tapZones] ?: true,
        incognito = this[K.incognito] ?: false,
        secureScreen = this[K.secure] ?: false,
        blurCovers = this[K.blurCovers] ?: false,
        pinHash = this[K.pin],
        biometric = this[K.biometric] ?: false,
        apiKey = this[K.apiKey],
        apiKeyValid = this[K.apiKeyValid] ?: false,
        cfUserAgent = this[K.cfUa],
        tagsVersion = this[K.tagsVersion] ?: -1,
        tagsSyncedAt = this[K.tagsSynced] ?: 0,
        parallelDownloads = this[K.parallel] ?: 6,
        showPopularRow = this[K.popularRow] ?: true,
        infiniteScroll = this[K.infinite] ?: true,
    )

    val flow: StateFlow<AppSettings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { context.dataStore.data.first().toSettings() })

    val current: AppSettings get() = flow.value

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTheme(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setAmoled(v: Boolean) = edit { it[K.amoled] = v }
    suspend fun setDynamicColor(v: Boolean) = edit { it[K.dynamic] = v }
    suspend fun setGridColumns(v: Int) = edit { it[K.grid] = v }
    suspend fun setLanguage(v: Language) = edit { it[K.language] = v.name }
    suspend fun setDefaultSort(v: SortType) = edit { it[K.sort] = v.name }
    suspend fun setAvoidedMode(v: AvoidedMode) = edit { it[K.avoided] = v.name }
    suspend fun setTitleType(v: TitleType) = edit { it[K.title] = v.name }
    suspend fun setReaderMode(v: ReaderMode) = edit { it[K.reader] = v.name }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[K.screenOn] = v }
    suspend fun setVolumeKeys(v: Boolean) = edit { it[K.volume] = v }
    suspend fun setPreloadPages(v: Int) = edit { it[K.preload] = v }
    suspend fun setPreloadAll(v: Boolean) = edit { it[K.preloadAll] = v }
    suspend fun setTapZones(v: Boolean) = edit { it[K.tapZones] = v }
    suspend fun setIncognito(v: Boolean) = edit { it[K.incognito] = v }
    suspend fun setSecureScreen(v: Boolean) = edit { it[K.secure] = v }
    suspend fun setBlurCovers(v: Boolean) = edit { it[K.blurCovers] = v }
    suspend fun setBiometric(v: Boolean) = edit { it[K.biometric] = v }
    suspend fun setParallelDownloads(v: Int) = edit { it[K.parallel] = v }
    suspend fun setShowPopularRow(v: Boolean) = edit { it[K.popularRow] = v }
    suspend fun setInfiniteScroll(v: Boolean) = edit { it[K.infinite] = v }
    suspend fun setCfUserAgent(v: String?) = edit { if (v == null) it.remove(K.cfUa) else it[K.cfUa] = v }
    suspend fun setTagsVersion(v: Int) = edit {
        it[K.tagsVersion] = v
        it[K.tagsSynced] = System.currentTimeMillis()
    }

    suspend fun setPin(pin: String?) = edit {
        if (pin == null) { it.remove(K.pin); it[K.biometric] = false } else it[K.pin] = hash(pin)
    }

    fun checkPin(pin: String) = current.pinHash == hash(pin)

    suspend fun setApiKey(key: String?, valid: Boolean) = edit {
        if (key.isNullOrBlank()) { it.remove(K.apiKey); it[K.apiKeyValid] = false }
        else { it[K.apiKey] = key.trim(); it[K.apiKeyValid] = valid }
    }

    suspend fun setApiKeyValid(valid: Boolean) = edit { if (it[K.apiKey] != null) it[K.apiKeyValid] = valid }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("nclientnext:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
