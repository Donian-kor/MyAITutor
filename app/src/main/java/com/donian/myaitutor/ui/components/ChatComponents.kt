package com.donian.myaitutor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * AI 선생님 아바타 아이콘.
 * 채팅 메시지 옆에 표시하여 "AI 선생님"임을 시각적으로 전달합니다.
 */
@Composable
fun TeacherAvatar(
    modifier: Modifier = Modifier,
    size: Int = 36,
    isThinking: Boolean = false
) {
    val haloAlpha = if (isThinking) {
        val transition = rememberInfiniteTransition(label = "avatar-halo")
        val animated by transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "avatar-halo-alpha"
        )
        animated
    } else {
        0.2f
    }

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        // 후광
        Box(
            modifier = Modifier
                .size((size + 8).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha))
        )
        // 아바타 원
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(size.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.robot_tutor),
                    contentDescription = "AI 선생님",
                    modifier = Modifier.size((size * 0.6f).dp)
                )
            }
        }
    }
}

/**
 * AI 선생님 메시지 버블 - 개선된 디자인.
 * 답변 내용이 잘리지 않도록 충분한 여백 제공.
 * 배경색으로 AI 답변과 사용자 질문을 구분.
 */
@Composable
fun TeacherMessageBubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        TeacherAvatar(size = 32)
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer,
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeacherAvatar(size = 32, isThinking = true)
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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