package com.sintonia.sincronia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sintonia.sincronia.data.DanceRepository
import com.sintonia.sincronia.domain.Dance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DanceLibraryUiState(
    val selectedDance: Dance? = null,
    val isDancing: Boolean = false
)

class DanceLibraryViewModel(
    private val repository: DanceRepository
) : ViewModel() {
    val dances: StateFlow<List<Dance>> = repository.dances

    private val _uiState = MutableStateFlow(DanceLibraryUiState())
    val uiState: StateFlow<DanceLibraryUiState> = _uiState.asStateFlow()

    fun createDance(name: String) {
        repository.createDance(name)
    }

    fun selectDance(dance: Dance) {
        _uiState.value = DanceLibraryUiState(selectedDance = dance)
    }

    fun closeOverlay() {
        _uiState.value = DanceLibraryUiState()
    }

    fun startDancing() {
        val selectedDance = _uiState.value.selectedDance ?: return
        _uiState.value = DanceLibraryUiState(selectedDance = selectedDance, isDancing = true)
    }

    fun deleteSelectedDance() {
        val selectedDance = _uiState.value.selectedDance ?: return
        repository.deleteDance(selectedDance.id)
        closeOverlay()
    }
}

class DanceLibraryViewModelFactory(
    private val repository: DanceRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DanceLibraryViewModel(repository) as T
    }
}
