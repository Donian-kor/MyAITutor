package com.donian.myaitutor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.donian.myaitutor.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TeacherAvatar(
    modifier: Modifier = Modifier,
    size: Int = 36
) {
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(size.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ai_chat_icon),
                contentDescription = "AI 선생님",
                modifier = Modifier.padding((size * 0.05f).dp)
            )
        }
    }
}

/**
 * AI 선생님 메시지 버블 - 개선된 디자인.
 * 새 AI 채팅 아이콘 + "나만의 AI쌤 - 과목명" 라벨 표시.
 * 흰색 배경 + 두꺼운 테두리로 학생 답변과 구분.
 */
@Composable
fun TeacherMessageBubble(
    subject: String = "",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI 채팅 아이콘
        Image(
            painter = painterResource(R.drawable.ai_chat_icon),
            contentDescription = "AI 선생님",
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(10.dp))

        // 아이콘 하단에서 시작하는 답변 카드
        Column(modifier = Modifier.weight(1f, fill = false)) {
            // 라벨: 나만의 AI쌤 - 과목명
            if (subject.isNotBlank()) {
                Text(
                    text = "나만의 AI쌤 - $subject",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * 사용자 메시지 버블 - 개선된 디자인.
 * AI 답변과 배경색으로 명확히 구분 (연한 그린 계열).
 */
@Composable
fun UserMessageBubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 6.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 1.dp,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                content()
            }
        }
    }
}

/**
 * AI 선생님이 생각 중일 때 표시하는 타이핑 인디케이터.
 * 세 개의 점이 순차적으로 나타났다 사라지는 애니메이션.
 */
@Composable
fun TypingIndicator(
    subject: String = "",
    modifier: Modifier = Modifier
) {
    TeacherMessageBubble(
        subject = subject,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            repeat(3) { index ->
                val animatedAlpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = index * 200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot-$index"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = animatedAlpha)
                        )
                )
            }
        }
    }
}

/**
 * 선생님 인사 메시지 카드.
 * 앱 시작 시 또는 빈 상태일 때 표시합니다.
 */
@Composable
fun TeacherGreetingCard(
    message: String,
    modifier: Modifier = Modifier
) {
    TeacherMessageBubble(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}