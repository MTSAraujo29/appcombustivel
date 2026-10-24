package com.fuelcompare.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.NearbyQuery
import com.fuelcompare.domain.model.Region
import com.fuelcompare.domain.model.RegionOrigin
import com.fuelcompare.domain.model.SavedRegion
import com.fuelcompare.domain.model.SortMode
import com.fuelcompare.domain.model.SourceAttribution
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.toUserMessage
import com.fuelcompare.domain.model.RegionalFuelPrice
import com.fuelcompare.domain.repository.PreferencesRepository
import com.fuelcompare.domain.rules.CheapestStation
import com.fuelcompare.domain.rules.PriceRules
import com.fuelcompare.domain.usecase.GetCheapestStationUseCase
import com.fuelcompare.domain.usecase.GetNearbyStationsUseCase
import com.fuelcompare.domain.usecase.ResolveDeviceLocationUseCase
import com.fuelcompare.domain.usecase.ReverseGeocodeLocationUseCase
import com.fuelcompare.domain.usecase.SearchLocationsUseCase
import com.fuelcompare.domain.usecase.SortStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeViewMode { MAP, LIST }

data class MapPoint(
    val latitude: Double,
    val longitude: Double,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val stations: List<Station> = emptyList(),
    val sortedStations: List<Station> = emptyList(),
    val regionalPrices: List<RegionalFuelPrice> = emptyList(),
    val region: Region? = null,
    val selectedFuel: FuelType = FuelType.GASOLINE,
    val radiusKm: Int = 5,
    val sortMode: SortMode = SortMode.DISTANCE,
    val viewMode: HomeViewMode = HomeViewMode.MAP,
    val lastUpdated: Instant? = null,
    val sources: List<SourceAttribution> = emptyList(),
    val warnings: List<String> = emptyList(),
    val fromCache: Boolean = false,
    val cachedAt: Instant? = null,
    val errorMessage: String? = null,
    val needsLocation: Boolean = false,
    val isLocating: Boolean = false,
    val cheapest: CheapestStation? = null,
    val topPricedStationIds: List<String> = emptyList(),
    val queryCenter: MapPoint? = null,
    val mapFocus: MapPoint? = null,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LocationSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val isSavingSearch: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val getNearbyStations: GetNearbyStationsUseCase,
    private val getCheapestStation: GetCheapestStationUseCase,
    private val sortStations: SortStationsUseCase,
    private val searchLocations: SearchLocationsUseCase,
    private val resolveDeviceLocation: ResolveDeviceLocationUseCase,
    private val reverseGeocodeLocation: ReverseGeocodeLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var useRegionHint = true

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect(::applyPreferences)
        }
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    fun retry() {
        load(forceRefresh = true)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun selectFuel(fuel: FuelType) {
        if (_uiState.value.selectedFuel == fuel) return
        _uiState.update { it.copy(selectedFuel = fuel).derived() }
        viewModelScope.launch { runCatching { preferencesRepository.setFuel(fuel) } }
    }

    fun selectSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode).derived() }
    }

    fun setViewMode(mode: HomeViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun selectRadius(radiusKm: Int) {
        val normalized = radiusKm.coerceIn(1, 10)
        if (normalized == _uiState.value.radiusKm) return
        _uiState.update { it.copy(radiusKm = normalized) }
        viewModelScope.launch { runCatching { preferencesRepository.setRadiusKm(normalized) } }
        load(forceRefresh = true)
    }

    fun onMapMoved(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        val state = _uiState.value
        if (state.searchVisible || state.isLoading) return
        val previous = state.queryCenter ?: state.region?.let { MapPoint(it.latitude, it.longitude) }
        if (previous != null && haversineKm(previous, MapPoint(latitude, longitude)) < 1.0) return

        useRegionHint = false
        _uiState.update { it.copy(queryCenter = MapPoint(latitude, longitude)) }
        load()
    }

    fun locateUser() {
        if (_uiState.value.isLocating) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLocating = true, errorMessage = null, searchError = null)
            }
            val location = resolveDeviceLocation().getOrElse { failure ->
                _uiState.update {
                    it.copy(isLocating = false, errorMessage = failure.toUserMessage())
                }
                return@launch
            }
            val region = reverseGeocodeLocation(location.latitude, location.longitude)
                .getOrElse { failure ->
                    _uiState.update {
                        it.copy(isLocating = false, errorMessage = failure.toUserMessage())
                    }
                    return@launch
                }
            val savedRegion = SavedRegion(
                municipality = region.municipality,
                state = region.state,
                latitude = location.latitude,
                longitude = location.longitude,
            )
            runCatching { preferencesRepository.setRegion(savedRegion) }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(
                            isLocating = false,
                            errorMessage = "Não foi possível salvar a região no aparelho.",
                        )
                    }
                    return@launch
                }
            val point = MapPoint(location.latitude, location.longitude)
            useRegionHint = true
            _uiState.update {
                it.copy(
                    isLocating = false,
                    needsLocation = false,
                    region = region.copy(resolvedFrom = RegionOrigin.GEOCODER),
                    queryCenter = point,
                    mapFocus = point,
                    searchVisible = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    errorMessage = null,
                )
            }
            load(forceRefresh = true)
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(
                errorMessage = "Permissão de localização não concedida. " +
                    "Busque pela cidade ou CEP para continuar.",
            )
        }
    }

    fun openSearch() {
        _uiState.update {
            it.copy(
                searchVisible = true,
                searchError = null,
                searchQuery = "",
                searchResults = emptyList(),
            )
        }
    }

    fun closeSearch() {
        if (_uiState.value.isSavingSearch) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchVisible = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchError = null,
            )
        }
    }

    fun onSearchQueryChange(rawQuery: String) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = rawQuery,
                searchResults = emptyList(),
                isSearching = false,
                searchError = null,
            )
        }
        if (normalizeQuery(rawQuery).length < 2) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(rawQuery)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(_uiState.value.searchQuery) }
    }

    fun selectSearchSuggestion(suggestion: LocationSuggestion) {
        if (_uiState.value.isSavingSearch) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSavingSearch = true, searchError = null)
            }
            val savedRegion = suggestion.toSavedRegion()
            try {
                preferencesRepository.setRegion(savedRegion)
            } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingSearch = false,
                        searchError = "Não foi possível salvar a região. Tente novamente.",
                    )
                }
                return@launch
            }

            val point = MapPoint(suggestion.latitude, suggestion.longitude)
            val region = Region(
                municipality = suggestion.municipality,
                state = suggestion.state,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude,
                resolvedFrom = RegionOrigin.REQUEST,
            )
            useRegionHint = true
            _uiState.update {
                it.copy(
                    isSavingSearch = false,
                    searchVisible = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    searchError = null,
                    needsLocation = false,
                    region = region,
                    queryCenter = point,
                    mapFocus = point,
                    stations = emptyList(),
                    sortedStations = emptyList(),
                    isLoading = true,
                    errorMessage = null,
                )
            }
            load(forceRefresh = true, initial = true)
        }
    }

    private suspend fun runSearch(rawQuery: String) {
        val normalized = normalizeQuery(rawQuery)
        if (normalized.length < 2) return
        _uiState.update {
            it.copy(isSearching = true, searchError = null, searchResults = emptyList())
        }
        val result = searchLocations(normalized, limit = 5)
        if (normalizeQuery(_uiState.value.searchQuery) != normalized) return
        result
            .onSuccess { suggestions ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = suggestions,
                        searchError = if (suggestions.isEmpty()) {
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
                        searchResults = emptyList(),
                        searchError = failure.toUserMessage(),
                    )
                }
            }
    }

    private fun applyPreferences(preferences: com.fuelcompare.domain.model.UserPreferences) {
        val current = _uiState.value
        val savedRegion = preferences.region
        val incomingRegion = savedRegion?.toRegion(RegionOrigin.REQUEST)

        if (!initialized) {
            initialized = true
            if (incomingRegion != null) {
                val point = MapPoint(incomingRegion.latitude, incomingRegion.longitude)
                useRegionHint = true
                _uiState.update {
                    it.copy(
                        selectedFuel = preferences.fuel,
                        radiusKm = preferences.radiusKm,
                        region = incomingRegion,
                        queryCenter = point,
                        mapFocus = point,
                        isLoading = true,
                        needsLocation = false,
                    ).derived()
                }
                load(initial = true)
            } else {
                _uiState.update {
                    it.copy(
                        selectedFuel = preferences.fuel,
                        radiusKm = preferences.radiusKm,
                        isLoading = false,
                        needsLocation = true,
                    ).derived()
                }
            }
            return
        }

        val fuelChanged = preferences.fuel != current.selectedFuel
        val radiusChanged = preferences.radiusKm != current.radiusKm
        val incomingChanged = incomingRegion != null &&
            (current.region == null ||
                current.region.label != incomingRegion.label ||
                current.region.latitude != incomingRegion.latitude ||
                current.region.longitude != incomingRegion.longitude)

        if (!fuelChanged && !radiusChanged && !incomingChanged) return

        _uiState.update {
            it.copy(
                selectedFuel = preferences.fuel,
                radiusKm = preferences.radiusKm,
                region = incomingRegion ?: it.region,
            ).derived()
        }

        if (incomingChanged) {
            val changedRegion = checkNotNull(incomingRegion)
            val point = MapPoint(changedRegion.latitude, changedRegion.longitude)
            useRegionHint = true
            _uiState.update {
                it.copy(
                    queryCenter = point,
                    mapFocus = point,
                    needsLocation = false,
                    isLoading = it.stations.isEmpty(),
                    errorMessage = null,
                )
            }
            load()
        } else if (radiusChanged) {
            load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean = false, initial: Boolean = false) {
        val state = _uiState.value
        val center = state.queryCenter ?: state.region?.let {
            MapPoint(it.latitude, it.longitude)
        } ?: return
        val regionHint = if (useRegionHint) state.region else null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial || it.stations.isEmpty(),
                    isRefreshing = !initial && it.stations.isNotEmpty(),
                    errorMessage = null,
                )
            }

            val query = NearbyQuery(
                latitude = center.latitude,
                longitude = center.longitude,
                radiusKm = state.radiusKm,
                limit = NEARBY_LIMIT,
                city = regionHint?.municipality,
                state = regionHint?.state,
            )

            getNearbyStations(query, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    val data = loaded.data
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            stations = data.stations,
                            regionalPrices = data.regionalPrices,
                            region = data.region ?: current.region,
                            lastUpdated = data.lastUpdated,
                            sources = data.sources,
                            warnings = data.warnings,
                            fromCache = loaded.fromCache,
                            cachedAt = loaded.cachedAt,
                            errorMessage = null,
                            needsLocation = false,
                        ).derived()
                    }
                }
                .onFailure { failure ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            fromCache = if (current.stations.isEmpty()) false else current.fromCache,
                            errorMessage = failure.toUserMessage(),
                        )
                    }
                }
        }
    }

    private fun HomeUiState.derived(): HomeUiState {
        val ordered = sortStations(stations, sortMode, selectedFuel)
        val cheapest = getCheapestStation(stations, selectedFuel)
        val today = LocalDate.now()
        val topPriced = ordered.asSequence()
            .mapNotNull { station ->
                val price = station.priceFor(selectedFuel)
                    ?.takeIf { PriceRules.isPlausible(it) && PriceRules.isFresh(it, today) }
                    ?: return@mapNotNull null
                station.id to price.amount
            }
            .sortedBy { it.second }
            .map { it.first }
            .distinct()
            .take(2)
            .toList()
        return copy(
            sortedStations = ordered,
            cheapest = cheapest,
            topPricedStationIds = topPriced,
        )
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

    private fun haversineKm(a: MapPoint, b: MapPoint): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadiusKm * atan2(sqrt(h), sqrt(1 - h))
    }

    companion object {
        private const val NEARBY_LIMIT = 50
        private const val SEARCH_DEBOUNCE_MS = 450L
    }
}
