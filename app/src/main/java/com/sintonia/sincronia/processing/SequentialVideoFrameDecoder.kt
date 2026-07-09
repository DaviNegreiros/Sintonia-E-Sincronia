package com.sintonia.sincronia.processing

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
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

data class DecodedVideoFrame(
    val index: Int,
    val presentationTimeUs: Long,
    val bitmap: Bitmap
)

class SequentialVideoFrameDecoder(
    private val videoFile: File,
    private val performanceTracker: ProcessingPerformanceTracker? = null
) {
    private var reusableArgb = IntArray(0)

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
                                        val bitmap = frameImage.toDisplayBitmap(info, performanceTracker)
                                        performanceTracker?.recordProcessedFrame()
                                        onFrame(info, DecodedVideoFrame(frameIndex, presentationTimeUs, bitmap))
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

    private fun Image.toDisplayBitmap(
        info: DecodedVideoInfo,
        performanceTracker: ProcessingPerformanceTracker?
    ): Bitmap {
        val rawBitmap = performanceTracker?.measureImageToBitmap {
            toBitmap(info.rawWidth, info.rawHeight)
        } ?: toBitmap(info.rawWidth, info.rawHeight)
        val rotatedBitmap = performanceTracker?.measureBitmapTransform {
            rawBitmap.rotate(info.rotationDegrees)
        } ?: rawBitmap.rotate(info.rotationDegrees)
        return if (rotatedBitmap.width == info.width && rotatedBitmap.height == info.height) {
            rotatedBitmap
        } else {
            performanceTracker?.measureBitmapTransform {
                Bitmap.createScaledBitmap(rotatedBitmap, info.width, info.height, true).also {
                    rotatedBitmap.recycle()
                }
            } ?: Bitmap.createScaledBitmap(rotatedBitmap, info.width, info.height, true).also {
                rotatedBitmap.recycle()
            }
        }
    }

    private fun Image.toBitmap(targetWidth: Int, targetHeight: Int): Bitmap {
        val crop = cropRect
        val cropLeft = crop.left
        val cropTop = crop.top
        val width = crop.width()
        val height = crop.height()
        val pixelCount = width * height
        val argb = obtainArgbBuffer(pixelCount)
        val imagePlanes = planes
        val yPlane = imagePlanes[0]
        val uPlane = imagePlanes[1]
        val vPlane = imagePlanes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        var row = 0
        while (row < height) {
            val absoluteRow = cropTop + row
            val chromaRow = absoluteRow shr 1
            val nextRowSharesChroma = row + 1 < height && ((absoluteRow + 1) shr 1) == chromaRow
            val yRowStart = yRowStride * absoluteRow + cropLeft
            val outputRowStart = row * width
            val nextYRowStart = if (nextRowSharesChroma) yRowStart + yRowStride else 0
            val nextOutputRowStart = if (nextRowSharesChroma) outputRowStart + width else 0
            val uRowStart = uRowStride * chromaRow
            val vRowStart = vRowStride * chromaRow
            var col = 0
            var absoluteCol = cropLeft

            while (col < width) {
                val chromaCol = absoluteCol shr 1
                val u = uBuffer.getUnsigned(uRowStart + uPixelStride * chromaCol)
                val v = vBuffer.getUnsigned(vRowStart + vPixelStride * chromaCol)
                val outputIndex = outputRowStart + col
                argb[outputIndex] = yuvToArgb(yBuffer.getUnsigned(yRowStart + col), u, v)
                if (nextRowSharesChroma) {
                    argb[nextOutputRowStart + col] = yuvToArgb(
                        yBuffer.getUnsigned(nextYRowStart + col),
                        u,
                        v
                    )
                }

                if (col + 1 < width && ((absoluteCol + 1) shr 1) == chromaCol) {
                    val nextCol = col + 1
                    argb[outputIndex + 1] = yuvToArgb(yBuffer.getUnsigned(yRowStart + nextCol), u, v)
                    if (nextRowSharesChroma) {
                        argb[nextOutputRowStart + nextCol] = yuvToArgb(
                            yBuffer.getUnsigned(nextYRowStart + nextCol),
                            u,
                            v
                        )
                    }
                    col += 2
                    absoluteCol += 2
                } else {
                    col += 1
                    absoluteCol += 1
                }
            }

            row += if (nextRowSharesChroma) 2 else 1
        }

        val bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        return if (width == targetWidth && height == targetHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                bitmap.recycle()
            }
        }
    }

    private fun obtainArgbBuffer(size: Int): IntArray {
        if (reusableArgb.size != size) {
            reusableArgb = IntArray(size)
        }
        return reusableArgb
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
            recycle()
        }
    }

    private fun ByteBuffer.getUnsigned(index: Int): Int = get(index).toInt() and 0xff

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun yuvToArgb(y: Int, u: Int, v: Int): Int {
        val c = y - 16
        val d = u - 128
        val e = v - 128
        val r = (298 * c + 409 * e + 128) shr 8
        val g = (298 * c - 100 * d - 208 * e + 128) shr 8
        val b = (298 * c + 516 * d + 128) shr 8
        return -0x1000000 or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)
    }

    private fun Int.roundToEven(): Int = if (this % 2 == 0) this else this - 1

    private fun elapsedMs(startedNs: Long): Long = (System.nanoTime() - startedNs) / 1_000_000L

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val DEFAULT_FPS = 30.0
        const val TARGET_PROCESSING_FPS = 15.0
        val TARGET_PROCESSING_INTERVAL_US = (1_000_000.0 / TARGET_PROCESSING_FPS)
            .roundToLong()
            .coerceAtLeast(1L)
    }
}
