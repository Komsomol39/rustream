package com.komsomol.rustream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.komsomol.rustream.ui.navigation.AppNavGraph
import com.komsomol.rustream.ui.theme.RuStreamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RuStreamTheme {
                AppNavGraph()
            }
        }
    }
}
