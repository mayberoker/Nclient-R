package com.nclientnext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nclientnext.data.SearchParams
import com.nclientnext.data.Tag
import com.nclientnext.data.TagType
import com.nclientnext.ui.AppNavigation
import com.nclientnext.ui.Routes
import com.nclientnext.ui.components.LocalContainer
import com.nclientnext.ui.screens.LockScreen
import com.nclientnext.ui.theme.NClientTheme

class MainActivity : FragmentActivity() {

    /** Set by the reader to capture volume keys. */
    var keyHandler: ((Int) -> Boolean)? = null

    private val container get() = (application as App).container
    private val locked = mutableStateOf(false)
    private val deepLink = mutableStateOf<Any?>(null)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked.value = container.settings.current.locked
        if (savedInstanceState == null) handleIntent(intent)
        askNotificationPermission()
        // In calculator disguise, the recents screen shows "Calculator" too
        if (com.nclientnext.ui.IconSwitcher.isCalculator(this)) {
            @Suppress("DEPRECATION")
            setTaskDescription(android.app.ActivityManager.TaskDescription(getString(R.string.calculator_name)))
        }

        setContent {
            val settings by container.settings.flow.collectAsState()
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            NClientTheme(settings) {
              com.nclientnext.ui.theme.AuroraBackground {
                if (locked.value && settings.locked) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalContainer provides container) {
                        LockScreen(
                            onBiometric = if (settings.biometric && canUseBiometric()) ({ showBiometric() }) else null,
                            onUnlock = { locked.value = false },
                        )
                    }
                } else {
                    AppNavigation(container, deepLink.value, onDeepLinkHandled = { deepLink.value = null })
                }
              }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after 30 s in the background (short trips to file pickers don't re-lock)
        if (container.settings.current.locked && stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 30_000) {
            locked.value = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val h = keyHandler
        if (h != null && (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.action == KeyEvent.ACTION_DOWN) h(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("""https?://\S+""").find(text)?.value?.let(Uri::parse)
                    ?: Regex("""\b(\d{1,7})\b""").find(text)?.groupValues?.get(1)?.let { Uri.parse("https://nhentai.net/g/$it/") }
            }
            else -> null
        }
        deepLink.value = uri?.let(::routeFor) ?: return
    }

    private fun routeFor(uri: Uri): Any? {
        val seg = uri.pathSegments
        if (seg.isEmpty()) return null
        return when (seg[0]) {
            "g" -> seg.getOrNull(1)?.toIntOrNull()?.let { Routes.Gallery(it) }
            "search" -> uri.getQueryParameter("q")?.let { Routes.Search(container.repository.encodeParams(SearchParams(query = it))) }
            "tag", "artist", "parody", "character", "group" -> seg.getOrNull(1)?.let { slug ->
                val name = slug.replace('-', ' ')
                Routes.Search(container.repository.encodeParams(SearchParams(include = listOf(Tag(-slug.hashCode(), name, TagType.byKey(seg[0]))))))
            }
            else -> null
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("app", MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notif", false)) {
                prefs.edit().putBoolean("asked_notif", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    private fun canUseBiometric() = BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometric() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked.value = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
}
