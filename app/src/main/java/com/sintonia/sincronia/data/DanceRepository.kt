package com.sintonia.sincronia.data

import android.net.Uri
import com.sintonia.sincronia.domain.CropSelection
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.Rank
import com.sintonia.sincronia.domain.VideoInfo
import com.sintonia.sincronia.processing.ImportProgress
import com.sintonia.sincronia.processing.ProcessingPerformanceReport
import kotlinx.coroutines.flow.StateFlow

interface DanceRepository {
    val dances: StateFlow<List<Dance>>
    val importProgress: StateFlow<ImportProgress?>
    val processingPerformanceReport: StateFlow<ProcessingPerformanceReport?>

    suspend fun importDance(
        title: String,
        sourceUri: Uri,
        cropSelection: CropSelection
    ): Result<Dance>

    fun refresh()

    fun readVideoInfo(uri: Uri): VideoInfo

    fun isDanceTitleAvailable(title: String): Boolean

    fun updateBestRank(id: String, rank: Rank)

    fun deleteDance(id: String)
}
