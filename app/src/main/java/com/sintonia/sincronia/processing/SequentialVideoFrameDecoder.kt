package com.sintonia.sincronia.processing

import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class DecodedVideoInfo(
    val width: Int,
    val height: Int,
    val rawWidth: Int,
    val rawHeight: Int,
    val rotationDegrees: Int,
    val fps: Double,
    val frameCount: Int,
    val durationSeconds: Double
)

class SequentialVideoFrameDecoder(
    private val videoFile: File,
    private val performanceTracker: ProcessingPerformanceTracker? = null
) {
    private val frameBuffers = RgbaFrameBuffers()

    suspend fun decodeFrames(onFrame: suspend (DecodedVideoInfo, DecodedVideoFrame) -> Unit): DecodedVideoInfo {
        val openStartedNs = System.nanoTime()
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(videoFile.absolutePath)
            val trackIndex = selectVideoTrack(extractor)
            require(trackIndex >= 0) { "Nenhuma faixa de vídeo encontrada em ${videoFile.name}." }
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            val info = format.toDecodedVideoInfo()
            performanceTracker?.recordVideoInfo(info)
            VideoDecodePerformanceLogger.videoOpened(
                openMs = elapsedMs(openStartedNs),
                frameCount = info.frameCount,
                fps = info.fps,
                width = info.width,
                height = info.height
            )

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val readStartedNs = System.nanoTime()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var frameIndex = 0
            var emittedFrameCount = 0
            var nextTargetTimeUs = 0L
            var lastTimestampMs = -1L

            while (!outputDone) {
                coroutineContext.ensureActive()

                val decoderCycleStartedAt = ProcessingPerformanceTracker.now()
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(decoder.getInputBuffer(inputIndex))
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                performanceTracker?.addDecoderWaitReadTime(
                    ProcessingPerformanceTracker.elapsedSince(decoderCycleStartedAt)
                )
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outputIndex >= 0 -> {
                        val hasFrame = bufferInfo.size > 0
                        if (hasFrame) {
                            val presentationTimeUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            performanceTracker?.recordDecodedFrame()
                            val shouldEmitFrame = presentationTimeUs >= nextTargetTimeUs
                            if (shouldEmitFrame) {
                                while (nextTargetTimeUs <= presentationTimeUs) {
                                    nextTargetTimeUs += TARGET_PROCESSING_INTERVAL_US
                                }

                                val decodeStartedNs = System.nanoTime()
                                val getOutputImageStartedAt = ProcessingPerformanceTracker.now()
                                val image = decoder.getOutputImage(outputIndex)
                                performanceTracker?.addDecoderGetOutputImageTime(
                                    ProcessingPerformanceTracker.elapsedSince(getOutputImageStartedAt)
                                )
                                if (image != null) {
                                    image.use { frameImage ->
                                        val timestampMs = presentationTimeUs
                                            .toMonotonicMillisecondsAfter(lastTimestampMs)
                                        lastTimestampMs = timestampMs
                                        frameImage.toDisplayFrame(
                                            info = info,
                                            frameIndex = frameIndex,
                                            presentationTimeUs = presentationTimeUs,
                                            timestampMs = timestampMs,
                                            performanceTracker = performanceTracker
                                        )
                                        performanceTracker?.recordProcessedFrame()
                                        onFrame(info, frameBuffers.frame)
                                    }
                                    VideoDecodePerformanceLogger.frameDecoded(
                                        emittedFrameCount,
                                        elapsedMs(decodeStartedNs)
                                    )
                                    emittedFrameCount += 1
                                }
                            } else {
                                performanceTracker?.recordSampledOutFrame()
                            }
                            frameIndex += 1
                        }

                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            VideoDecodePerformanceLogger.finished(emittedFrameCount, elapsedMs(readStartedNs))
            return info.copy(frameCount = emittedFrameCount)
        } finally {
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return index
        }
        return -1
    }

    private fun MediaFormat.toDecodedVideoInfo(): DecodedVideoInfo {
        val rawWidth = getInteger(MediaFormat.KEY_WIDTH)
        val rawHeight = getInteger(MediaFormat.KEY_HEIGHT)
        val rotation = if (containsKey(MediaFormat.KEY_ROTATION)) getInteger(MediaFormat.KEY_ROTATION) else 0
        val normalizedRotation = rotation.floorMod(360)
        val durationUs = if (containsKey(MediaFormat.KEY_DURATION)) getLong(MediaFormat.KEY_DURATION) else 0L
        val fps = if (containsKey(MediaFormat.KEY_FRAME_RATE)) {
            getInteger(MediaFormat.KEY_FRAME_RATE).toDouble().takeIf { it > 0.0 } ?: DEFAULT_FPS
        } else {
            DEFAULT_FPS
        }
        val displayWidth = if (normalizedRotation == 90 || normalizedRotation == 270) rawHeight else rawWidth
        val displayHeight = if (normalizedRotation == 90 || normalizedRotation == 270) rawWidth else rawHeight
        val durationSeconds = durationUs / 1_000_000.0
        return DecodedVideoInfo(
            width = displayWidth.roundToEven(),
            height = displayHeight.roundToEven(),
            rawWidth = rawWidth.roundToEven(),
            rawHeight = rawHeight.roundToEven(),
            rotationDegrees = normalizedRotation,
            fps = fps,
            frameCount = (durationSeconds * fps).roundToInt().coerceAtLeast(1),
            durationSeconds = durationSeconds
        )
    }

    private fun Image.toDisplayFrame(
        info: DecodedVideoInfo,
        frameIndex: Int,
        presentationTimeUs: Long,
        timestampMs: Long,
        performanceTracker: ProcessingPerformanceTracker?
    ) {
        require(format == ImageFormat.YUV_420_888) {
            "Formato de imagem não suportado pelo pipeline nativo: $format."
        }
        require(planes.size >= 3) {
            "YUV_420_888 deve possuir ao menos 3 planos; recebido: ${planes.size}."
        }
        val crop = cropRect
        val cropLeft = crop.left
        val cropTop = crop.top
        val cropWidth = crop.width().roundToEven()
        val cropHeight = crop.height().roundToEven()
        val tempPixelCount = maxOf(
            cropWidth * cropHeight,
            info.rawWidth * info.rawHeight,
            info.width * info.height
        )
        frameBuffers.ensure(info.width, info.height, tempPixelCount)
        val imagePlanes = planes
        val yPlane = imagePlanes[0]
        val uPlane = imagePlanes[1]
        val vPlane = imagePlanes[2]
        require(yPlane.pixelStride == 1) {
            "Pipeline nativo requer pixelStride 1 no plano Y; recebido: ${yPlane.pixelStride}."
        }
        require(uPlane.pixelStride == vPlane.pixelStride) {
            "Pipeline nativo requer pixelStride U/V igual; recebido U=${uPlane.pixelStride}, V=${vPlane.pixelStride}."
        }
        val rgbaStride = info.width * RgbaFrameBuffers.RGBA_BYTES_PER_PIXEL
        val result = performanceTracker?.measureLibyuv {
            NativeLibyuvBridge.convertAndroid420ToRgba(
                yBuffer = yPlane.buffer,
                uBuffer = uPlane.buffer,
                vBuffer = vPlane.buffer,
                yRowStride = yPlane.rowStride,
                uRowStride = uPlane.rowStride,
                vRowStride = vPlane.rowStride,
                uvPixelStride = uPlane.pixelStride,
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropWidth = cropWidth,
                cropHeight = cropHeight,
                targetWidth = info.rawWidth,
                targetHeight = info.rawHeight,
                rotationDegrees = info.rotationDegrees,
                tempI420A = frameBuffers.tempI420A,
                tempI420B = frameBuffers.tempI420B,
                rgbaBuffer = frameBuffers.rgbaBuffer,
                rgbaStride = rgbaStride
            )
        } ?: NativeLibyuvBridge.convertAndroid420ToRgba(
            yBuffer = yPlane.buffer,
            uBuffer = uPlane.buffer,
            vBuffer = vPlane.buffer,
            yRowStride = yPlane.rowStride,
            uRowStride = uPlane.rowStride,
            vRowStride = vPlane.rowStride,
            uvPixelStride = uPlane.pixelStride,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            targetWidth = info.rawWidth,
            targetHeight = info.rawHeight,
            rotationDegrees = info.rotationDegrees,
            tempI420A = frameBuffers.tempI420A,
            tempI420B = frameBuffers.tempI420B,
            rgbaBuffer = frameBuffers.rgbaBuffer,
            rgbaStride = rgbaStride
        )
        check(result == 0) { "Falha na conversão libyuv: código $result." }
        frameBuffers.rgbaBuffer.position(0)
        frameBuffers.rgbaBuffer.limit(frameBuffers.rgbaBuffer.capacity())
        frameBuffers.frame.update(
            index = frameIndex,
            presentationTimeUs = presentationTimeUs,
            timestampMs = timestampMs,
            width = info.width,
            height = info.height,
            rgbaStride = rgbaStride,
            rgbaBuffer = frameBuffers.rgbaBuffer
        )
    }

    private fun Long.toMonotonicMillisecondsAfter(previousTimestampMs: Long): Long {
        val timestampMs = (this / 1000L).coerceAtLeast(0L)
        return timestampMs.coerceAtLeast(previousTimestampMs + 1L)
    }

    private fun Int.roundToEven(): Int =
        if (this % 2 == 0) this else this - 1

    private fun Int.floorMod(modulus: Int): Int =
        ((this % modulus) + modulus) % modulus

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000L

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val DEFAULT_FPS = 30.0
        const val TARGET_PROCESSING_FPS = 15.0
        val TARGET_PROCESSING_INTERVAL_US = (1_000_000.0 / TARGET_PROCESSING_FPS)
            .roundToLong()
            .coerceAtLeast(1L)
    }
}
