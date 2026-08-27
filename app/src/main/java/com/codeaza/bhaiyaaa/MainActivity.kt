package com.codeaza.bhaiyaaa

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.codeaza.bhaiyaaa.ui.navigation.BhaiyaaaApp
import com.codeaza.bhaiyaaa.ui.theme.BhaiyaaaTheme

// FragmentActivity (not plain ComponentActivity) because BiometricPrompt
// requires a FragmentActivity/Fragment host to survive configuration changes.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BhaiyaaaTheme {
                BhaiyaaaApp()
            }
        }
    }
}
