package com.samarth.modijiownsthisbillboard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.samarth.modijiownsthisbillboard.ui.theme.ModijiOwnsThisBillboardTheme
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ObjectDetectionView"
private const val MIN_CLASSIFICATION_CONFIDENCE = 0.55f
private const val MAX_LABELS_ON_SCREEN = 3

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ModijiOwnsThisBillboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraScreen()
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        ObjectDetectionView()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Camera permission required")
        }
    }
}

data class DetectedObject(
    val boundingBox: Rect,
    val displayLines: List<String>,
    val imageSize: Size,
    val rotation: Int
)

private data class FormattedLabel(
    val displayLines: List<String>,
    val bestConfidence: Float?
)

private fun formatLabelText(text: String): String {
    return text.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }
}

private fun formatDetectedLabel(labels: List<com.google.mlkit.vision.objects.DetectedObject.Label>): FormattedLabel {
    val sortedLabels = labels.sortedByDescending { it.confidence }
    val displayLines = if (sortedLabels.isEmpty()) {
        listOf("No labels")
    } else {
        sortedLabels
            .take(MAX_LABELS_ON_SCREEN)
            .map { label ->
                val labelName = label.text.takeIf { it.isNotBlank() }?.let(::formatLabelText) ?: "Unknown"
                "$labelName (${String.format(Locale.US, "%.2f", label.confidence)})"
            }
    }

    return FormattedLabel(
        displayLines = displayLines,
        bestConfidence = sortedLabels.firstOrNull()?.confidence
    )
}

@Composable
fun ObjectDetectionView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var detectedObjects by remember { mutableStateOf(emptyList<DetectedObject>()) }
    val density = LocalDensity.current

    val labelTextPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 18.dp.toPx() }
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }
    val labelBackgroundPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(220, 0, 180, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
    val labelPadding = with(density) { 6.dp.toPx() }
    val labelCornerRadius = with(density) { 6.dp.toPx() }
    val lineHeight = labelTextPaint.fontSpacing

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val preview = Preview.Builder().build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                preview.surfaceProvider = previewView.surfaceProvider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val options = ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
                val objectDetector = ObjectDetection.getClient(options)
                val analysisExecutor = Executors.newSingleThreadExecutor()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val image = InputImage.fromMediaImage(mediaImage, rotation)
                        val currentImageSize = Size(image.width, image.height)

                        objectDetector.process(image)
                            .addOnSuccessListener { objects ->
                                detectedObjects = objects.mapNotNull { detectedObject ->
                                    val formattedLabel = formatDetectedLabel(detectedObject.labels)
                                    val bestConfidence = formattedLabel.bestConfidence

                                    Log.d(
                                        TAG,
                                        buildString {
                                            append("Detected box=")
                                            append(detectedObject.boundingBox)
                                            append(", labels=")
                                            append(
                                                if (detectedObject.labels.isEmpty()) {
                                                    "[]"
                                                } else {
                                                    detectedObject.labels.joinToString(prefix = "[", postfix = "]") { label ->
                                                        val labelName = label.text.takeIf { it.isNotBlank() } ?: "<blank>"
                                                        "$labelName:${String.format(Locale.US, "%.2f", label.confidence)}"
                                                    }
                                                }
                                            )
                                        }
                                    )

                                    if (bestConfidence == null || bestConfidence < MIN_CLASSIFICATION_CONFIDENCE) {
                                        null
                                    } else {
                                        DetectedObject(
                                            boundingBox = detectedObject.boundingBox,
                                            displayLines = formattedLabel.displayLines,
                                            imageSize = currentImageSize,
                                            rotation = rotation
                                        )
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Detection failed", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProviderFuture.get().bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedObjects.forEach { obj ->
                val rect = obj.boundingBox

                val isRotated = obj.rotation == 90 || obj.rotation == 270
                val imageWidth = if (isRotated) obj.imageSize.height else obj.imageSize.width
                val imageHeight = if (isRotated) obj.imageSize.width else obj.imageSize.height

                val scaleX = size.width / imageWidth
                val scaleY = size.height / imageHeight

                val left = rect.left * scaleX
                val top = rect.top * scaleY
                val boxWidth = (rect.right - rect.left) * scaleX
                val boxHeight = (rect.bottom - rect.top) * scaleY
                val squareSide = max(boxWidth, boxHeight)
                val horizontalInset = (squareSide - boxWidth) / 2f
                val verticalInset = (squareSide - boxHeight) / 2f
                val boxLeft = left - horizontalInset
                val boxTop = top - verticalInset

                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(
                        x = boxLeft,
                        y = boxTop
                    ),
                    size = ComposeSize(squareSide, squareSide),
                    style = Stroke(width = 3.dp.toPx())
                )

                val longestLineWidth = obj.displayLines.maxOfOrNull { line -> labelTextPaint.measureText(line) } ?: 0f
                val backgroundHeight = (lineHeight * obj.displayLines.size) + (labelPadding * 2)
                val preferredTop = boxTop - backgroundHeight - labelPadding
                val backgroundTop = max(0f, preferredTop)
                val textStartY = backgroundTop + labelPadding - labelTextPaint.fontMetrics.top
                val backgroundRight = min(size.width, boxLeft + longestLineWidth + (labelPadding * 2))

                drawContext.canvas.nativeCanvas.apply {
                    drawRoundRect(
                        boxLeft,
                        backgroundTop,
                        backgroundRight,
                        backgroundTop + backgroundHeight,
                        labelCornerRadius,
                        labelCornerRadius,
                        labelBackgroundPaint
                    )
                    obj.displayLines.forEachIndexed { index, line ->
                        drawText(
                            line,
                            boxLeft + labelPadding,
                            textStartY + (index * lineHeight),
                            labelTextPaint
                        )
                    }
                }
            }
        }
    }
}
