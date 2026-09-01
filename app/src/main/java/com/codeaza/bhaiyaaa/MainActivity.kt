package com.codeaza.bhaiyaaa

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeaza.bhaiyaaa.notifications.Notifier
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.navigation.SukoonApp
import com.codeaza.bhaiyaaa.ui.theme.SukoonTheme

/**
 * FragmentActivity rather than ComponentActivity: BiometricPrompt requires a
 * FragmentActivity host to survive configuration changes.
 *
 * Deliberately thin - it installs the splash screen, applies the theme the user
 * chose, and hands off to Compose. All logic lives in ViewModels.
 */
class MainActivity : FragmentActivity() {

    /**
     * A number whose latest call should be opened, from a call-note prompt.
     *
     * Held as state rather than read from the intent inside Compose: the
     * activity is `singleTask`, so a second tap arrives at [onNewIntent] with
     * the composition already running, and reading `intent` during composition
     * would miss it entirely.
     */
    private var pendingNoteNumber by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNoteNumber = intent.noteNumber()
    }

    private fun Intent?.noteNumber(): String? =
        this?.getStringExtra(Notifier.EXTRA_NOTE_FOR_NUMBER)?.takeIf { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNoteNumber = intent.noteNumber()

        setContent {
            val viewModel: SukoonViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Re-lock when the app leaves the foreground, so returning to it
            // asks for the PIN again rather than showing the VIP list.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) viewModel.relockIfEnabled()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            SukoonTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SukoonApp(
                        viewModel = viewModel,
                        pendingNoteNumber = pendingNoteNumber,
                        onPendingNoteHandled = { pendingNoteNumber = null }
                    )
                }
            }
        }
    }
}
