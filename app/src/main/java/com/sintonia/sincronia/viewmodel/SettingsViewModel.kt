package com.sintonia.sincronia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sintonia.sincronia.settings.AppSettings
import com.sintonia.sincronia.settings.AppSettingsRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val repository: AppSettingsRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings

    fun setShowSkeleton(enabled: Boolean) {
        repository.setShowSkeleton(enabled)
    }

    fun setCountdownSeconds(seconds: Int) {
        repository.setCountdownSeconds(seconds)
    }
}

class SettingsViewModelFactory(
    private val repository: AppSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(repository) as T
    }
}

