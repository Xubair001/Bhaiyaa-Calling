package com.codeaza.bhaiyaaa

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.codeaza.bhaiyaaa.ui.theme.BhaiyaaaTheme

/**
 * FragmentActivity rather than ComponentActivity: BiometricPrompt requires a
 * FragmentActivity host to survive configuration changes.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BhaiyaaaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text("BHAIYAAA")
                }
            }
        }
    }
}
