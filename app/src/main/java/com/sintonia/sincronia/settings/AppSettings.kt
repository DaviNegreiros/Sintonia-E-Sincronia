package com.sintonia.sincronia.settings

data class AppSettings(
    val showSkeleton: Boolean = false,
    val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
) {
    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 10
        val allowedCountdownSeconds = setOf(3, 5, 10)
    }
}

