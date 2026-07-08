package com.sintonia.sincronia.data

import android.content.Context

object AppContainer {
    @Volatile
    private var danceRepository: DanceRepository? = null

    fun danceRepository(context: Context): DanceRepository =
        danceRepository ?: synchronized(this) {
            danceRepository ?: LocalDanceRepository(context).also { danceRepository = it }
        }
}
