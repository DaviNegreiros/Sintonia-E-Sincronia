package com.sintonia.sincronia.processing

import android.util.Log
import kotlin.math.roundToInt

object VideoDecodePerformanceLogger {
    private const val TAG = "DanceVideoDecoder"

    fun videoOpened(openMs: Long, frameCount: Int, fps: Double, width: Int, height: Int) {
        Log.d(TAG, "open=${openMs}ms frames=$frameCount fps=$fps size=${width}x$height")
    }

    fun frameDecoded(frameIndex: Int, decodeMs: Long) {
        if (frameIndex == 0 || frameIndex % 30 == 0) {
            Log.d(TAG, "decodedFrame=$frameIndex decodeMs=$decodeMs")
        }
    }

    fun finished(frameCount: Int, readMs: Long) {
        val effectiveFps = if (readMs > 0L) frameCount * 1000.0 / readMs else 0.0
        Log.d(TAG, "readTotal=${readMs}ms frames=$frameCount effectiveFps=${(effectiveFps * 10).roundToInt() / 10.0}")
    }
}

