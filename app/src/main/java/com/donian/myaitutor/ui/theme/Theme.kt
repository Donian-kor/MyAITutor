package com.donian.myaitutor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun FocusAiTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF245B9B),
        onPrimary = Color.White,
        secondary = Color(0xFF4D6F91),
        background = Color(0xFFF4F6FA),
        surface = Color.White,
        onSurface = Color(0xFF182232),
        onSurfaceVariant = Color(0xFF657386),
        outlineVariant = Color(0xFFDDE3EC)
    )
    MaterialTheme(colorScheme = colors, content = content)
}