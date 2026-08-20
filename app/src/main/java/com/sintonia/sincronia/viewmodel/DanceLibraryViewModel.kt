package com.sintonia.sincronia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sintonia.sincronia.data.DanceRepository
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.DanceResult
import com.sintonia.sincronia.domain.DanceSessionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DanceLibraryUiState(
    val selectedDance: Dance? = null,
    val isDancing: Boolean = false,
    val result: DanceResult? = null
//    Debug report transport disabled for release.
//    val debugReportPath: String? = null
)

class DanceLibraryViewModel(
    private val repository: DanceRepository
) : ViewModel() {
    val dances: StateFlow<List<Dance>> = repository.dances

    private val _uiState = MutableStateFlow(DanceLibraryUiState())
    val uiState: StateFlow<DanceLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.dances.collect { dances ->
                val current = _uiState.value
                val selected = current.selectedDance ?: return@collect
                val updated = dances.firstOrNull { it.id == selected.id }
                _uiState.value = if (updated == null) {
                    DanceLibraryUiState()
                } else {
                    current.copy(selectedDance = updated)
                }
            }
        }
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

    fun cancelDancing() {
        val selectedDance = _uiState.value.selectedDance ?: return
        _uiState.value = DanceLibraryUiState(selectedDance = selectedDance)
    }

    fun finishDancingWithResult(sessionResult: DanceSessionResult) {
        val selectedDance = _uiState.value.selectedDance ?: return
        val result = sessionResult.result
        repository.updateBestRank(selectedDance.id, result.rank)
        _uiState.value = DanceLibraryUiState(
            selectedDance = repository.dances.value.firstOrNull { it.id == selectedDance.id } ?: selectedDance,
            isDancing = false,
            result = result
//            debugReportPath = sessionResult.debugReportPath
        )
    }

    fun closeResult() {
        _uiState.value = DanceLibraryUiState()
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
