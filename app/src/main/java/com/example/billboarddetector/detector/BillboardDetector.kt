package com.example.billboarddetector.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class BillboardDetector(
    context: Context,
    private val config: DetectorConfig = DetectorConfig()
) {
    private val labels = loadLabels(context, "labels.txt").ifEmpty { listOf("billboard") }
    private val interpreter = Interpreter(
        loadModelFile(context, "billboard_detector.tflite"),
        Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }
    )
    private val inputBuffer = ByteBuffer
        .allocateDirect(4 * config.inputWidth * config.inputHeight * 3)
        .order(ByteOrder.nativeOrder())

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> = withContext(Dispatchers.Default) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, config.inputWidth, config.inputHeight, true)
        convertBitmapToInputBuffer(scaledBitmap)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val output = Array(1) {
            Array(outputShape[1]) {
                FloatArray(outputShape[2])
            }
        }

        interpreter.run(inputBuffer, output)
        parseDetections(output[0])
    }

    fun close() {
        interpreter.close()
    }

    private fun convertBitmapToInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixel shr 8 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixel and 0xFF) / 255f))
        }
        inputBuffer.rewind()
    }

    private fun parseDetections(raw: Array<FloatArray>): List<DetectionResult> {
        val candidates = buildList {
            for (row in raw) {
                if (row.size < config.numDetectionElements) continue

                // Default YOLO-style row layout: centerX, centerY, width, height, score, classIndex.
                // Replace this block if billboard_detector.tflite exports a different tensor format.
                val score = row[4]
                if (score < config.scoreThreshold) continue

                val classIndex = row[5].toInt().coerceIn(0, labels.lastIndex)
                val cx = row[0]
                val cy = row[1]
                val width = row[2]
                val height = row[3]

                val left = (cx - width / 2f).coerceIn(0f, 1f)
                val top = (cy - height / 2f).coerceIn(0f, 1f)
                val right = (cx + width / 2f).coerceIn(0f, 1f)
                val bottom = (cy + height / 2f).coerceIn(0f, 1f)

                add(
                    DetectionResult(
                        boundingBox = RectF(left, top, right, bottom),
                        score = score,
                        label = labels[classIndex]
                    )
                )
            }
        }

        return nonMaxSuppression(candidates)
            .filter { it.label.equals("billboard", ignoreCase = true) }
            .take(config.maxResults)
    }

    private fun nonMaxSuppression(candidates: List<DetectionResult>): List<DetectionResult> {
        val sorted = candidates.sortedByDescending { it.score }.toMutableList()
        val selected = mutableListOf<DetectionResult>()

        while (sorted.isNotEmpty() && selected.size < config.maxResults) {
            val current = sorted.removeAt(0)
            selected += current
            sorted.removeAll { candidate -> iou(current.boundingBox, candidate.boundingBox) > config.iouThreshold }
        }

        return selected
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)

        val intersectionWidth = max(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight
        val unionArea = a.width() * a.height() + b.width() * b.height() - intersectionArea

        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        context.assets.openFd(fileName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).channel.use { fileChannel ->
                return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    private fun loadLabels(context: Context, fileName: String): List<String> = runCatching {
        context.assets.open(fileName).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }.getOrDefault(emptyList())
}
