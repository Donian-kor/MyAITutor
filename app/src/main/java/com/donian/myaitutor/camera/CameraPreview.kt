package com.donian.myaitutor.camera

import android.content.Context
import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/** ImageCapture callback: destination file + result callback. */
typealias CaptureImageAction = (File, (Result<File>) -> Unit) -> Unit

@Composable
@SuppressLint("WrongConstant")
fun CameraPreview(
    modifier: Modifier = Modifier,
    onCaptureReady: (CaptureImageAction?) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(context, lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val mainHandler = Handler(Looper.getMainLooper())
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        fun currentRotation(): Int {
            val rotation = previewView.display?.rotation
                ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
                ?: Surface.ROTATION_0
            return when (rotation) {
                Surface.ROTATION_90 -> Surface.ROTATION_90
                Surface.ROTATION_180 -> Surface.ROTATION_180
                Surface.ROTATION_270 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }
        }

        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    imageCapture?.targetRotation = currentRotation()
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, mainHandler)

        val listener = Runnable {
            try {
                val provider = providerFuture.get()
                if (disposed) return@Runnable
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(95)
                    .setTargetRotation(currentRotation())
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                imageCapture = capture
            } catch (error: Exception) {
                onCameraError(error.localizedMessage ?: "카메라를 시작하지 못했습니다.")
            }
        }

        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            displayManager.unregisterDisplayListener(displayListener)
            imageCapture = null
            onCaptureReady(null)
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    LaunchedEffect(imageCapture) {
        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            onCaptureReady(null)
        } else {
            onCaptureReady { destination, callback ->
                val outputOptions = ImageCapture.OutputFileOptions.Builder(destination).build()
                captureUseCase.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            callback(Result.success(destination))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            callback(Result.failure(exception))
                        }
                    }
                )
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
