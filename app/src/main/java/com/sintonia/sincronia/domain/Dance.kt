package com.sintonia.sincronia.domain

import android.net.Uri
import java.io.File

data class Dance(
    val metadata: DanceMetadata,
    val folder: File,
    val videoFile: File,
    val videoUri: Uri,
    val previewUri: Uri?,
    val accentColor: Long,
    val gradientStart: Long,
    val gradientEnd: Long
) {
    val id: String = metadata.id
    val name: String = metadata.title
    val rank: Rank = metadata.bestRank ?: Rank.UNKNOWN
}
