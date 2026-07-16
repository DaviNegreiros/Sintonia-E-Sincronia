package com.sintonia.sincronia.processing

import android.content.Context
import android.graphics.ImageFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class RealtimePoseResult(
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val landmarks: List<PoseLandmark>
)

class RealtimePoseLandmarker(
    context: Context,
    private val onResult: (RealtimePoseResult) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isProcessing = AtomicBoolean(false)
    private val lastTimestampMs = AtomicLong(-1L)
    private val inFlightFrame = AtomicReference<FrameMetadata?>(null)
    private val frameBuffers = RgbaFrameBuffers()
    private val landmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(PoseLandmarkerConfig.MODEL_ASSET)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(PoseLandmarkerConfig.NUM_POSES)
            .setMinPoseDetectionConfidence(PoseLandmarkerConfig.MIN_POSE_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(PoseLandmarkerConfig.MIN_POSE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(PoseLandmarkerConfig.MIN_TRACKING_CONFIDENCE)
            .setOutputSegmentationMasks(false)
            .setResultListener { result, inputImage ->
                runCatching { inputImage.close() }
                handleResult(result)
            }
            .setErrorListener { error ->
                handleError(error)
            }
            .build()
        landmarker = PoseLandmarker.createFromOptions(context.applicationContext, options)
    }

    fun detect(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        if (imageProxy.format != ImageFormat.YUV_420_888) {
            isProcessing.set(false)
            imageProxy.close()
            postError(IllegalArgumentException("Formato inesperado da câmera: ${imageProxy.format}."))
            return
        }

        val timestampMs = monotonicTimestampMs(imageProxy.imageInfo.timestamp)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val frameMetadata = runCatching {
            imageProxy.toRgbaFrame(rotationDegrees)
        }.getOrElse { error ->
            imageProxy.close()
            isProcessing.set(false)
            postError(error)
            return
        }
        imageProxy.close()
        val mpImage = ByteBufferImageBuilder(
            frameBuffers.rgbaBuffer,
            frameMetadata.imageWidth,
            frameMetadata.imageHeight,
            MPImage.IMAGE_FORMAT_RGBA
        ).build()

        inFlightFrame.set(frameMetadata)
        runCatching {
            landmarker.detectAsync(mpImage, timestampMs)
        }.onFailure { error ->
            runCatching { mpImage.close() }
            inFlightFrame.getAndSet(null)
            isProcessing.set(false)
            postError(error)
        }
    }

    override fun close() {
        inFlightFrame.getAndSet(null)
        landmarker.close()
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks().firstOrNull()
            ?.map { landmark ->
                PoseLandmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(0f)
                ).rounded()
            }
            ?: emptyList()

        val frame = inFlightFrame.getAndSet(null)
        isProcessing.set(false)
        mainHandler.post {
            onResult(
                RealtimePoseResult(
                    timestampMs = result.timestampMs(),
                    imageWidth = frame?.imageWidth ?: 0,
                    imageHeight = frame?.imageHeight ?: 0,
                    landmarks = landmarks
                )
            )
        }
    }

    private fun handleError(error: RuntimeException) {
        inFlightFrame.getAndSet(null)
        isProcessing.set(false)
        postError(error)
    }

    private fun postError(error: Throwable) {
        Log.w(TAG, "Falha no tracking de pose em tempo real.", error)
        mainHandler.post { onError(error) }
    }

    private fun monotonicTimestampMs(timestampNs: Long): Long {
        val candidate = (timestampNs / 1_000_000L).coerceAtLeast(0L)
        while (true) {
            val previous = lastTimestampMs.get()
            val next = candidate.coerceAtLeast(previous + 1L)
            if (lastTimestampMs.compareAndSet(previous, next)) return next
        }
    }

    private fun ImageProxy.toRgbaFrame(rotationDegrees: Int): FrameMetadata {
        val crop = cropRect
        val cropWidth = crop.width().roundToEven().coerceAtLeast(2)
        val cropHeight = crop.height().roundToEven().coerceAtLeast(2)
        val rotated = rotationDegrees == 90 || rotationDegrees == 270
        val outputWidth = if (rotated) cropHeight else cropWidth
        val outputHeight = if (rotated) cropWidth else cropHeight
        val tempPixelCount = maxOf(cropWidth * cropHeight, outputWidth * outputHeight)
        frameBuffers.ensure(outputWidth, outputHeight, tempPixelCount)

        val imagePlanes = planes
        val yPlane = imagePlanes[0]
        val uPlane = imagePlanes[1]
        val vPlane = imagePlanes[2]
        val rgbaStride = outputWidth * RgbaFrameBuffers.RGBA_BYTES_PER_PIXEL
        val result = NativeLibyuvBridge.convertAndroid420ToRgba(
            yBuffer = yPlane.buffer,
            uBuffer = uPlane.buffer,
            vBuffer = vPlane.buffer,
            yRowStride = yPlane.rowStride,
            uRowStride = uPlane.rowStride,
            vRowStride = vPlane.rowStride,
            uvPixelStride = uPlane.pixelStride,
            cropLeft = crop.left,
            cropTop = crop.top,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            targetWidth = cropWidth,
            targetHeight = cropHeight,
            rotationDegrees = rotationDegrees,
            tempI420A = frameBuffers.tempI420A,
            tempI420B = frameBuffers.tempI420B,
            rgbaBuffer = frameBuffers.rgbaBuffer,
            rgbaStride = rgbaStride
        )
        check(result == 0) { "Falha na conversão libyuv real-time: código $result." }
        frameBuffers.rgbaBuffer.position(0)
        frameBuffers.rgbaBuffer.limit(outputWidth * outputHeight * RgbaFrameBuffers.RGBA_BYTES_PER_PIXEL)

        return FrameMetadata(
            imageWidth = outputWidth,
            imageHeight = outputHeight
        )
    }

    private fun Int.roundToEven(): Int =
        if (this % 2 == 0) this else this - 1

    private data class FrameMetadata(
        val imageWidth: Int,
        val imageHeight: Int
    )

    private companion object {
        const val TAG = "RealtimePoseLandmarker"
    }
}
