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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/**
 * 앱 시작 시 모델 준비 상태를 보여주는 첫 화면.
 *
 * - 중앙에 앱 아이콘을 크게 배치합니다.
 * - 하단에 "선생님이 수업을 준비하고 있어요" 문구와 진행 표시를 둡니다.
 * - 다운로드 중에는 실제 진행률을, 엔진 로딩/검증 중에는 무한 진행 표시를 사용합니다.
 * - 준비가 끝나면 [com.donian.myaitutor.ui.FocusAiApp]이 메인 화면으로 전환합니다.
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

    // 준비 중에는 아이콘 뒤 은은한 후광이 천천히 호흡하도록 합니다.
    val haloAlpha = if (isWorking) {
        val transition = rememberInfiniteTransition(label = "loading-halo")
        val animated by transition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.24f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading-halo-alpha"
        )
        animated
    } else {
        0.10f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
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
            Box(contentAlignment = Alignment.Center) {
                // 아이콘 뒤 후광
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .alpha(haloAlpha)
                        .background(colors.primary, CircleShape)
                )
                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = Color.White,
                    shadowElevation = 14.dp,
                    modifier = Modifier.size(148.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_icon),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            Text(
                text = stringResource(R.string.loading_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            phaseDescription(phase)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }

            Spacer(Modifier.height(36.dp))

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

            Spacer(Modifier.height(40.dp))

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
                    )
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
                        .height(48.dp)
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
