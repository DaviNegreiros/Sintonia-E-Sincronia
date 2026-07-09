package com.sintonia.sincronia.processing

import kotlin.math.roundToInt

object ProcessingPerformanceConfig {
    const val SHOW_PROCESSING_REPORT = true
}

data class ProcessingPerformanceReport(
    val durationSeconds: Double,
    val width: Int,
    val height: Int,
    val originalFps: Double,
    val effectiveProcessingFps: Double,
    val totalFramesDecoded: Int,
    val processedFrames: Int,
    val sampledOutFrames: Int,
    val totalProcessingMs: Long,
    val videoImportMs: Long,
    val decoderMs: Long,
    val decoderWaitReadMs: Long,
    val decoderGetOutputImageMs: Long,
    val imageToBitmapMs: Long,
    val bitmapTransformMs: Long,
    val mediaPipeMs: Long,
    val normalizationMs: Long,
    val angleCalculationMs: Long,
    val movesetFrameBuildMs: Long,
    val movesetJsonWriteMs: Long,
    val debugVideoMs: Long,
    val debugEncoderMs: Long
) {
    fun toDisplayText(): String = buildString {
        appendLine("Tempo total: ${totalProcessingMs.formatDuration()}")
        appendLine()
        appendLine("Vídeo:")
        appendLine("${durationSeconds.roundToOne()}s · ${originalFps.roundToOne()} FPS · ${width}x$height")
        appendLine("FPS processado: ${effectiveProcessingFps.roundToOne()}")
        appendLine()
        appendLine("Frames:")
        appendLine("Decodificados: $totalFramesDecoded")
        appendLine("Processados: $processedFrames")
        appendLine("Descartados: $sampledOutFrames")
        appendLine()
        appendLine("Tempo por etapa:")
        appendLine("Importação/crop: ${videoImportMs.formatDuration()}")
        appendLine("Decoder total: ${decoderMs.formatDuration()}")
        appendLine("  wait/read codec: ${decoderWaitReadMs.formatDuration()}")
        appendLine("  getOutputImage: ${decoderGetOutputImageMs.formatDuration()}")
        appendLine("  Image -> Bitmap: ${imageToBitmapMs.formatDuration()}")
        appendLine("  rotação/resize: ${bitmapTransformMs.formatDuration()}")
        appendLine("MediaPipe: ${mediaPipeMs.formatDuration()}")
        appendLine("Normalização: ${normalizationMs.formatDuration()}")
        appendLine("Ângulos: ${angleCalculationMs.formatDuration()}")
        appendLine("Frames JSON: ${movesetFrameBuildMs.formatDuration()}")
        appendLine("Escrita JSON: ${movesetJsonWriteMs.formatDuration()}")
        appendLine("Debug video: ${debugVideoMs.formatDuration()}")
        appendLine("Encoder debug: ${debugEncoderMs.formatDuration()}")
    }
}

class ProcessingPerformanceTracker {
    private var totalStartedAtMs = 0L
    private var videoImportMs = 0L
    private var decoderWaitReadMs = 0L
    private var decoderGetOutputImageMs = 0L
    private var imageToBitmapMs = 0L
    private var bitmapTransformMs = 0L
    private var mediaPipeMs = 0L
    private var normalizationMs = 0L
    private var angleCalculationMs = 0L
    private var movesetFrameBuildMs = 0L
    private var movesetJsonWriteMs = 0L
    private var debugVideoMs = 0L
    private var debugEncoderMs = 0L
    private var totalProcessingMs = 0L
    private var durationSeconds = 0.0
    private var width = 0
    private var height = 0
    private var originalFps = 0.0
    private var decodedFrames = 0
    private var processedFrames = 0
    private var sampledOutFrames = 0

    fun startTotal() {
        totalStartedAtMs = now()
    }

    fun finishTotal() {
        totalProcessingMs = elapsedSince(totalStartedAtMs)
    }

    fun recordVideoInfo(info: DecodedVideoInfo) {
        durationSeconds = info.durationSeconds
        width = info.width
        height = info.height
        originalFps = info.fps
    }

    fun recordDecodedFrame() {
        decodedFrames += 1
    }

    fun recordProcessedFrame() {
        processedFrames += 1
    }

    fun recordSampledOutFrame() {
        sampledOutFrames += 1
    }

    fun addDecoderWaitReadTime(ms: Long) {
        decoderWaitReadMs += ms
    }

    fun addDecoderGetOutputImageTime(ms: Long) {
        decoderGetOutputImageMs += ms
    }

    fun addVideoImportTime(ms: Long) {
        videoImportMs += ms
    }

    fun addDebugVideoTime(ms: Long) {
        debugVideoMs += ms
    }

    fun addDebugEncoderTime(ms: Long) {
        debugEncoderMs += ms
    }

    fun <T> measureVideoImport(block: () -> T): T =
        measure({ videoImportMs += it }, block)

    fun <T> measureImageToBitmap(block: () -> T): T =
        measure({ imageToBitmapMs += it }, block)

    fun <T> measureBitmapTransform(block: () -> T): T =
        measure({ bitmapTransformMs += it }, block)

    fun <T> measureMediaPipe(block: () -> T): T =
        measure({ mediaPipeMs += it }, block)

    fun <T> measureNormalization(block: () -> T): T =
        measure({ normalizationMs += it }, block)

    fun <T> measureAngleCalculation(block: () -> T): T =
        measure({ angleCalculationMs += it }, block)

    fun <T> measureMovesetFrameBuild(block: () -> T): T =
        measure({ movesetFrameBuildMs += it }, block)

    fun <T> measureMovesetJsonWrite(block: () -> T): T =
        measure({ movesetJsonWriteMs += it }, block)

    fun <T> measureDebugEncoder(block: () -> T): T =
        measure({ debugEncoderMs += it }, block)

    fun <T> measureDebugVideo(block: () -> T): T =
        measure({ debugVideoMs += it }, block)

    fun buildReport(): ProcessingPerformanceReport {
        val effectiveFps = if (durationSeconds > 0.0) processedFrames / durationSeconds else 0.0
        val decoderMs = decoderWaitReadMs + decoderGetOutputImageMs + imageToBitmapMs + bitmapTransformMs
        return ProcessingPerformanceReport(
            durationSeconds = durationSeconds,
            width = width,
            height = height,
            originalFps = originalFps,
            effectiveProcessingFps = effectiveFps,
            totalFramesDecoded = decodedFrames,
            processedFrames = processedFrames,
            sampledOutFrames = sampledOutFrames,
            totalProcessingMs = totalProcessingMs,
            videoImportMs = videoImportMs,
            decoderMs = decoderMs,
            decoderWaitReadMs = decoderWaitReadMs,
            decoderGetOutputImageMs = decoderGetOutputImageMs,
            imageToBitmapMs = imageToBitmapMs,
            bitmapTransformMs = bitmapTransformMs,
            mediaPipeMs = mediaPipeMs,
            normalizationMs = normalizationMs,
            angleCalculationMs = angleCalculationMs,
            movesetFrameBuildMs = movesetFrameBuildMs,
            movesetJsonWriteMs = movesetJsonWriteMs,
            debugVideoMs = debugVideoMs,
            debugEncoderMs = debugEncoderMs
        )
    }

    private fun <T> measure(addElapsed: (Long) -> Unit, block: () -> T): T {
        val startedAt = now()
        return try {
            block()
        } finally {
            addElapsed(elapsedSince(startedAt))
        }
    }

    companion object {
        fun now(): Long = System.currentTimeMillis()
        fun elapsedSince(startedAtMs: Long): Long = now() - startedAtMs
    }
}

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = this % 1000
    return if (minutes > 0) {
        "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    } else {
        "${seconds}.${(millis / 100).coerceIn(0, 9)}s"
    }
}

private fun Double.roundToOne(): Double = (this * 10.0).roundToInt() / 10.0
