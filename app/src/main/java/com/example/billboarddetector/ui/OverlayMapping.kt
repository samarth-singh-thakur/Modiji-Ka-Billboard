package com.example.billboarddetector.ui

import android.graphics.RectF
import androidx.compose.ui.unit.IntSize
import com.example.billboarddetector.detector.DetectionResult

fun mapDetectionToOverlay(
    detection: DetectionResult,
    containerSize: IntSize,
    previewSize: IntSize
): RectF {
    if (containerSize.width == 0 || containerSize.height == 0 || previewSize.width == 0 || previewSize.height == 0) {
        return RectF()
    }

    val previewAspect = previewSize.width.toFloat() / previewSize.height.toFloat()
    val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()

    val scaledWidth: Float
    val scaledHeight: Float
    val xOffset: Float
    val yOffset: Float

    if (previewAspect > containerAspect) {
        val scale = containerSize.height.toFloat() / previewSize.height.toFloat()
        scaledWidth = previewSize.width * scale
        scaledHeight = containerSize.height.toFloat()
        xOffset = (containerSize.width - scaledWidth) / 2f
        yOffset = 0f
    } else {
        val scale = containerSize.width.toFloat() / previewSize.width.toFloat()
        scaledWidth = containerSize.width.toFloat()
        scaledHeight = previewSize.height * scale
        xOffset = 0f
        yOffset = (containerSize.height - scaledHeight) / 2f
    }

    val box = detection.boundingBox
    return RectF(
        xOffset + box.left * scaledWidth,
        yOffset + box.top * scaledHeight,
        xOffset + box.right * scaledWidth,
        yOffset + box.bottom * scaledHeight
    )
}
