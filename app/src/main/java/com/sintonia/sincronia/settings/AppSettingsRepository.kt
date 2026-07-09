package com.sintonia.sincronia.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setShowSkeleton(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_SKELETON, enabled).apply()
        _settings.value = readSettings()
    }

    fun setCountdownSeconds(seconds: Int) {
        val safeValue = if (seconds in AppSettings.allowedCountdownSeconds) {
            seconds
        } else {
            AppSettings.DEFAULT_COUNTDOWN_SECONDS
        }
        preferences.edit().putInt(KEY_COUNTDOWN_SECONDS, safeValue).apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): AppSettings {
        val countdown = preferences.getInt(KEY_COUNTDOWN_SECONDS, AppSettings.DEFAULT_COUNTDOWN_SECONDS)
        return AppSettings(
            showSkeleton = preferences.getBoolean(KEY_SHOW_SKELETON, false),
            countdownSeconds = if (countdown in AppSettings.allowedCountdownSeconds) {
                countdown
            } else {
                AppSettings.DEFAULT_COUNTDOWN_SECONDS
            }
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "sintonia_settings"
        const val KEY_SHOW_SKELETON = "show_skeleton"
        const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
    }
}

