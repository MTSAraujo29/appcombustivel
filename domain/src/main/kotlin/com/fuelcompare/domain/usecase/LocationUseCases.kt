package com.fuelcompare.domain.usecase

import com.fuelcompare.domain.model.AppFailure
import com.fuelcompare.domain.model.DeviceLocation
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.Region
import com.fuelcompare.domain.repository.GeocodingRepository
import com.fuelcompare.domain.repository.LocationProvider

class SearchLocationsUseCase(
    private val repository: GeocodingRepository,
) {
    suspend operator fun invoke(query: String, limit: Int = 5): Result<List<LocationSuggestion>> {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return Result.failure(
                AppFailure.InvalidRequest("Digite ao menos 2 caracteres para buscar."),
            )
        }
        if (trimmed.length > 160) {
            return Result.failure(
                AppFailure.InvalidRequest("A busca deve ter no máximo 160 caracteres."),
            )
        }
        if (limit !in 1..8) {
            return Result.failure(AppFailure.InvalidRequest("Limite de resultados inválido."))
        }
        if (trimmed.any { it.isISOControl() || it == '<' || it == '>' }) {
            return Result.failure(AppFailure.InvalidRequest("Busca inválida."))
        }
        return repository.search(trimmed, limit)
    }
}

class ReverseGeocodeLocationUseCase(
    private val repository: GeocodingRepository,
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): Result<Region> {
        QueryValidation.validateCoordinates(latitude, longitude)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        return repository.reverse(latitude, longitude)
    }
}

class ResolveDeviceLocationUseCase(
    private val locationProvider: LocationProvider,
) {
    suspend operator fun invoke(): Result<DeviceLocation> {
        val enabled = runCatching { locationProvider.isLocationEnabled() }.getOrDefault(false)
        if (!enabled) return Result.failure(AppFailure.LocationUnavailable())
        val location = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            ?: return Result.failure(AppFailure.LocationUnavailable())
        QueryValidation.validateCoordinates(location.latitude, location.longitude)?.let {
            return Result.failure(AppFailure.LocationUnavailable())
        }
        return Result.success(location)
    }
}
