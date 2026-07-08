package com.sintonia.sincronia.domain

data class CropSelection(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float = left + width
    val bottom: Float = top + height
}
