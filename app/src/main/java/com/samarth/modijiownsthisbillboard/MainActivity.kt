package com.samarth.modijiownsthisbillboard

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.samarth.modijiownsthisbillboard.ui.theme.ModijiOwnsThisBillboardTheme
import java.util.concurrent.Executors

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
    val labels: List<String>,
    val imageSize: Size,
    val rotation: Int
)

@Composable
fun ObjectDetectionView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var detectedObjects by remember { mutableStateOf(emptyList<DetectedObject>()) }

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
                                detectedObjects = objects.map { 
                                    DetectedObject(
                                        it.boundingBox,
                                        it.labels.map { label -> label.text },
                                        currentImageSize,
                                        rotation
                                    )
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("ObjectDetectionView", "Detection failed", e)
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
                    Log.e("ObjectDetectionView", "Use case binding failed", e)
                }

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedObjects.forEach { obj ->
                val rect = obj.boundingBox
                
                // When rotation is 90 or 270, width and height are swapped
                val isRotated = obj.rotation == 90 || obj.rotation == 270
                val imageWidth = if (isRotated) obj.imageSize.height else obj.imageSize.width
                val imageHeight = if (isRotated) obj.imageSize.width else obj.imageSize.height
                
                val scaleX = size.width / imageWidth
                val scaleY = size.height / imageHeight

                // Map coordinates from image space to view space
                // This is a simplified mapping that might need adjustment based on PreviewView's scale type
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        rect.left * scaleX,
                        rect.top * scaleY
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (rect.right - rect.left) * scaleX,
                        (rect.bottom - rect.top) * scaleY
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
