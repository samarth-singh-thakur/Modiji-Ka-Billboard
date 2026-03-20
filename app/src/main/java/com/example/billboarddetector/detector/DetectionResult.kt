package com.example.billboarddetector.detector

import android.graphics.RectF

data class DetectionResult(
    val boundingBox: RectF,
    val score: Float,
    val label: String
)
