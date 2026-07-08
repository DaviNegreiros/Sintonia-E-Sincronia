package com.sintonia.sincronia.domain

data class Dance(
    val id: Long,
    val name: String,
    val rank: Rank,
    val accentColor: Long,
    val gradientStart: Long,
    val gradientEnd: Long,
    val metadata: DanceMetadata
)
