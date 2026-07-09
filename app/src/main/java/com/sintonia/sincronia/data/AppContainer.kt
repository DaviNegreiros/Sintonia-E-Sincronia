package com.sintonia.sincronia.data

import android.content.Context
import com.sintonia.sincronia.settings.AppSettingsRepository

object AppContainer {
    @Volatile
    private var danceRepository: DanceRepository? = null
    @Volatile
    private var appSettingsRepository: AppSettingsRepository? = null

    fun danceRepository(context: Context): DanceRepository =
        danceRepository ?: synchronized(this) {
            danceRepository ?: LocalDanceRepository(context, appSettingsRepository(context)).also { danceRepository = it }
        }

    fun appSettingsRepository(context: Context): AppSettingsRepository =
        appSettingsRepository ?: synchronized(this) {
            appSettingsRepository ?: AppSettingsRepository(context).also { appSettingsRepository = it }
        }
}
