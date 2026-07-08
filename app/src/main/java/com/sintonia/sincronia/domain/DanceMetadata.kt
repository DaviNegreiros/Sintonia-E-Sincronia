package com.sintonia.sincronia.domain

data class DanceMetadata(
    val durationSeconds: Int,
    val bpm: Int?,
    val difficulty: Difficulty,
    val source: DanceSource
)

enum class DanceSource {
    Mock,
    LocalVideo,
    ProcessedOffline
}
