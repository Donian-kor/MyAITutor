package com.donian.myaitutor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.components.InfoCard

@Composable
internal fun SettingsScreen(
    snapshot: ModelDownloadSnapshot,
    backendLabel: String,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onDeleteModel: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isBusy = snapshot.status == ModelDownloadStatus.DOWNLOADING ||
        snapshot.status == ModelDownloadStatus.QUEUED || snapshot.status == ModelDownloadStatus.PAUSED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", modifier = Modifier.size(25.dp))
                }
                Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemma 4 E4B-IT", fontWeight = FontWeight.Bold)
                            Text("LiteRT-LM · 온디바이스 멀티모달 AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("모델 상태", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (snapshot.status) {
                                ModelDownloadStatus.READY -> "설치 완료"
                                ModelDownloadStatus.DOWNLOADING -> "다운로드 중"
                                ModelDownloadStatus.QUEUED -> "대기 중"
                                ModelDownloadStatus.PAUSED -> "일시 중지"
                                ModelDownloadStatus.FAILED -> "오류"
                                ModelDownloadStatus.NOT_INSTALLED -> "미설치"
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = if (snapshot.status == ModelDownloadStatus.READY) Color(0xFF16845B) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(snapshot.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isBusy || snapshot.status == ModelDownloadStatus.READY) {
                        LinearProgressIndicator(
                            progress = { if (snapshot.status == ModelDownloadStatus.READY) 1f else snapshot.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${snapshot.downloadedLabel} / ${snapshot.totalLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (snapshot.status == ModelDownloadStatus.READY) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16845B))
                            Spacer(Modifier.width(7.dp))
                            Text("모델 파일을 찾았습니다. AI 분석을 바로 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("모델 삭제")
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (snapshot.status == ModelDownloadStatus.FAILED) "다시 다운로드" else "AI 모델 다운로드")
                        }
                    }
                }
            }

            InfoCard(
                icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "다운로드 안내",
                body = "모델 파일은 약 3.66GB입니다. 요금이 발생할 수 있으니 반드시 Wi-Fi에서 다운로드하세요. 다운로드가 끝나면 AI 추론은 기기 안에서만 실행됩니다."
            )
            InfoCard(
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "AI 생성기 학습 방식",
                body = "개념 설명 → 핵심 원리 → 문제 적용 → 계산 단계 → 최종 답안 순으로 검산까지 설명합니다. 답변에 추가 질문을 이어서 학습할 수 있습니다."
            )
            InfoCard(
                icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "개인정보 오프라인 처리",
                body = "문제 촬영과 분석은 모두 기기 안에서 처리되며 외부 서버로 전송되지 않습니다. AI 분석 요청과 추가 질문도 같은 방식으로 처리됩니다. 최초 모델 다운로드 및 업데이트에만 인터넷이 필요합니다."
            )
            Text("나만의 AI쌤 0.0.1 · Gemma 4 E4B-IT LiteRT-LM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("실행 환경: 기기 메모리에 맞춰 모델 로딩과 추론이 최적화됩니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("현재 백엔드: $backendLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("AI 모델을 삭제할까요?") },
            text = { Text("약 3.66GB의 모델 파일을 기기에서 모두 삭제합니다. 다시 사용하려면 재다운로드가 필요합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteModel()
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }
}