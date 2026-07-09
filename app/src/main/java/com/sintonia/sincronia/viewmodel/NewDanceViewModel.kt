package com.sintonia.sincronia.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sintonia.sincronia.data.DuplicateDanceNameException
import com.sintonia.sincronia.data.DanceRepository
import com.sintonia.sincronia.domain.CropSelection
import com.sintonia.sincronia.domain.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NewDanceUiState(
    val name: String = "",
    val sourceUri: Uri? = null,
    val videoInfo: VideoInfo = VideoInfo(width = 1080, height = 1920),
    val isImporting: Boolean = false,
    val importedDanceId: String? = null,
    val isDuplicateName: Boolean = false,
    val errorMessage: String? = null
) {
    val hasVideo: Boolean = sourceUri != null
    val canPickVideo: Boolean = name.trim().isNotEmpty() && !isDuplicateName && !isImporting
    val canConfirmCrop: Boolean = name.trim().isNotEmpty() && !isDuplicateName && sourceUri != null && !isImporting
}

class NewDanceViewModel(
    private val repository: DanceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewDanceUiState())
    val uiState: StateFlow<NewDanceUiState> = _uiState.asStateFlow()
    val importProgress = repository.importProgress
    val processingPerformanceReport = repository.processingPerformanceReport
    private var importJob: Job? = null

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                isDuplicateName = name.trim().isNotEmpty() && !repository.isDanceTitleAvailable(name),
                errorMessage = null
            )
        }
    }

    fun selectVideo(uri: Uri) {
        val currentState = _uiState.value
        if (currentState.isDuplicateName) {
            _uiState.update { it.copy(errorMessage = DUPLICATE_NAME_MESSAGE) }
            return
        }

        _uiState.update {
            it.copy(
                sourceUri = uri,
                videoInfo = repository.readVideoInfo(uri),
                errorMessage = null,
                importedDanceId = null
            )
        }
    }

    fun cancelVideoSelection() {
        _uiState.value = NewDanceUiState()
    }

    fun importSelectedVideo(cropSelection: CropSelection) {
        val currentState = _uiState.value
        val uri = currentState.sourceUri ?: return
        if (currentState.isImporting || currentState.name.trim().isEmpty()) return
        if (currentState.isDuplicateName) {
            _uiState.update { it.copy(errorMessage = DUPLICATE_NAME_MESSAGE) }
            return
        }

        _uiState.update { it.copy(isImporting = true, errorMessage = null) }
        importJob = viewModelScope.launch {
            repository.importDance(
                title = currentState.name,
                sourceUri = uri,
                cropSelection = cropSelection
            ).onSuccess { dance ->
                _uiState.update {
                    it.copy(isImporting = false, importedDanceId = dance.id)
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    _uiState.update { it.copy(isImporting = false, errorMessage = null) }
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        isDuplicateName = error is DuplicateDanceNameException,
                        errorMessage = if (error is DuplicateDanceNameException) {
                            DUPLICATE_NAME_MESSAGE
                        } else {
                            error.localizedMessage ?: "Não foi possível importar o vídeo."
                        }
                    )
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _uiState.update { it.copy(isImporting = false, errorMessage = null) }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importedDanceId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reset() {
        _uiState.value = NewDanceUiState()
    }

    override fun onCleared() {
        importJob?.cancel()
        importJob = null
        super.onCleared()
    }

    private companion object {
        const val DUPLICATE_NAME_MESSAGE = "Esse nome já está em uso."
    }
}

class NewDanceViewModelFactory(
    private val repository: DanceRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NewDanceViewModel(repository) as T
    }
}
