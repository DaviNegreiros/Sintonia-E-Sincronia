package com.sintonia.sincronia.processing

import android.content.Context
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.nio.ByteBuffer

class MediaPipePoseLandmarker(context: Context) : AutoCloseable {
    private val landmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(PoseLandmarkerConfig.MODEL_ASSET)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(PoseLandmarkerConfig.NUM_POSES)
            .setMinPoseDetectionConfidence(PoseLandmarkerConfig.MIN_POSE_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(PoseLandmarkerConfig.MIN_POSE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(PoseLandmarkerConfig.MIN_TRACKING_CONFIDENCE)
            .setOutputSegmentationMasks(false)
            .build()
        landmarker = PoseLandmarker.createFromOptions(context.applicationContext, options)
    }

    fun detect(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        performanceTracker: ProcessingPerformanceTracker? = null
    ): List<PoseLandmark> {
        rgbaBuffer.position(0)
        rgbaBuffer.limit(rgbaBuffer.capacity())
        val image = performanceTracker?.measureMpImageBuild {
            ByteBufferImageBuilder(rgbaBuffer, width, height, MPImage.IMAGE_FORMAT_RGBA).build()
        } ?: ByteBufferImageBuilder(rgbaBuffer, width, height, MPImage.IMAGE_FORMAT_RGBA).build()
        val result = performanceTracker?.measureMediaPipe {
            landmarker.detectForVideo(image, timestampMs)
        } ?: landmarker.detectForVideo(image, timestampMs)
        val firstPose = result.landmarks().firstOrNull() ?: return emptyList()
        return firstPose.map { landmark ->
            PoseLandmark(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f)
            ).rounded()
        }
    }

    override fun close() {
        landmarker.close()
    }

}

internal object PoseLandmarkerConfig {
    const val MODEL_ASSET = "pose_landmarker_full.task"
    const val NUM_POSES = 1
    const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
    const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
    const val MIN_TRACKING_CONFIDENCE = 0.5f
}
