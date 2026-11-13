package com.donian.myaitutor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.BuildConfig
import com.donian.myaitutor.R
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.components.TeacherAvatar
import com.donian.myaitutor.ui.components.InfoCard

@Composable
internal fun SettingsScreen(
    snapshot: ModelDownloadSnapshot,
    backendLabel: String,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onClearCache: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    val isBusy = snapshot.status == ModelDownloadStatus.DOWNLOADING ||
        snapshot.status == ModelDownloadStatus.QUEUED ||
        snapshot.status == ModelDownloadStatus.PAUSED ||
        snapshot.status == ModelDownloadStatus.VERIFYING

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
            // 상단 헤더 - AI 선생님 프로필
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back), modifier = Modifier.size(25.dp))
                }
                TeacherAvatar(size = 36)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "AI 선생님 설정",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // AI 선생님 모델 카드
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(painter = painterResource(R.drawable.robot_tutor), contentDescription = null, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemma 4 E4B-IT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.settings_engine_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_model_status), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (snapshot.status) {
                                ModelDownloadStatus.READY -> stringResource(R.string.model_status_ready)
                                ModelDownloadStatus.DOWNLOADING -> stringResource(R.string.model_status_downloading)
                                ModelDownloadStatus.QUEUED -> stringResource(R.string.model_status_queued)
                                ModelDownloadStatus.PAUSED -> stringResource(R.string.model_status_paused)
                                ModelDownloadStatus.VERIFYING -> stringResource(R.string.model_status_verifying)
                                ModelDownloadStatus.FAILED -> stringResource(R.string.model_status_failed)
                                ModelDownloadStatus.NOT_INSTALLED -> stringResource(R.string.model_status_not_installed)
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
                            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.content_desc_model_installed), tint = Color(0xFF16845B))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.settings_model_found), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.settings_btn_model_delete))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.settings_btn_model_delete))
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            enabled = snapshot.status != ModelDownloadStatus.DOWNLOADING,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.settings_btn_download))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (snapshot.status) {
                                    ModelDownloadStatus.FAILED -> stringResource(R.string.settings_btn_download_retry)
                                    ModelDownloadStatus.PAUSED, ModelDownloadStatus.QUEUED -> stringResource(R.string.settings_btn_download_resume)
                                    else -> stringResource(R.string.settings_btn_download)
                                }
                            )
                        }
                    }
                }
            }

            // 설정 액션 버튼들
            OutlinedButton(
                onClick = onClearCache,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.settings_btn_clear_cache))
            }

            OutlinedButton(
                onClick = { showLicensesDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.settings_btn_licenses))
            }

            // 정보 카드들
            InfoCard(
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.settings_ocr_title),
                body = stringResource(R.string.settings_ocr_details)
            )
            InfoCard(
                icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.info_download_title),
                body = stringResource(R.string.info_download_body)
            )
            InfoCard(
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.info_learning_title),
                body = stringResource(R.string.info_learning_body)
            )
            InfoCard(
                icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.info_privacy_title),
                body = stringResource(R.string.info_privacy_body)
            )

            // 버전 정보
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.settings_app_version,
                            BuildConfig.VERSION_NAME
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.settings_runtime_env), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_current_backend, backendLabel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_model_title)) },
            text = { Text(stringResource(R.string.dialog_delete_model_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteModel()
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(stringResource(R.string.dialog_licenses_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_licenses_content,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text(stringResource(R.string.btn_dismiss)) }
            }
        )
    }
}