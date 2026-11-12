package com.donian.myaitutor.ui.camera

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.donian.myaitutor.camera.CameraPreview
import com.donian.myaitutor.camera.CaptureImageAction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CameraScreen(
    capturedImage: File?,
    captureAction: CaptureImageAction?,
    cameraError: String?,
    onCaptureActionReady: (CaptureImageAction?) -> Unit,
    onCameraError: (String) -> Unit,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    val view = LocalView.current
    val activity = view.context as? Activity
    // 미리보기 디코딩은 메인 스레드에서 하지 않는다. (R-04)
    val capturedPreview by produceState<CapturedPreview?>(initialValue = null, capturedImage) {
        val file = capturedImage
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodePreview(file) }
        }
    }
    val preview = capturedPreview

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            if (previousLightStatusBars != null) {
                controller?.isAppearanceLightStatusBars = previousLightStatusBars
            }
            if (previousLightNavigationBars != null) {
                controller?.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (capturedImage == null) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onCaptureReady = onCaptureActionReady,
                onCameraError = onCameraError
            )
        } else if (preview != null) {
            Image(
                bitmap = preview.bitmap,
                contentDescription = "촬영한 문제 미리보기",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = preview.rotation
                        scaleX = preview.scaleX
                    },
                contentScale = ContentScale.Fit
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "카메라 닫기",
                        tint = Color.White
                    )
                }
                Text(
                    if (capturedImage == null) "문제 촬영" else "사진 확인",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(1f))

            if (cameraError != null) {
                Text(
                    cameraError,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            if (capturedImage == null) {
                Button(
                    onClick = onCapture,
                    enabled = captureAction != null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 20.dp)
                        .navigationBarsPadding()
                        .size(76.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "사진 촬영",
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("다시 촬영")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "촬영 확인")
                        Spacer(Modifier.width(6.dp))
                        Text("확인")
                    }
                }
            }
        }
    }
}

/** 촬영 미리보기용 디코딩 결과. */
private data class CapturedPreview(
    val bitmap: ImageBitmap,
    val rotation: Float,
    val scaleX: Float
)

/** 미리보기 최대 장변(px). 원본 해상도 그대로 로드하지 않아 OOM을 막는다. */
private const val PREVIEW_MAX_SIDE_PX = 1600

/**
 * 촬영 이미지를 미리보기 크기로 디코딩합니다. 반드시 IO 스레드에서 호출할 것. (R-04)
 */
private fun decodePreview(file: File): CapturedPreview? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_MAX_SIDE_PX) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

    val orientation = ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    val (rotation, scaleX) = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f to 1f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f to 1f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f to 1f
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to -1f
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to -1f
        ExifInterface.ORIENTATION_TRANSPOSE -> 90f to -1f
        ExifInterface.ORIENTATION_TRANSVERSE -> 270f to -1f
        else -> 0f to 1f
    }
    return CapturedPreview(bitmap.asImageBitmap(), rotation, scaleX)
}
