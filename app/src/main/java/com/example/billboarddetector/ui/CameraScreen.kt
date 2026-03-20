package com.example.billboarddetector.ui

import android.annotation.SuppressLint
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.example.billboarddetector.R
import com.example.billboarddetector.camera.CameraPreviewView
import com.example.billboarddetector.detector.BillboardDetector
import com.example.billboarddetector.detector.DetectionResult
import com.example.billboarddetector.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val detector = remember { BillboardDetector(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val uiScope = rememberCoroutineScope()
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var previewBufferSize by remember { mutableStateOf(IntSize(1280, 720)) }
    var lastAnalyzedTimestamp by remember { mutableLongStateOf(0L) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            detector.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(previewView) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val analysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedTimestamp < 120 || activeJob?.isActive == true) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastAnalyzedTimestamp = now
            previewBufferSize = IntSize(imageProxy.width, imageProxy.height)

            val bitmap = runCatching { ImageUtils.imageProxyToBitmap(imageProxy) }
                .onFailure {
                    uiScope.launch { detections = emptyList() }
                    imageProxy.close()
                }
                .getOrNull() ?: return@setAnalyzer

            imageProxy.close()
            activeJob = analysisScope.launch {
                val results = runCatching { detector.detect(bitmap) }
                    .getOrElse { emptyList() }
                uiScope.launch { detections = results }
            }
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            context as LifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { containerSize = it }
    ) {
        CameraPreviewView(
            previewView = previewView,
            modifier = Modifier.fillMaxSize()
        )
        DetectionOverlay(
            detections = detections,
            containerSize = containerSize,
            previewSize = previewBufferSize,
            modifier = Modifier.fillMaxSize()
        )
        if (containerSize == IntSize.Zero) {
            Text(
                text = context.getString(R.string.camera_loading),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
