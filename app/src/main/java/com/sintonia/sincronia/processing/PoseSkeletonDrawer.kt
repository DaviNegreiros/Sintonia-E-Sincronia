package com.sintonia.sincronia.processing

import android.graphics.Color

class PoseSkeletonDrawer(
    private val visibilityThreshold: Float = 0.35f,
    private val connectionColor: Int = Color.rgb(0, 255, 0),
    private val landmarkColor: Int = Color.rgb(255, 128, 0),
    private val connectionThickness: Float = 2f,
    private val landmarkRadius: Float = 4f
) {
    private val landmarkData = FloatArray(MAX_POSE_LANDMARKS * VALUES_PER_LANDMARK)

    fun draw(frame: DecodedVideoFrame, landmarks: List<PoseLandmark>) {
        if (landmarks.isEmpty()) return
        val landmarkCount = minOf(landmarks.size, MAX_POSE_LANDMARKS)
        for (index in 0 until landmarkCount) {
            val landmark = landmarks[index]
            val offset = index * VALUES_PER_LANDMARK
            landmarkData[offset] = landmark.x
            landmarkData[offset + 1] = landmark.y
            landmarkData[offset + 2] = landmark.visibility
        }

        val result = NativeLibyuvBridge.drawPoseSkeletonRgba(
            rgbaBuffer = frame.rgbaBuffer,
            width = frame.width,
            height = frame.height,
            rgbaStride = frame.rgbaStride,
            landmarks = landmarkData,
            landmarkCount = landmarkCount,
            connections = CONNECTIONS,
            connectionCount = CONNECTIONS.size / 2,
            visibilityThreshold = visibilityThreshold,
            connectionRed = Color.red(connectionColor),
            connectionGreen = Color.green(connectionColor),
            connectionBlue = Color.blue(connectionColor),
            landmarkRed = Color.red(landmarkColor),
            landmarkGreen = Color.green(landmarkColor),
            landmarkBlue = Color.blue(landmarkColor),
            connectionThickness = connectionThickness,
            landmarkRadius = landmarkRadius
        )
        check(result == 0) { "Falha ao desenhar esqueleto no buffer RGBA: código $result." }
    }

    private companion object {
        const val MAX_POSE_LANDMARKS = 33
        const val VALUES_PER_LANDMARK = 3
        val CONNECTIONS: IntArray = PoseConnections.connections
            .flatMap { (start, end) -> listOf(start, end) }
            .toIntArray()
    }
}
