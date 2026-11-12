package com.donian.myaitutor.ui.camera

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.donian.myaitutor.camera.CameraPreview
import com.donian.myaitutor.R
import com.donian.myaitutor.camera.CaptureImageAction
import com.donian.myaitutor.camera.ExifImageTransform
import com.donian.myaitutor.camera.ImageSelection
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
    onConfirm: (ImageSelection?) -> Unit
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
    var selection by remember(capturedImage) { mutableStateOf<ImageSelection?>(null) }

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
                contentDescription = stringResource(R.string.content_desc_captured_preview),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            ImageSelectionOverlay(
                imageWidth = preview.bitmap.width,
                imageHeight = preview.bitmap.height,
                selection = selection,
                onSelectionChanged = { selection = it }
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
                        contentDescription = stringResource(R.string.content_desc_close_camera),
                        tint = Color.White
                    )
                }
                Text(
                    if (capturedImage == null) stringResource(R.string.camera_title_capture) else stringResource(R.string.camera_title_confirm),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (capturedImage != null && selection != null) {
                    TextButton(onClick = { selection = null }) {
                        Text(stringResource(R.string.camera_btn_select_all), color = Color.White)
                    }
                }
            }

            if (capturedImage != null) {
                Text(
                    stringResource(R.string.camera_selection_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
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
                        contentDescription = stringResource(R.string.content_desc_take_photo),
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
                    Button(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.65f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.camera_btn_retake), fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { onConfirm(selection) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.content_desc_capture_confirm))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.camera_btn_confirm))
                    }
                }
            }
        }
    }
}

/** 촬영 미리보기용 디코딩 결과. */
private data class CapturedPreview(
    val bitmap: ImageBitmap
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
    val oriented = ExifImageTransform.fromOrientation(orientation).apply(bitmap)
    if (oriented !== bitmap) bitmap.recycle()
    return CapturedPreview(oriented.asImageBitmap())
}

@Composable
private fun ImageSelectionOverlay(
    imageWidth: Int,
    imageHeight: Int,
    selection: ImageSelection?,
    onSelectionChanged: (ImageSelection?) -> Unit
) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageWidth, imageHeight) {
                val scale = minOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
                val displayWidth = imageWidth * scale
                val displayHeight = imageHeight * scale
                val imageBounds = Rect(
                    (size.width - displayWidth) / 2f,
                    (size.height - displayHeight) / 2f,
                    (size.width + displayWidth) / 2f,
                    (size.height + displayHeight) / 2f
                )
                var dragStart: Offset? = null
                var dragEnd: Offset? = null

                fun selectionFor(start: Offset, end: Offset): ImageSelection? {
                    val startX = (start.x - imageBounds.left).coerceIn(0f, displayWidth)
                    val startY = (start.y - imageBounds.top).coerceIn(0f, displayHeight)
                    val endX = (end.x - imageBounds.left).coerceIn(0f, displayWidth)
                    val endY = (end.y - imageBounds.top).coerceIn(0f, displayHeight)
                    return ImageSelection.fromPoints(
                        startX, startY, endX, endY, displayWidth, displayHeight
                    )
                }

                detectDragGestures(
                    onDragStart = { point ->
                        if (imageBounds.contains(point)) {
                            dragStart = point
                            dragEnd = point
                        }
                    },
                    onDrag = { change, _ ->
                        val start = dragStart
                        if (start != null) {
                            change.consume()
                            dragEnd = change.position
                            onSelectionChanged(selectionFor(start, change.position))
                        }
                    },
                    onDragEnd = {
                        val start = dragStart
                        val end = dragEnd
                        if (start != null && end != null) {
                            onSelectionChanged(selectionFor(start, end))
                        }
                        dragStart = null
                        dragEnd = null
                    },
                    onDragCancel = {
                        dragStart = null
                        dragEnd = null
                    }
                )
            }
            .drawWithCache {
                val scale = minOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
                val displayWidth = imageWidth * scale
                val displayHeight = imageHeight * scale
                val imageBounds = Rect(
                    (size.width - displayWidth) / 2f,
                    (size.height - displayHeight) / 2f,
                    (size.width + displayWidth) / 2f,
                    (size.height + displayHeight) / 2f
                )
                onDrawBehind {
                    val region = selection ?: return@onDrawBehind
                    val left = imageBounds.left + region.left * displayWidth
                    val top = imageBounds.top + region.top * displayHeight
                    val right = imageBounds.left + region.right * displayWidth
                    val bottom = imageBounds.top + region.bottom * displayHeight
                    val shade = Color.Black.copy(alpha = 0.48f)
                    drawRect(shade, Offset(imageBounds.left, imageBounds.top), androidx.compose.ui.geometry.Size(imageBounds.width, top - imageBounds.top))
                    drawRect(shade, Offset(imageBounds.left, bottom), androidx.compose.ui.geometry.Size(imageBounds.width, imageBounds.bottom - bottom))
                    drawRect(shade, Offset(imageBounds.left, top), androidx.compose.ui.geometry.Size(left - imageBounds.left, bottom - top))
                    drawRect(shade, Offset(right, top), androidx.compose.ui.geometry.Size(imageBounds.right - right, bottom - top))
                    drawRect(
                        color = Color(0xFF80CBC4),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
    )
}
