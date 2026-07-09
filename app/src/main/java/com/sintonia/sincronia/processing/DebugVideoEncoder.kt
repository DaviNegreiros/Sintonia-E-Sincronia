package com.sintonia.sincronia.processing

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class DebugVideoEncoder(
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    fps: Double
) : AutoCloseable {
    private val frameRate = fps.roundToInt().coerceIn(1, 60)
    private val colorFormat = selectColorFormat()
    private val encoder: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var closed = false

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 3).coerceAtLeast(1_500_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    fun encode(bitmap: Bitmap, presentationTimeUs: Long) {
        val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = requireNotNull(encoder.getInputBuffer(inputIndex))
            inputBuffer.clear()
            writeBitmapAsYuv420(bitmap, inputBuffer, colorFormat)
            encoder.queueInputBuffer(inputIndex, 0, yuvBufferSize(), presentationTimeUs, 0)
        }
        drain(endOfStream = false)
    }

    fun finish() {
        val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            encoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drain(endOfStream = true)
    }

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Encoder output format changed after muxer start." }
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val encodedData = requireNotNull(encoder.getOutputBuffer(outputIndex))
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {
                        check(muxerStarted) { "Muxer has not started." }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    val reachedEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (reachedEnd) return
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { encoder.stop() }
        encoder.release()
        if (muxerStarted) {
            runCatching { muxer.stop() }
        }
        muxer.release()
    }

    private fun writeBitmapAsYuv420(bitmap: Bitmap, output: ByteBuffer, format: Int) {
        val argb = IntArray(width * height)
        if (bitmap.width == width && bitmap.height == height) {
            bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        } else {
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            scaled.getPixels(argb, 0, width, 0, 0, width, height)
            scaled.recycle()
        }

        val ySize = width * height
        val uvSize = ySize / 4
        val bytes = ByteArray(yuvBufferSize())
        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uvSize
        var uvInterleavedIndex = ySize
        val semiPlanar = format != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

        for (row in 0 until height) {
            for (col in 0 until width) {
                val pixel = argb[row * width + col]
                val r = pixel shr 16 and 0xff
                val g = pixel shr 8 and 0xff
                val b = pixel and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                bytes[yIndex++] = y.coerceIn(0, 255).toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    if (semiPlanar) {
                        bytes[uvInterleavedIndex++] = u.coerceIn(0, 255).toByte()
                        bytes[uvInterleavedIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        bytes[uIndex++] = u.coerceIn(0, 255).toByte()
                        bytes[vIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }

        output.put(bytes)
    }

    private fun yuvBufferSize(): Int = width * height * 3 / 2

    private fun selectColorFormat(): Int {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codec = codecList.codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
        } ?: return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

        val capabilities = codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val preferred = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        return preferred.firstOrNull { it in capabilities.colorFormats }
            ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
