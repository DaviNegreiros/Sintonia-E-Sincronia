package com.sintonia.sincronia.domain

data class DanceResult(
    val danceName: String,
    val rank: Rank,
    val successPercentage: Int
)

data class DanceSessionResult(
    val result: DanceResult
//    Debug report transport disabled for release.
//    val debugReportPath: String? = null
)
