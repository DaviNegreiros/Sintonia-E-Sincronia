package com.sintonia.sincronia.processing

import kotlin.math.acos
import kotlin.math.sqrt

class JointAngleCalculator {
    fun calculate(normalizedLandmarks: List<PoseLandmark>): Map<String, Double> {
        if (normalizedLandmarks.isEmpty()) return emptyMap()

        val points = normalizedLandmarks.map { doubleArrayOf(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
        val shoulderCenter = average(points[LEFT_SHOULDER], points[RIGHT_SHOULDER])
        val hipCenter = average(points[LEFT_HIP], points[RIGHT_HIP])

        return linkedMapOf(
            "left_shoulder" to angle(points[LEFT_ELBOW], points[LEFT_SHOULDER], points[LEFT_HIP]),
            "right_shoulder" to angle(points[RIGHT_ELBOW], points[RIGHT_SHOULDER], points[RIGHT_HIP]),
            "left_elbow" to angle(points[LEFT_SHOULDER], points[LEFT_ELBOW], points[LEFT_WRIST]),
            "right_elbow" to angle(points[RIGHT_SHOULDER], points[RIGHT_ELBOW], points[RIGHT_WRIST]),
            "left_hip" to angle(points[LEFT_SHOULDER], points[LEFT_HIP], points[LEFT_KNEE]),
            "right_hip" to angle(points[RIGHT_SHOULDER], points[RIGHT_HIP], points[RIGHT_KNEE]),
            "left_knee" to angle(points[LEFT_HIP], points[LEFT_KNEE], points[LEFT_ANKLE]),
            "right_knee" to angle(points[RIGHT_HIP], points[RIGHT_KNEE], points[RIGHT_ANKLE]),
            "torso" to torsoAngle(shoulderCenter, hipCenter)
        ).mapValues { (_, value) -> value.roundToSixDecimals() }
    }

    private fun angle(first: DoubleArray, middle: DoubleArray, last: DoubleArray): Double {
        val firstVector = subtract(first, middle)
        val lastVector = subtract(last, middle)
        val denominator = norm(firstVector) * norm(lastVector)
        if (denominator <= 1e-8) return 0.0

        val cosine = (dot(firstVector, lastVector) / denominator).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun torsoAngle(shoulderCenter: DoubleArray, hipCenter: DoubleArray): Double {
        val torsoVector = subtract(shoulderCenter, hipCenter)
        val verticalVector = doubleArrayOf(0.0, -1.0, 0.0)
        val denominator = norm(torsoVector) * norm(verticalVector)
        if (denominator <= 1e-8) return 0.0

        val cosine = (dot(torsoVector, verticalVector) / denominator).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun subtract(first: DoubleArray, second: DoubleArray): DoubleArray =
        doubleArrayOf(first[0] - second[0], first[1] - second[1], first[2] - second[2])

    private fun average(first: DoubleArray, second: DoubleArray): DoubleArray =
        doubleArrayOf((first[0] + second[0]) / 2.0, (first[1] + second[1]) / 2.0, (first[2] + second[2]) / 2.0)

    private fun dot(first: DoubleArray, second: DoubleArray): Double =
        first[0] * second[0] + first[1] * second[1] + first[2] * second[2]

    private fun norm(vector: DoubleArray): Double =
        sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2])

    private companion object {
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
    }
}

