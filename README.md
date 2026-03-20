# Billboard Detector Android App

This project is a complete Android Studio app in Kotlin that uses CameraX for the rear camera preview, TensorFlow Lite for on-device billboard detection, and a Compose overlay to draw real-time bounding boxes and confidence labels.

## Project structure

- `app/src/main/java/com/example/billboarddetector/MainActivity.kt` – runtime permission entry point.
- `app/src/main/java/com/example/billboarddetector/ui/CameraScreen.kt` – CameraX preview + frame analysis loop.
- `app/src/main/java/com/example/billboarddetector/detector/BillboardDetector.kt` – TensorFlow Lite wrapper, preprocessing, parsing, and NMS.
- `app/src/main/java/com/example/billboarddetector/ui/DetectionOverlay.kt` – Compose bounding box overlay.
- `app/src/main/java/com/example/billboarddetector/util/ImageUtils.kt` – `ImageProxy` to `Bitmap` conversion.
- `app/src/main/java/com/example/billboarddetector/ui/OverlayMapping.kt` – preview-to-screen coordinate mapping.

## Setup

1. Open the project in Android Studio Hedgehog or newer.
2. Place your TensorFlow Lite model at:
   - `app/src/main/assets/billboard_detector.tflite`
3. Place labels at:
   - `app/src/main/assets/labels.txt`
4. Ensure `labels.txt` contains a `billboard` class entry.
5. Sync Gradle and run the `app` configuration on a physical Android device.

> The repository includes the `assets/` directory, but not the model binary itself.

## How to run

1. Connect an Android device with a rear camera.
2. Press **Run** in Android Studio.
3. Grant the camera permission when prompted.
4. Point the camera at a billboard-like object.
5. The app continuously analyzes frames and draws green boxes with confidence scores over detections.

## Using a different model

The default parser assumes a YOLO-style output row:

`[centerX, centerY, width, height, score, classIndex]`

If your `.tflite` model outputs a different tensor shape or order, update only the `parseDetections()` function in:

- `app/src/main/java/com/example/billboarddetector/detector/BillboardDetector.kt`

You can also tune these parameters in `DetectorConfig.kt`:

- `inputWidth`
- `inputHeight`
- `scoreThreshold`
- `iouThreshold`
- `maxResults`
- `numDetectionElements`

## Bounding box coordinate mapping

The model returns normalized coordinates in the analyzed image space. The app converts those values into screen coordinates by:

1. Comparing the analyzed frame aspect ratio with the `PreviewView` container aspect ratio.
2. Applying the same center-crop scale logic used by the camera preview.
3. Computing horizontal or vertical offsets introduced by cropping.
4. Scaling normalized box coordinates into the visible overlay space.

That logic is isolated in `OverlayMapping.kt`, so it is easy to adjust if your preview scaling strategy changes.

## Performance notes

- CameraX analysis uses `STRATEGY_KEEP_ONLY_LATEST`.
- The analyzer throttles to roughly one inference every 120 ms.
- Inference runs on a background coroutine using a single TensorFlow Lite interpreter.
- Stale boxes are cleared automatically because each frame replaces the detection list.
