package com.sintonia.sincronia.processing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class DancePoseProcessor(
    context: Context,
    private val normalizer: PoseNormalizer = PoseNormalizer(),
    private val angleCalculator: JointAngleCalculator = JointAngleCalculator(),
    private val skeletonDrawer: PoseSkeletonDrawer = PoseSkeletonDrawer(),
    private val movesetWriter: MovesetJsonWriter = MovesetJsonWriter()
) {
    private val appContext = context.applicationContext

    suspend fun process(
        videoFile: File,
        debugVideoFile: File,
        movesetFile: File,
        performanceTracker: ProcessingPerformanceTracker? = null,
        onProgress: (ImportProgress) -> Unit
    ): MovesetMetadata = withContext(Dispatchers.Default) {
        onProgress(ImportProgress("Detectando poses...", 0.1f))

        val frames = mutableListOf<MovesetFrame>()
        var detectedFrames = 0
        var finalInfo: DecodedVideoInfo? = null

        MediaPipePoseLandmarker(appContext).use { landmarker ->
            var encoder: DebugVideoEncoder? = null
            try {
                finalInfo = SequentialVideoFrameDecoder(videoFile, performanceTracker).decodeFrames { videoInfo, decodedFrame ->
                    coroutineContext.ensureActive()
                    val debugEncoder = encoder ?: DebugVideoEncoder(
                        outputFile = debugVideoFile,
                        sourceVideoFile = videoFile,
                        width = videoInfo.width,
                        height = videoInfo.height,
                        fps = videoInfo.fps
                    ).also { encoder = it }

                    processDecodedFrame(
                        decodedFrame = decodedFrame,
                        videoInfo = videoInfo,
                        landmarker = landmarker,
                        encoder = debugEncoder,
                        performanceTracker = performanceTracker,
                        frames = frames,
                        onDetectedPose = { detectedFrames += 1 },
                        onProgress = onProgress
                    )
                }
                val finishStartedAt = ProcessingPerformanceTracker.now()
                encoder?.finish()
                val finishMs = ProcessingPerformanceTracker.elapsedSince(finishStartedAt)
                performanceTracker?.addDebugEncoderTime(finishMs)
                performanceTracker?.addDebugVideoTime(finishMs)
            } finally {
                encoder?.close()
            }
        }

        if (detectedFrames == 0) {
            movesetFile.delete()
            debugVideoFile.delete()
            throw NoPoseDetectedException()
        }

        val videoInfo = requireNotNull(finalInfo) { "Nenhum frame foi decodificado do vídeo." }
        val metadata = MovesetMetadata(
            video = videoFile.name,
            fps = videoInfo.fps,
            frameCount = frames.size,
            width = videoInfo.width,
            height = videoInfo.height,
            duration = videoInfo.durationSeconds.roundToSixDecimals()
        )
        if (performanceTracker == null) {
            movesetWriter.write(movesetFile, metadata, frames)
        } else {
            performanceTracker.measureMovesetJsonWrite {
                movesetWriter.write(movesetFile, metadata, frames)
            }
        }
        onProgress(ImportProgress("Finalizando dança...", 1f))
        metadata
    }

    private suspend fun processDecodedFrame(
        decodedFrame: DecodedVideoFrame,
        videoInfo: DecodedVideoInfo,
        landmarker: MediaPipePoseLandmarker,
        encoder: DebugVideoEncoder,
        performanceTracker: ProcessingPerformanceTracker?,
        frames: MutableList<MovesetFrame>,
        onDetectedPose: () -> Unit,
        onProgress: (ImportProgress) -> Unit
    ) {
        val frameIndex = decodedFrame.index
        val timestampMs = decodedFrame.timestampMs
        val timestampUs = timestampMs * 1000L

        coroutineContext.ensureActive()
        val landmarks = landmarker.detect(
            rgbaBuffer = decodedFrame.rgbaBuffer,
            width = decodedFrame.width,
            height = decodedFrame.height,
            timestampMs = timestampMs,
            performanceTracker = performanceTracker
        )
        val normalized = if (landmarks.isNotEmpty()) {
            performanceTracker?.measureNormalization {
                normalizer.normalize(landmarks).normalizedLandmarks
            } ?: normalizer.normalize(landmarks).normalizedLandmarks
        } else {
            emptyList()
        }
        val jointAngles = if (normalized.isNotEmpty()) {
            performanceTracker?.measureAngleCalculation {
                angleCalculator.calculate(normalized)
            } ?: angleCalculator.calculate(normalized)
        } else {
            emptyMap()
        }
        if (landmarks.isNotEmpty()) onDetectedPose()

        if (performanceTracker == null) {
            frames += MovesetFrame(
                frame = frameIndex,
                timestamp = (timestampMs / 1000.0).roundToSixDecimals(),
                poseDetected = landmarks.isNotEmpty(),
                landmarks = landmarks,
                normalizedLandmarks = normalized,
                jointAngles = jointAngles
            )
        } else {
            performanceTracker.measureMovesetFrameBuild {
                frames += MovesetFrame(
                    frame = frameIndex,
                    timestamp = (timestampMs / 1000.0).roundToSixDecimals(),
                    poseDetected = landmarks.isNotEmpty(),
                    landmarks = landmarks,
                    normalizedLandmarks = normalized,
                    jointAngles = jointAngles
                )
            }
        }

        if (performanceTracker == null) {
            skeletonDrawer.draw(decodedFrame, landmarks)
            encoder.encode(decodedFrame.rgbaBuffer, decodedFrame.rgbaStride, timestampUs)
        } else {
            performanceTracker.measureDebugVideo {
                performanceTracker.measureSkeletonDraw {
                    skeletonDrawer.draw(decodedFrame, landmarks)
                }
                encoder.encode(decodedFrame.rgbaBuffer, decodedFrame.rgbaStride, timestampUs, performanceTracker)
            }
        }

        onProgress(
            ImportProgress(
                message = "Detectando poses...",
                progress = 0.1f + 0.85f * ((frameIndex + 1).toFloat() / videoInfo.frameCount.toFloat())
            )
        )
    }
}
