package com.fuelcompare.feature.comparison

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelcompare.domain.model.NearbyQuery
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.toUserMessage
import com.fuelcompare.domain.repository.PreferencesRepository
import com.fuelcompare.domain.usecase.CompareStationsUseCase
import com.fuelcompare.domain.usecase.GetNearbyStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ComparisonSlot(val label: String) {
    A("Posto A"),
    B("Posto B"),
}

data class ComparisonUiState(
    val selectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val isComparing: Boolean = false,
    val comparison: StationComparison? = null,
    val errorMessage: String? = null,
    val selectionMessage: String? = null,
    val activeSlot: ComparisonSlot = ComparisonSlot.A,
    val selectedA: Station? = null,
    val selectedB: Station? = null,
    val nearby: List<Station> = emptyList(),
    val nearbyLoading: Boolean = false,
    val nearbyError: String? = null,
    val hasRegion: Boolean = true,
    val regionLabel: String? = null,
    val fromCache: Boolean = false,
    val cachedAt: Instant? = null,
)

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val compareStations: CompareStationsUseCase,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val initialAId: String? = savedStateHandle
        .get<String>(ARG_STATION_A)
        ?.takeIf { it.isNotBlank() }
    private val initialBId: String? = savedStateHandle
        .get<String>(ARG_STATION_B)
        ?.takeIf { it.isNotBlank() }
    private var pendingPreselectId: String? = null
    private var nearbyJob: Job? = null
    private var compareJob: Job? = null

    private val _uiState = MutableStateFlow(
        ComparisonUiState(
            isLoading = initialAId != null && initialBId != null && initialAId != initialBId,
            selectionMode = initialAId == null || initialBId == null || initialAId == initialBId,
        ),
    )
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    init {
        if (initialAId != null && initialBId != null && initialAId != initialBId) {
            runCompare(initialAId, initialBId, automatic = true)
        } else {
            pendingPreselectId = initialAId ?: initialBId
            loadNearby()
        }
    }

    fun retry() {
        val state = _uiState.value
        val comparison = state.comparison
        when {
            comparison != null -> runCompare(
                stationAId = comparison.stationA.id,
                stationBId = comparison.stationB.id,
                automatic = false,
            )

            state.selectedA != null && state.selectedB != null -> runCompare(
                stationAId = state.selectedA.id,
                stationBId = state.selectedB.id,
                automatic = false,
            )

            initialAId != null && initialBId != null -> runCompare(
                stationAId = initialAId,
                stationBId = initialBId,
                automatic = true,
            )

            else -> loadNearby()
        }
    }

    fun retryNearby() = loadNearby()

    fun selectSlot(slot: ComparisonSlot) {
        _uiState.update {
            it.copy(activeSlot = slot, selectionMessage = null)
        }
    }

    fun chooseStation(station: Station) {
        val state = _uiState.value
        if (state.isComparing) return
        val target = state.activeSlot
        val other = if (target == ComparisonSlot.A) state.selectedB else state.selectedA
        if (other?.id == station.id) {
            _uiState.update {
                it.copy(
                    selectionMessage = "Escolha um posto diferente para ${target.label}. " +
                        "Este posto já está do outro lado da comparação.",
                )
            }
            return
        }

        val selectedA = if (target == ComparisonSlot.A) station else state.selectedA
        val selectedB = if (target == ComparisonSlot.B) station else state.selectedB
        val nextSlot = when (target) {
            ComparisonSlot.A -> if (state.selectedB == null) ComparisonSlot.B else ComparisonSlot.A
            ComparisonSlot.B -> if (state.selectedA == null) ComparisonSlot.A else ComparisonSlot.B
        }
        _uiState.update {
            it.copy(
                selectedA = selectedA,
                selectedB = selectedB,
                activeSlot = nextSlot,
                selectionMessage = null,
            )
        }
    }

    fun clearSlot(slot: ComparisonSlot) {
        _uiState.update {
            when (slot) {
                ComparisonSlot.A -> it.copy(selectedA = null, activeSlot = ComparisonSlot.A)
                ComparisonSlot.B -> it.copy(selectedB = null, activeSlot = ComparisonSlot.B)
            }.copy(selectionMessage = null)
        }
    }

    fun compareSelected() {
        val state = _uiState.value
        val a = state.selectedA
        val b = state.selectedB
        if (a == null || b == null) {
            _uiState.update {
                it.copy(selectionMessage = "Selecione os dois postos para comparar.")
            }
            return
        }
        if (a.id == b.id) {
            _uiState.update {
                it.copy(selectionMessage = "Selecione dois postos diferentes para comparar.")
            }
            return
        }
        runCompare(a.id, b.id, automatic = false)
    }

    fun startNewComparison() {
        compareJob?.cancel()
        _uiState.update {
            it.copy(
                selectionMode = true,
                isLoading = false,
                isComparing = false,
                comparison = null,
                errorMessage = null,
                selectionMessage = null,
                activeSlot = ComparisonSlot.A,
                selectedA = null,
                selectedB = null,
            )
        }
        loadNearby()
    }

    fun dismissSelectionMessage() {
        _uiState.update { it.copy(selectionMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun runCompare(
        stationAId: String,
        stationBId: String,
        automatic: Boolean,
    ) {
        compareJob?.cancel()
        compareJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = automatic && current.comparison == null,
                    isComparing = !automatic,
                    errorMessage = null,
                    selectionMessage = null,
                )
            }
            compareStations(stationAId, stationBId)
                .onSuccess { result ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isComparing = false,
                            selectionMode = false,
                            comparison = result.data,
                            fromCache = result.fromCache,
                            cachedAt = result.cachedAt,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isComparing = false,
                            errorMessage = failure.toUserMessage(),
                        )
                    }
                }
        }
    }

    private fun loadNearby() {
        nearbyJob?.cancel()
        nearbyJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    nearbyLoading = true,
                    nearbyError = null,
                    selectionMode = true,
                )
            }
            val preferences = try {
                preferencesRepository.preferences.first()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        nearbyLoading = false,
                        nearbyError = "Não foi possível ler a região salva neste aparelho.",
                    )
                }
                return@launch
            }

            val region = preferences.region
            if (region == null) {
                _uiState.update {
                    it.copy(
                        nearbyLoading = false,
                        hasRegion = false,
                        regionLabel = null,
                        nearby = emptyList(),
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(hasRegion = true, regionLabel = region.label)
            }

            getNearbyStations(
                NearbyQuery(
                    latitude = region.latitude,
                    longitude = region.longitude,
                    radiusKm = preferences.radiusKm,
                    limit = NEARBY_LIMIT,
                    city = region.municipality,
                    state = region.state,
                ),
            )
                .onSuccess { result ->
                    val stations = result.data.stations
                        .sortedWith(
                            compareBy<Station> { it.distanceKm ?: Double.MAX_VALUE }
                                .thenBy { it.name.lowercase() }
                                .thenBy { it.id },
                        )
                    _uiState.update { current ->
                        var selectedA = current.selectedA
                        var selectedB = current.selectedB
                        var activeSlot = current.activeSlot
                        val preselect = pendingPreselectId
                        pendingPreselectId = null
                        if (preselect != null) {
                            val station = stations.firstOrNull { it.id == preselect }
                            if (station != null && selectedA == null && selectedB == null) {
                                selectedA = station
                                activeSlot = ComparisonSlot.B
                            }
                        }
                        current.copy(
                            nearby = stations,
                            nearbyLoading = false,
                            nearbyError = null,
                            fromCache = result.fromCache,
                            cachedAt = result.cachedAt,
                            selectedA = selectedA,
                            selectedB = selectedB,
                            activeSlot = activeSlot,
                            regionLabel = result.data.region?.label ?: current.regionLabel,
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            nearbyLoading = false,
                            nearbyError = failure.toUserMessage(),
                        )
                    }
                }
        }
    }

    companion object {
        const val ARG_STATION_A = "stationA"
        const val ARG_STATION_B = "stationB"
        private const val NEARBY_LIMIT = 50
    }
}
