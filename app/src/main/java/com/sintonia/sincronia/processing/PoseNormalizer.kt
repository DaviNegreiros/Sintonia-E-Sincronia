package com.sintonia.sincronia.processing

import kotlin.math.sqrt

data class NormalizationResult(
    val normalizedLandmarks: List<PoseLandmark>,
    val hipCenter: FloatArray,
    val scaleFactor: Float
)

class PoseNormalizer {
    fun normalize(landmarks: List<PoseLandmark>): NormalizationResult {
        if (landmarks.isEmpty()) {
            return NormalizationResult(
                normalizedLandmarks = emptyList(),
                hipCenter = floatArrayOf(Float.NaN, Float.NaN, Float.NaN),
                scaleFactor = 0f
            )
        }

        val hipCenter = centerBetween(landmarks, LEFT_HIP, RIGHT_HIP)
        val shoulderCenter = centerBetween(landmarks, LEFT_SHOULDER, RIGHT_SHOULDER)
        var scaleFactor = norm(
            shoulderCenter[0] - hipCenter[0],
            shoulderCenter[1] - hipCenter[1],
            shoulderCenter[2] - hipCenter[2]
        )
        if (scaleFactor <= 1e-6f) {
            scaleFactor = 1f
        }

        val normalized = landmarks.map { landmark ->
            PoseLandmark(
                x = ((landmark.x - hipCenter[0]) / scaleFactor).roundToSixDecimals(),
                y = ((landmark.y - hipCenter[1]) / scaleFactor).roundToSixDecimals(),
                z = ((landmark.z - hipCenter[2]) / scaleFactor).roundToSixDecimals(),
                visibility = landmark.visibility.roundToSixDecimals()
            )
        }

        return NormalizationResult(normalized, hipCenter, scaleFactor)
    }

    private fun centerBetween(landmarks: List<PoseLandmark>, firstIndex: Int, secondIndex: Int): FloatArray {
        val first = landmarks[firstIndex]
        val second = landmarks[secondIndex]
        return floatArrayOf(
            (first.x + second.x) / 2f,
            (first.y + second.y) / 2f,
            (first.z + second.z) / 2f
        )
    }

    private fun norm(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

    private companion object {
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
    }
}

