package com.fuelcompare.feature.stationdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.model.toUserMessage
import com.fuelcompare.domain.usecase.GetStationDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StationDetailsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val details: StationDetails? = null,
    val errorMessage: String? = null,
    val fromCache: Boolean = false,
    val cachedAt: Instant? = null,
    val isMissingStationId: Boolean = false,
)

@HiltViewModel
class StationDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getStationDetails: GetStationDetailsUseCase,
) : ViewModel() {

    private val stationId: String? = savedStateHandle[STATION_ID_ARG]
    private val _uiState = MutableStateFlow(
        StationDetailsUiState(
            isLoading = stationId != null,
            isMissingStationId = stationId == null,
        ),
    )
    val uiState: StateFlow<StationDetailsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (stationId != null) {
            load(initial = true)
        }
    }

    fun refresh() {
        if (stationId == null) return
        load(initial = _uiState.value.details == null)
    }

    fun retry() = refresh()

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun load(initial: Boolean) {
        val id = stationId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = initial || current.details == null,
                    isRefreshing = !initial && current.details != null,
                    errorMessage = null,
                )
            }
            getStationDetails(id)
                .onSuccess { result ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            details = result.data,
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
                            isRefreshing = false,
                            errorMessage = failure.toUserMessage(),
                        )
                    }
                }
        }
    }

    companion object {
        const val STATION_ID_ARG = "stationId"
    }
}
