package com.sintonia.sincronia.processing

data class ImportProgress(
    val message: String,
    val progress: Float
) {
    val percent: Int = (progress.coerceIn(0f, 1f) * 100f).toInt()
}

