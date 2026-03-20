package com.example.billboarddetector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billboarddetector.detector.DetectionResult

@Composable
fun DetectionOverlay(
    detections: List<DetectionResult>,
    modifier: Modifier = Modifier,
    containerSize: IntSize,
    previewSize: IntSize
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 3.dp.toPx()
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 14.sp.toPx()
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 0, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }

        detections.forEach { detection ->
            val rect = mapDetectionToOverlay(detection, containerSize, previewSize)
            if (rect.width() <= 0f || rect.height() <= 0f) return@forEach

            drawRect(
                color = Color(0xFF00E676),
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width(), rect.height()),
                style = Stroke(width = strokeWidth)
            )

            val label = "${detection.label} ${(detection.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize
            val bgLeft = rect.left
            val bgTop = (rect.top - textHeight - 20f).coerceAtLeast(0f)
            val bgRight = bgLeft + textWidth + 24f
            val bgBottom = bgTop + textHeight + 16f

            drawContext.canvas.nativeCanvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 12f, 12f, bgPaint)
            drawContext.canvas.nativeCanvas.drawText(label, bgLeft + 12f, bgBottom - 10f, textPaint)
        }
    }
}
