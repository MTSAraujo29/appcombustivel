package com.fuelcompare.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.ThemeMode
import com.fuelcompare.domain.repository.PreferencesRepository
import com.fuelcompare.domain.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val fuel: FuelType = FuelType.GASOLINE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val radiusKm: Int = 5,
    val regionLabel: String? = null,
    val isClearingCache: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val stationRepository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fuel = preferences.fuel,
                        themeMode = preferences.themeMode,
                        radiusKm = preferences.radiusKm,
                        regionLabel = preferences.region?.label,
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (mode == _uiState.value.themeMode) return
        _uiState.update { it.copy(themeMode = mode) }
        persist("Não foi possível salvar o tema.") {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setFuel(fuel: FuelType) {
        if (fuel == _uiState.value.fuel) return
        _uiState.update { it.copy(fuel = fuel) }
        persist("Não foi possível salvar o combustível padrão.") {
            preferencesRepository.setFuel(fuel)
        }
    }

    fun setRadiusKm(radiusKm: Int) {
        val normalized = radiusKm.coerceIn(1, 10)
        if (normalized == _uiState.value.radiusKm) return
        _uiState.update { it.copy(radiusKm = normalized) }
        persist("Não foi possível salvar o raio de busca.") {
            preferencesRepository.setRadiusKm(normalized)
        }
    }

    fun clearCache() {
        if (_uiState.value.isClearingCache) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isClearingCache = true, message = null, messageIsError = false)
            }
            try {
                stationRepository.clearCache()
                _uiState.update {
                    it.copy(
                        isClearingCache = false,
                        message = "Cópias locais de postos e preços foram removidas.",
                        messageIsError = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isClearingCache = false,
                        message = "Não foi possível limpar os dados salvos. Tente novamente.",
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun persist(errorMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(message = errorMessage, messageIsError = true)
                }
            }
        }
    }
}
