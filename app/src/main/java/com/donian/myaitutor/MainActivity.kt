package com.donian.myaitutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.donian.myaitutor.ui.FocusAiApp
import com.donian.myaitutor.ui.theme.FocusAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusAiTheme {
                FocusAiApp()
            }
        }
    }
}