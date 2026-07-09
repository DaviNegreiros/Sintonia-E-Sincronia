package com.sintonia.sincronia.processing

import java.nio.ByteBuffer

object NativeLibyuvBridge {
    init {
        System.loadLibrary("sincronia_yuv")
    }

    external fun convertAndroid420ToRgba(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yRowStride: Int,
        uRowStride: Int,
        vRowStride: Int,
        uvPixelStride: Int,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int,
        tempI420A: ByteBuffer,
        tempI420B: ByteBuffer,
        rgbaBuffer: ByteBuffer,
        rgbaStride: Int
    ): Int

    external fun drawPoseSkeletonRgba(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rgbaStride: Int,
        landmarks: FloatArray,
        landmarkCount: Int,
        connections: IntArray,
        connectionCount: Int,
        visibilityThreshold: Float,
        connectionRed: Int,
        connectionGreen: Int,
        connectionBlue: Int,
        landmarkRed: Int,
        landmarkGreen: Int,
        landmarkBlue: Int,
        connectionThickness: Float,
        landmarkRadius: Float
    ): Int

    external fun rgbaToYuv420(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rgbaStride: Int,
        outputBuffer: ByteBuffer,
        outputCapacity: Int,
        planar: Boolean
    ): Int
}
