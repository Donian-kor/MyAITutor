package com.donian.myaitutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.donian.myaitutor.ui.FocusAiApp
import com.donian.myaitutor.ui.FocusAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusAiTheme {
                FocusAiApp()
            }
        }
    }
}
