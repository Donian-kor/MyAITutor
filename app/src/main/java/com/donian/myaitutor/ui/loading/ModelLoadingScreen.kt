package com.donian.myaitutor.ui.loading

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * 앱 시작 시 모델 준비 상태를 보여주는 첫 화면.
 *
 * AI 선생님이 수업을 준비하는 느낌으로 디자인되었습니다.
 * - 선생님 아바타와 말풍선으로 준비 메시지 표시
 * - 하단에 진행 표시와 액션 버튼
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
    val isWorking = phase == ModelLoadingPhase.READY ||
        phase == ModelLoadingPhase.DOWNLOADING ||
        phase == ModelLoadingPhase.VERIFYING ||
        phase == ModelLoadingPhase.ENGINE_LOADING

    val haloAlpha = if (isWorking) {
        val transition = rememberInfiniteTransition(label = "loading-halo")
        val animated by transition.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading-halo-alpha"
        )
        animated
    } else {
        0.12f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.primaryContainer.copy(alpha = 0.3f),
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp)
        ) {
            // 선생님 아바타 + 후광
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .alpha(haloAlpha)
                        .background(colors.primary, CircleShape)
                )
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.robot_tutor),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // 선생님 말풍선
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                color = colors.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.loading_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    phaseDescription(phase)?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            LoadingProgress(phase = phase, snapshot = snapshot)

            if (phase == ModelLoadingPhase.FAILED && snapshot.message.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = snapshot.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }

            Spacer(Modifier.height(36.dp))

            LoadingActions(
                phase = phase,
                onDownload = onDownload,
                onRetry = onRetry,
                onSkip = onSkip
            )
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
    val trackColor = colors.outlineVariant.copy(alpha = 0.45f)

    when (phase) {
        ModelLoadingPhase.DOWNLOADING -> {
            LinearProgressIndicator(
                progress = { snapshot.progress },
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .height(8.dp),
                color = colors.primary,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.loading_progress_percent,
                    (snapshot.progress * 100).toInt()
                ),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (snapshot.totalBytes > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.loading_progress_bytes,
                        snapshot.downloadedLabel,
                        snapshot.totalLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        ModelLoadingPhase.VERIFYING,
        ModelLoadingPhase.ENGINE_LOADING,
        ModelLoadingPhase.READY -> {
            LinearProgressIndicator(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .height(8.dp),
                color = colors.primary,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.loading_progress_wait),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (phase) {
            ModelLoadingPhase.DOWNLOAD_REQUIRED -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.loading_btn_download))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.loading_btn_skip))
                }
            }

            ModelLoadingPhase.FAILED -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.loading_btn_retry))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.loading_btn_skip))
                }
            }

            ModelLoadingPhase.DOWNLOADING,
            ModelLoadingPhase.VERIFYING,
            ModelLoadingPhase.ENGINE_LOADING,
            ModelLoadingPhase.READY -> {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.loading_btn_skip))
                }
            }
        }
    }
}