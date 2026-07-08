package com.sintonia.sincronia.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NewDanceUiState(
    val name: String = "",
    val hasVideo: Boolean = false
) {
    val canCreate: Boolean = name.trim().isNotEmpty()
}

class NewDanceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NewDanceUiState())
    val uiState: StateFlow<NewDanceUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun markVideoSelected() {
        _uiState.update { it.copy(hasVideo = true) }
    }

    fun reset() {
        _uiState.value = NewDanceUiState()
    }
}
