package com.donian.myaitutor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 나만의 AI 쌤 앱 테마.
 *
 * 따뜻하고 친근한 교실 느낌의 색상 팔레트를 사용합니다.
 * 파랑 계열을 기본으로 하되, 선생님의 따뜻함을 전달하기 위해
 * 부드러운 톤과 둥근 형태를 강조합니다.
 */
@Composable
fun FocusAiTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        // 기본 색상 - 선생님의 신뢰감 있는 파랑
        primary = Color(0xFF2B6CB0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE8F7),
        onPrimaryContainer = Color(0xFF1A3A5C),
        // 보조 색상 - 따뜻한 하늘색
        secondary = Color(0xFF4A7FA5),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8F2F9),
        onSecondaryContainer = Color(0xFF1E3A50),
        // 배경 - 부드러운 아이보리 톤
        background = Color(0xFFF7F9FC),
        onBackground = Color(0xFF1A2332),
        surface = Color.White,
        onSurface = Color(0xFF1A2332),
        onSurfaceVariant = Color(0xFF5A6B80),
        // 윤곽선 - 부드러운 회색
        outline = Color(0xFFB8C4D4),
        outlineVariant = Color(0xFFDCE3EC),
        // 오류 색상
        error = Color(0xFFD93025),
        errorContainer = Color(0xFFFCE8E6),
        onErrorContainer = Color(0xFF8B1A12),
        // 역할 색상 (AI 선생님 메시지)
        tertiary = Color(0xFF5B8A72),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE5F0E9),
        onTertiaryContainer = Color(0xFF1E3D2A)
    )
    MaterialTheme(colorScheme = colors, content = content)
}