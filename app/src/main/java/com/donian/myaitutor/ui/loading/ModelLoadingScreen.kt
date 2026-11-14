package com.donian.myaitutor.ui.loading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.ui.components.TeacherAvatar

/**
 * 앱 시작 시 모델 준비 상태를 보여주는 첫 화면 - 대폭 개선된 디자인.
 *
 * 개선 사항:
 * - Figma 디자인 시안 기반의 세련된 그라데이션 배경
 * - 개선된 타이포그래피 위계 (테마 기반)
 * - 더 부드러운 애니메이션 (이징 곡선 + 페이드 전환)
 * - 개선된 명암비와 접근성
 * - 등장 애니메이션 추가
 */
@Composable
fun ModelLoadingScreen(
    phase: ModelLoadingPhase,
    snapshot: ModelDownloadSnapshot,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val isWorking = phase == ModelLoadingPhase.READY ||
        phase == ModelLoadingPhase.DOWNLOADING ||
        phase == ModelLoadingPhase.VERIFYING ||
        phase == ModelLoadingPhase.ENGINE_LOADING

    // 등장 애니메이션 상태
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val haloAlpha = if (isWorking) {
        val transition = rememberInfiniteTransition(label = "loading-halo")
        val animated by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2_000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading-halo-alpha"
        )
        animated
    } else {
        0.2f
    }

    val avatarScale = if (isWorking) {
        val transition = rememberInfiniteTransition(label = "loading-scale")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2_000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading-scale-value"
        )
        animated
    } else {
        1f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.primaryContainer.copy(alpha = 0.6f),
                        colors.background,
                        colors.surface
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = 16.dp)
        ) {
            // 선생님 아바타 + 후광 - 개선된 애니메이션
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = tween(600)
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(y = (-5).dp)
                        .scale(avatarScale)
                ) {
                    // 외곽 후광 (아이콘 크기에 비례)
                    Box(
                        modifier = Modifier
                            .size(255.dp)
                            .alpha(haloAlpha * 0.5f)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        colors.primary.copy(alpha = 0.5f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    // 중간 후광
                    Box(
                        modifier = Modifier
                            .size(204.dp)
                            .alpha(haloAlpha * 0.8f)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        colors.primary.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    // 아바타 서페이스 - 아이콘(180dp)보다 10dp 크게
                    Surface(
                        shape = shapes.extraLarge,
                        color = colors.surface,
                        shadowElevation = 20.dp,
                        modifier = Modifier.size(162.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.robot_tutor),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.size(153.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            // 선생님 말풍선
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) + slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = tween(600, delayMillis = 200)
                )
            ) {
                Surface(
                    shape = shapes.extraLarge,
                    color = colors.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .wrapContentWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.loading_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(12.dp))

                        phaseDescription(phase)?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 진행 표시 영역
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 400)),
                exit = fadeOut()
            ) {
                LoadingProgress(phase = phase, snapshot = snapshot)
            }

            // 에러 메시지
            AnimatedVisibility(
                visible = visible && phase == ModelLoadingPhase.FAILED && snapshot.message.isNotBlank(),
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(300)
                ),
                exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                    targetOffsetY = { it / 3 },
                    animationSpec = tween(200)
                )
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        shape = shapes.medium,
                        color = colors.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        Text(
                            text = snapshot.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 액션 버튼
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 600)),
                exit = fadeOut()
            ) {
                LoadingActions(
                    phase = phase,
                    onDownload = onDownload,
                    onRetry = onRetry,
                    onSkip = onSkip
                )
            }
        }
    }
}

@Composable
private fun phaseDescription(phase: ModelLoadingPhase): String? = when (phase) {
    ModelLoadingPhase.DOWNLOAD_REQUIRED ->
        stringResource(R.string.loading_desc_download_required)

    ModelLoadingPhase.DOWNLOADING ->
        stringResource(R.string.loading_desc_downloading)

    ModelLoadingPhase.VERIFYING ->
        stringResource(R.string.loading_desc_verifying)

    ModelLoadingPhase.ENGINE_LOADING -> null

    ModelLoadingPhase.READY ->
        stringResource(R.string.loading_desc_ready)

    ModelLoadingPhase.FAILED ->
        stringResource(R.string.loading_desc_failed)
}

@Composable
private fun LoadingProgress(
    phase: ModelLoadingPhase,
    snapshot: ModelDownloadSnapshot
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val trackColor = colors.outlineVariant.copy(alpha = 0.6f)

    when (phase) {
        ModelLoadingPhase.DOWNLOADING -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { snapshot.progress },
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .height(12.dp)
                        .clip(shapes.small),
                    color = colors.primary,
                    trackColor = trackColor,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = stringResource(
                        R.string.loading_progress_percent,
                        (snapshot.progress * 100).toInt()
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold
                )
                if (snapshot.totalBytes > 0L) {
                    Text(
                        text = stringResource(
                            R.string.loading_progress_bytes,
                            snapshot.downloadedLabel,
                            snapshot.totalLabel
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        ModelLoadingPhase.VERIFYING,
        ModelLoadingPhase.ENGINE_LOADING,
        ModelLoadingPhase.READY -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .height(12.dp)
                        .clip(shapes.small),
                    color = colors.primary,
                    trackColor = trackColor,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = stringResource(R.string.loading_progress_wait),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        ModelLoadingPhase.DOWNLOAD_REQUIRED,
        ModelLoadingPhase.FAILED -> {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LoadingActions(
    phase: ModelLoadingPhase,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    val shapes = MaterialTheme.shapes

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (phase) {
            ModelLoadingPhase.DOWNLOAD_REQUIRED -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .widthIn(min = 240.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = shapes.large
                ) {
                    Text(
                        stringResource(R.string.loading_btn_download),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.loading_btn_skip),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            ModelLoadingPhase.FAILED -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .widthIn(min = 240.dp)
                        .height(56.dp),
                    shape = shapes.large
                ) {
                    Text(
                        stringResource(R.string.loading_btn_retry),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.loading_btn_skip),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            ModelLoadingPhase.DOWNLOADING,
            ModelLoadingPhase.VERIFYING,
            ModelLoadingPhase.ENGINE_LOADING,
            ModelLoadingPhase.READY -> {
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.loading_btn_skip),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}