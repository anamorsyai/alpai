package com.anamorsyai.alpai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.anamorsyai.alpai.ui.navigation.AlpAiNavHost
import com.anamorsyai.alpai.ui.theme.AlpAiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AlpAiApp
        setContent {
            val themeMode by app.settingsRepository.themeMode.collectAsState(
                initial = com.anamorsyai.alpai.data.repository.ThemeMode.SYSTEM,
            )
            AlpAiTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlpAiNavHost(app = app)
                }
            }
        }
    }
}
