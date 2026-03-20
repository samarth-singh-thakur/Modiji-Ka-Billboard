package com.example.billboarddetector.detector

data class DetectorConfig(
    val inputWidth: Int = 320,
    val inputHeight: Int = 320,
    val scoreThreshold: Float = 0.45f,
    val iouThreshold: Float = 0.5f,
    val maxResults: Int = 10,
    val numDetectionElements: Int = 6
)
