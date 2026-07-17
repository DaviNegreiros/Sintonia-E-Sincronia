package com.sintonia.sincronia.domain

data class DanceResult(
    val danceName: String,
    val rank: Rank,
    val successPercentage: Int,
    val debugReportPath: String? = null
)
