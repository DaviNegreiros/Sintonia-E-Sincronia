package com.sintonia.sincronia.domain

data class DanceMetadata(
    val id: String,
    val title: String,
    val video: String,
    val preview: String?,
    val moveset: String?,
    val bestRank: Rank?
)
