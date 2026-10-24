package com.fuelcompare.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.SavedRegion
import com.fuelcompare.domain.model.toUserMessage
import com.fuelcompare.domain.repository.PreferencesRepository
import com.fuelcompare.domain.usecase.ResolveDeviceLocationUseCase
import com.fuelcompare.domain.usecase.ReverseGeocodeLocationUseCase
import com.fuelcompare.domain.usecase.SearchLocationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0,
    val query: String = "",
    val suggestions: List<LocationSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val isLocating: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val resolveDeviceLocation: ResolveDeviceLocationUseCase,
    private val reverseGeocodeLocation: ReverseGeocodeLocationUseCase,
    private val searchLocations: SearchLocationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun goToLocationStep() {
        _uiState.update { it.copy(step = 1, message = null) }
    }

    fun goBack() {
        _uiState.update { it.copy(step = 0, message = null) }
    }

    fun onQueryChange(rawQuery: String) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = rawQuery,
                suggestions = emptyList(),
                isSearching = false,
                message = null,
            )
        }
        if (normalizeQuery(rawQuery).length < 2) return

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(rawQuery)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(_uiState.value.query) }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                message = "Permissão de localização não concedida. " +
                    "Você pode buscar pela cidade ou CEP.",
            )
        }
    }

    fun useDeviceLocation() {
        if (_uiState.value.isLocating || _uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, message = null) }
            val location = resolveDeviceLocation().getOrElse { failure ->
                _uiState.update {
                    it.copy(isLocating = false, message = failure.toUserMessage())
                }
                return@launch
            }
            val region = reverseGeocodeLocation(location.latitude, location.longitude)
                .getOrElse { failure ->
                    _uiState.update {
                        it.copy(isLocating = false, message = failure.toUserMessage())
                    }
                    return@launch
                }
            finishWithRegion(
                SavedRegion(
                    municipality = region.municipality,
                    state = region.state,
                    latitude = location.latitude,
                    longitude = location.longitude,
                ),
            )
        }
    }

    fun selectSuggestion(suggestion: LocationSuggestion) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch { finishWithRegion(suggestion.toSavedRegion()) }
    }

    fun skipLocation() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                preferencesRepository.setOnboardingCompleted(true)
                _uiState.update { it.copy(isSaving = false, completed = true) }
            } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = "Não foi possível salvar sua preferência. Tente novamente.",
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun performSearch(rawQuery: String) {
        val normalized = normalizeQuery(rawQuery)
        if (normalized.length < 2) return
        _uiState.update { it.copy(isSearching = true, message = null, suggestions = emptyList()) }
        val result = searchLocations(normalized, limit = 5)
        if (normalizeQuery(_uiState.value.query) != normalized) return
        result
            .onSuccess { suggestions ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        suggestions = suggestions,
                        message = if (suggestions.isEmpty()) {
                            "Nenhum município encontrado. Tente outro nome ou CEP."
                        } else {
                            null
                        },
                    )
                }
            }
            .onFailure { failure ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        suggestions = emptyList(),
                        message = failure.toUserMessage(),
                    )
                }
            }
    }

    private suspend fun finishWithRegion(region: SavedRegion) {
        _uiState.update { it.copy(isLocating = false, isSaving = true, message = null) }
        try {
            preferencesRepository.setRegion(region)
            preferencesRepository.setOnboardingCompleted(true)
            _uiState.update { it.copy(isSaving = false, completed = true) }
        } catch (error: kotlin.coroutines.cancellation.CancellationException) {
            throw error
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = "Não foi possível salvar sua localização. Tente novamente.",
                )
            }
        }
    }

    private fun normalizeQuery(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        val digits = trimmed.filter(Char::isDigit)
        return if (digits.length == 8 && trimmed.length <= 9) {
            "${digits.take(5)}-${digits.drop(5)}, Brasil"
        } else {
            trimmed
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 450L
    }
}
