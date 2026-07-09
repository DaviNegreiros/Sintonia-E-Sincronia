package com.sintonia.sincronia.processing

import java.nio.ByteBuffer
import java.nio.ByteOrder

class DecodedVideoFrame {
    var index: Int = 0
        private set
    var presentationTimeUs: Long = 0L
        private set
    var timestampMs: Long = 0L
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var rgbaStride: Int = 0
        private set
    var rgbaBuffer: ByteBuffer = emptyDirectBuffer()
        private set

    fun update(
        index: Int,
        presentationTimeUs: Long,
        timestampMs: Long,
        width: Int,
        height: Int,
        rgbaStride: Int,
        rgbaBuffer: ByteBuffer
    ) {
        this.index = index
        this.presentationTimeUs = presentationTimeUs
        this.timestampMs = timestampMs
        this.width = width
        this.height = height
        this.rgbaStride = rgbaStride
        this.rgbaBuffer = rgbaBuffer
    }
}

class RgbaFrameBuffers {
    val frame = DecodedVideoFrame()
    var rgbaBuffer: ByteBuffer = emptyDirectBuffer()
        private set
    var tempI420A: ByteBuffer = emptyDirectBuffer()
        private set
    var tempI420B: ByteBuffer = emptyDirectBuffer()
        private set

    fun ensure(width: Int, height: Int, tempPixelCount: Int) {
        val rgbaSize = width * height * RGBA_BYTES_PER_PIXEL
        if (rgbaBuffer.capacity() != rgbaSize) {
            rgbaBuffer = allocateDirect(rgbaSize)
        }

        val i420Size = tempPixelCount * 3 / 2
        if (tempI420A.capacity() != i420Size) {
            tempI420A = allocateDirect(i420Size)
        }
        if (tempI420B.capacity() != i420Size) {
            tempI420B = allocateDirect(i420Size)
        }
    }

    companion object {
        const val RGBA_BYTES_PER_PIXEL = 4

        private fun allocateDirect(size: Int): ByteBuffer =
            ByteBuffer.allocateDirect(size.coerceAtLeast(0)).order(ByteOrder.nativeOrder())
    }
}

private fun emptyDirectBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
