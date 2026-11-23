package com.nclientnext.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import com.nclientnext.ui.components.GlassScaffold
import com.nclientnext.ui.components.glassBarColors
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nclientnext.BuildConfig
import com.nclientnext.data.Language
import com.nclientnext.data.SortType
import com.nclientnext.data.TagStatus
import com.nclientnext.data.TagType
import com.nclientnext.data.TitleType
import com.nclientnext.data.api.Hosts
import com.nclientnext.data.db.TagEntity
import com.nclientnext.data.prefs.AvoidedMode
import com.nclientnext.data.prefs.ReaderMode
import com.nclientnext.data.prefs.ThemeMode
import com.nclientnext.ui.TagsViewModel
import com.nclientnext.ui.components.EmptyState
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.components.LocalNavigator
import com.nclientnext.ui.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- More (hub) ----------------

@Composable
fun MoreScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by c.settings.flow.collectAsState()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(runCatching { c.exporter.backup(uri) }.fold({ "Backup saved" }, { "Backup failed: ${it.message}" }))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            // Accepts both Nclient-R backups (.json) and NClientV3 backups (.zip)
            snackbar.showSnackbar(runCatching {
                if (c.v3.isV3Backup(uri)) c.v3.import(uri).toString() else c.exporter.restore(uri)
            }.getOrElse { "Restore failed: ${it.message}" })
        }
    }
    val v3ImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(runCatching { c.v3.import(uri).toString() }.getOrElse { "Import failed: ${it.message}" })
        }
    }
    val v3ExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(runCatching { c.v3.export(uri) }.fold({ "Saved. In NClientV3: Settings → Import" }, { "Export failed: ${it.message}" }))
        }
    }

    GlassScaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { TopAppBar(colors = glassBarColors(), title = { Text("More") }) }) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.AccountCircle, null) },
                headlineContent = { Text("Account") },
                supportingContent = { Text(if (settings.apiKey != null) (if (settings.apiKeyValid) "Connected with API key" else "API key saved (not verified)") else "Add an API key for online favorites") },
                modifier = Modifier.clickable { nav.account() },
            )
            HubItem(Icons.Outlined.Bookmarks, "Saved searches", "Bookmarked search queries") { nav.route(Routes.Bookmarks) }
            HubItem(Icons.Outlined.FilterAlt, "Tags", "Include, exclude or ignore every nhentai tag") { nav.route(Routes.Tags) }
            HubItem(Icons.Outlined.Casino, "Random gallery", "Surprise me") {
                scope.launch { runCatching { c.repository.randomId() }.onSuccess { nav.gallery(it) }.onFailure { snackbar.showSnackbar(it.message ?: "Failed") } }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Security, null) },
                headlineContent = { Text("Incognito mode") },
                supportingContent = { Text("Don't record reading history") },
                trailingContent = { Switch(settings.incognito, { v -> scope.launch { c.settings.setIncognito(v) } }) },
            )
            HubItem(Icons.Outlined.Settings, "Settings", "Appearance, reader, privacy, downloads") { nav.route(Routes.Settings) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            HubItem(Icons.Outlined.Backup, "Back up data", "Favorites, history, bookmarks and tag filters to a file") {
                backupLauncher.launch("nclient-r-backup-${System.currentTimeMillis() / 1000}.json")
            }
            HubItem(Icons.Outlined.Restore, "Restore backup", "Merge a backup file (Nclient-R or NClientV3) into this device") { restoreLauncher.launch(arrayOf("application/json", "application/zip", "*/*")) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            HubItem(Icons.Outlined.FileDownload, "Import from NClientV3", "Favorites, history, resume pages, tag filters and bookmarks from an NClientV3 backup .zip") {
                v3ImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*"))
            }
            HubItem(Icons.Outlined.FileUpload, "Export for NClientV3", "A .zip NClientV3 can import (it replaces NClientV3's favorites/history)") {
                v3ExportLauncher.launch("NClient-R_for_NClientV3_${System.currentTimeMillis() / 1000}.zip")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            HubItem(Icons.Outlined.Lightbulb, "Request a feature", "Ideas are welcome: open a request on GitHub") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.nclientnext.data.Project.FEATURE)))
            }
            HubItem(Icons.Outlined.BugReport, "Report a bug", "Something broken? Tell me and I'll fix it") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.nclientnext.data.Project.BUG)))
            }
            HubItem(Icons.Outlined.Info, "About", "Nclient-R ${BuildConfig.VERSION_NAME} · Made by Roker · Carrying the torch of NClientV3") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.nclientnext.data.Project.URL)))
            }
            Spacer(Modifier.height(com.nclientnext.ui.components.LocalBottomInset.current + 16.dp))
        }
    }
}

@Composable
private fun HubItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val s by c.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    val st = c.settings
    var pinDialog by remember { mutableStateOf(false) }
    fun io(block: suspend () -> Unit) { scope.launch { block() } }

    GlassScaffold(topBar = {
        TopAppBar(colors = glassBarColors(), title = { Text("Settings") }, navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("Appearance")
            AppIconChoice()
            Choice("Grid columns", listOf(0, 2, 3, 4, 5), s.gridColumns, { if (it == 0) "Auto" else "$it" }) { io { st.setGridColumns(it) } }
            Choice("Titles", TitleType.entries, s.titleType, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { io { st.setTitleType(it) } }
            Toggle("\"Popular today\" row", "Show trending galleries on the home screen", s.showPopularRow) { io { st.setShowPopularRow(it) } }
            Toggle("Infinite scroll", "Load the next page automatically", s.infiniteScroll) { io { st.setInfiniteScroll(it) } }

            Header("Browsing")
            Choice("Language", Language.entries, s.language, { it.label }) { io { st.setLanguage(it) } }
            Choice("Default sort", SortType.entries, s.defaultSort, { it.label }) { io { st.setDefaultSort(it) } }
            Choice("Avoided tags", AvoidedMode.entries, s.avoidedMode, { it.label }) { io { st.setAvoidedMode(it) } }

            Header("Reader")
            Choice("Reading mode", ReaderMode.entries, s.readerMode, { it.label }) { io { st.setReaderMode(it) } }
            Toggle("Tap zones", "Tap left / right edges to turn pages", s.tapZones) { io { st.setTapZones(it) } }
            Toggle("Volume keys turn pages", null, s.volumeKeys) { io { st.setVolumeKeys(it) } }
            Toggle("Keep screen on while reading", null, s.keepScreenOn) { io { st.setKeepScreenOn(it) } }
            Toggle("Preload whole gallery", "Downloads every page in full resolution while you read: no buffering, uses more data", s.preloadAll) { io { st.setPreloadAll(it) } }
            if (!s.preloadAll) SliderSetting("Preload pages ahead", s.preloadPages, 2..30) { io { st.setPreloadPages(it) } }

            Header("Downloads")
            SliderSetting("Parallel page downloads", s.parallelDownloads, 1..8) { io { st.setParallelDownloads(it) } }
            Text("Files are kept in the app's storage. Export to CBZ/PDF from the Downloads tab to keep them elsewhere.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

            Header("Privacy & security")
            ListItem(
                headlineContent = { Text("App lock (PIN)") },
                supportingContent = { Text(if (s.locked) "Enabled" else "Off") },
                leadingContent = { Icon(Icons.Outlined.Lock, null) },
                trailingContent = { Switch(s.locked, { on -> if (on) pinDialog = true else io { st.setPin(null) } }) },
            )
            if (s.locked) Toggle("Unlock with biometrics", "Fingerprint or face, falls back to PIN", s.biometric, icon = Icons.Outlined.Fingerprint) { io { st.setBiometric(it) } }
            Toggle("Hide content in recents", "Blocks screenshots and blanks the app preview", s.secureScreen) { io { st.setSecureScreen(it) } }
            Toggle("Discreet mode", "Blur covers and hide titles in lists; pages in the reader stay sharp", s.blurCovers) { io { st.setBlurCovers(it) } }
            Toggle("Incognito mode", "Don't record reading history", s.incognito) { io { st.setIncognito(it) } }

            Header("Network")
            ListItem(
                headlineContent = { Text("Reset browser verification") },
                supportingContent = { Text("Clear Cloudflare cookies if requests keep failing") },
                modifier = Modifier.clickable { CookieManager.getInstance().removeAllCookies(null); io { st.setCfUserAgent(null) } },
            )
        }
    }
    if (pinDialog) PinSetupDialog(onDismiss = { pinDialog = false }) { pin -> io { st.setPin(pin) }; pinDialog = false }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
        }
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(v.toInt().toString(), color = MaterialTheme.colorScheme.primary) }
        Slider(v, { v = it }, valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.last - range.first - 1,
            onValueChangeFinished = { onChange(v.toInt()) })
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.length >= 4 && a == b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it.filter(Char::isDigit).take(12) }, label = { Text("PIN (4+ digits)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(b, { b = it.filter(Char::isDigit).take(12) }, label = { Text("Repeat PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = b.isNotEmpty() && a != b)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSet(a) }) { Text("Set PIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- Account ----------------

@Composable
fun AccountScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val s by c.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    GlassScaffold(topBar = {
        TopAppBar(colors = glassBarColors(), title = { Text("Account") }, navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Connect with an API key", style = MaterialTheme.typography.titleLarge)
            Text("Online favorites use your nhentai API key. Create one in your account settings on the website, then paste it here. The key is stored only on this device.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${Hosts.BASE}user/settings#apikeys"))) }) {
                Text("Open API key page")
            }
            if (s.apiKey != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Key saved · ••••${s.apiKey!!.takeLast(4)}" + if (s.apiKeyValid) " · verified" else " · not verified",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { scope.launch { c.settings.setApiKey(null, false) } }) { Text("Remove") }
                    }
                }
            }
            OutlinedTextField(key, { key = it.trim() }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(enabled = key.length >= 8 && !busy, onClick = {
                busy = true; status = null
                scope.launch {
                    runCatching { c.api.validateKey(key) }
                        .onSuccess { ok ->
                            if (ok) { c.settings.setApiKey(key, true); key = ""; status = "Connected" }
                            else status = "That key was rejected"
                        }
                        .onFailure { e -> status = if (e is com.nclientnext.data.CloudflareException) "Browser verification needed first" else "Couldn't verify: ${e.message}" }
                    busy = false
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Verify & save") }
            TextButton(enabled = key.length >= 8, onClick = { scope.launch { c.settings.setApiKey(key, false); key = ""; status = "Saved without verifying" } }) {
                Text("Save without verifying")
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

// ---------------- Cloudflare ----------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareScreen() {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(false) }
    GlassScaffold(topBar = {
        TopAppBar(colors = glassBarColors(), 
            title = { Text(if (done) "Verified" else "Browser check") },
            navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { if (done) TextButton(onClick = nav::back) { Text("Done") } },
        )
    }) { pad ->
        AndroidView(
            modifier = Modifier.padding(pad).fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    val ua = settings.userAgentString
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookies = CookieManager.getInstance().getCookie(Hosts.BASE).orEmpty()
                            if ("cf_clearance" in cookies && !done) {
                                done = true
                                CookieManager.getInstance().flush()
                                scope.launch {
                                    c.settings.setCfUserAgent(ua)
                                    delay(600)
                                    nav.back()
                                }
                            }
                        }
                    }
                    loadUrl(Hosts.BASE)
                }
            },
        )
    }
}

// ---------------- Lock ----------------

@Composable
fun LockScreen(onBiometric: (() -> Unit)?, onUnlock: () -> Unit) {
    val c = LocalContainer.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onBiometric?.invoke() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Enter PIN", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Surface(
                        shape = CircleShape,
                        color = if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(14.dp),
                    ) {}
                }
            }
            if (error) Text("Wrong PIN", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(28.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            when (k) {
                                "bio" -> if (onBiometric != null) IconButton(onClick = onBiometric) { Icon(Icons.Outlined.Fingerprint, "Biometric") }
                                "del" -> IconButton(onClick = { pin = pin.dropLast(1); error = false }) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete") }
                                else -> FilledTonalButton(onClick = {
                                    error = false
                                    pin += k
                                    if (pin.length >= 4 && c.settings.checkPin(pin)) onUnlock()
                                    else if (pin.length >= 12) { error = true; pin = "" }
                                }, modifier = Modifier.size(72.dp), shape = CircleShape) { Text(k, style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                    }
                }
            }
            if (pin.length >= 4) TextButton(onClick = { if (c.settings.checkPin(pin)) onUnlock() else { error = true; pin = "" } }) { Text("Unlock") }
        }
    }
}


@Composable
fun AppIconChoice() {
    val ctx = LocalContext.current
    var calc by remember { mutableStateOf(com.nclientnext.ui.IconSwitcher.isCalculator(ctx)) }
    var note by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("App icon", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconOption("Nclient-R", com.nclientnext.R.drawable.ic_launcher_foreground, androidx.compose.ui.graphics.Color.Black, selected = !calc) {
                if (calc) { com.nclientnext.ui.IconSwitcher.setCalculator(ctx, false); calc = false; note = true }
            }
            IconOption("Calculator", com.nclientnext.R.drawable.ic_calc_foreground, androidx.compose.ui.graphics.Color(0xFF3A3A3C), selected = calc) {
                if (!calc) { com.nclientnext.ui.IconSwitcher.setCalculator(ctx, true); calc = true; note = true }
            }
        }
        if (note) Text(
            "Your launcher may take a few seconds to update the icon.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun IconOption(label: String, res: Int, bg: androidx.compose.ui.graphics.Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(72.dp)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) else Modifier)
                .padding(4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(bg),
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(res), label,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
