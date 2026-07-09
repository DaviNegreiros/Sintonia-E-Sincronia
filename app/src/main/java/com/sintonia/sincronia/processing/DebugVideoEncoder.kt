package com.sintonia.sincronia.processing

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class DebugVideoEncoder(
    private val outputFile: File,
    private val sourceVideoFile: File,
    private val width: Int,
    private val height: Int,
    fps: Double
) : AutoCloseable {
    private val frameRate = fps.roundToInt().coerceIn(1, 60)
    private val colorFormat = selectColorFormat()
    private val encoder: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()
    private val audioTrackFormat = sourceVideoFile.findAudioTrackFormat()
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var audioCopied = false
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

    fun encode(
        rgbaBuffer: ByteBuffer,
        rgbaStride: Int,
        presentationTimeUs: Long,
        performanceTracker: ProcessingPerformanceTracker? = null
    ) {
        val inputIndex = performanceTracker?.measureDebugEncoder {
            encoder.dequeueInputBuffer(TIMEOUT_US)
        } ?: encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = requireNotNull(encoder.getInputBuffer(inputIndex))
            inputBuffer.clear()
            if (performanceTracker == null) {
                writeRgbaAsYuv420(rgbaBuffer, rgbaStride, inputBuffer, colorFormat)
                encoder.queueInputBuffer(inputIndex, 0, yuvBufferSize(), presentationTimeUs, 0)
            } else {
                performanceTracker.measureRgbaToEncoderFormat {
                    writeRgbaAsYuv420(rgbaBuffer, rgbaStride, inputBuffer, colorFormat)
                }
                performanceTracker.measureDebugEncoder {
                    encoder.queueInputBuffer(inputIndex, 0, yuvBufferSize(), presentationTimeUs, 0)
                }
            }
        }
        if (performanceTracker == null) {
            drain(endOfStream = false)
        } else {
            performanceTracker.measureDebugEncoder {
                drain(endOfStream = false)
            }
        }
    }

    fun finish() {
        val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            encoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drain(endOfStream = true)
        runCatching { copyAudioTrack() }
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
                    videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                    audioTrackIndex = audioTrackFormat?.let { format ->
                        runCatching { muxer.addTrack(format) }.getOrDefault(NO_TRACK)
                    } ?: NO_TRACK
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
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }

                    val reachedEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (reachedEnd) return
                }
            }
        }
    }

    private fun copyAudioTrack() {
        if (audioCopied || audioTrackIndex == NO_TRACK || !muxerStarted) return
        audioCopied = true

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceVideoFile.absolutePath)
            val sourceTrackIndex = extractor.selectFirstAudioTrack()
            if (sourceTrackIndex == NO_TRACK) return

            val sourceFormat = extractor.getTrackFormat(sourceTrackIndex)
            val maxInputSize = if (sourceFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                sourceFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                DEFAULT_AUDIO_BUFFER_SIZE
            }.coerceAtLeast(DEFAULT_AUDIO_BUFFER_SIZE)
            val sampleBuffer = ByteBuffer.allocateDirect(maxInputSize)
            val sampleInfo = MediaCodec.BufferInfo()

            while (true) {
                sampleBuffer.clear()
                val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs >= 0L) {
                    sampleInfo.set(
                        0,
                        sampleSize,
                        sampleTimeUs,
                        extractor.sampleFlags
                    )
                    sampleBuffer.position(0)
                    sampleBuffer.limit(sampleSize)
                    muxer.writeSampleData(audioTrackIndex, sampleBuffer, sampleInfo)
                }
                extractor.advance()
            }
        } finally {
            extractor.release()
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

    private fun writeRgbaAsYuv420(rgbaBuffer: ByteBuffer, rgbaStride: Int, output: ByteBuffer, format: Int) {
        val planar = format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        val result = NativeLibyuvBridge.rgbaToYuv420(
            rgbaBuffer = rgbaBuffer,
            width = width,
            height = height,
            rgbaStride = rgbaStride,
            outputBuffer = output,
            outputCapacity = yuvBufferSize(),
            planar = planar
        )
        check(result == 0) { "Falha ao converter RGBA para YUV do encoder: código $result." }
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
        const val NO_TRACK = -1
        const val DEFAULT_AUDIO_BUFFER_SIZE = 256 * 1024
    }
}

private fun File.findAudioTrackFormat(): MediaFormat? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(absolutePath)
        val trackIndex = extractor.selectFirstAudioTrack()
        if (trackIndex == -1) null else extractor.getTrackFormat(trackIndex)
    } finally {
        extractor.release()
    }
}

private fun MediaExtractor.selectFirstAudioTrack(): Int {
    for (index in 0 until trackCount) {
        val format = getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime?.startsWith("audio/") == true) {
            selectTrack(index)
            return index
        }
    }
    return -1
}
