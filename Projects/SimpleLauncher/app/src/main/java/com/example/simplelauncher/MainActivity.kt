package com.example.simplelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.example.simplelauncher.ui.theme.screen.HomeScreen
import com.example.simplelauncher.ui.theme.simplelauncherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            simplelauncherTheme {
                // Keep the screen on while using the launcher (it's a "tool")
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                )
                MaterialTheme.colorScheme.let { /* no-op, ensures theme applied */ }
                HomeScreen()
            }
        }
    }
}
