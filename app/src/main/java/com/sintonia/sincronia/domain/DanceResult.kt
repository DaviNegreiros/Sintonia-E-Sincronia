package com.sintonia.sincronia.domain

data class DanceResult(
    val danceName: String,
    val rank: Rank,
    val successPercentage: Int
)

data class DanceSessionResult(
    val result: DanceResult,
    val debugReportPath: String? = null
)
