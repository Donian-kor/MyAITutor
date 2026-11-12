package com.donian.myaitutor.ui.camera

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val capturedBitmap = remember(capturedImage) {
        capturedImage?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    val capturedImageTransform = remember(capturedImage) {
        val orientation = capturedImage?.let {
            android.media.ExifInterface(it.absolutePath).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL

        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f to 1f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f to 1f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f to 1f
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to -1f
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to -1f
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> 90f to -1f
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> 270f to -1f
            else -> 0f to 1f
        }
    }

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
        } else if (capturedBitmap != null) {
            Image(
                bitmap = capturedBitmap,
                contentDescription = "촬영한 문제 미리보기",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = capturedImageTransform.first
                        scaleX = capturedImageTransform.second
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
                        Icons.Default.ArrowBack,
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
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("확인")
                    }
                }
            }
        }
    }
}