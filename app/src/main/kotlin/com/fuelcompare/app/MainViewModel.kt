package com.fuelcompare.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelcompare.domain.model.ThemeMode
import com.fuelcompare.domain.model.toUserMessage
import com.fuelcompare.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val startupError: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _uiState.value = MainUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            preferencesRepository.preferences
                .catch { error ->
                    _uiState.value = MainUiState(
                        isLoading = false,
                        startupError = error.toUserMessage(),
                    )
                }
                .collect { preferences ->
                    _uiState.value = MainUiState(
                        isLoading = false,
                        onboardingCompleted = preferences.onboardingCompleted,
                        themeMode = preferences.themeMode,
                    )
                }
        }
    }
}
