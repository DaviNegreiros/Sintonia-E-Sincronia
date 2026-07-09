package com.sintonia.sincronia.processing

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
) {
    fun rounded(): PoseLandmark = PoseLandmark(
        x = x.roundToSixDecimals(),
        y = y.roundToSixDecimals(),
        z = z.roundToSixDecimals(),
        visibility = visibility.roundToSixDecimals()
    )
}

internal fun Float.roundToSixDecimals(): Float =
    (kotlin.math.round(this * 1_000_000f) / 1_000_000f)

internal fun Double.roundToSixDecimals(): Double =
    kotlin.math.round(this * 1_000_000.0) / 1_000_000.0

