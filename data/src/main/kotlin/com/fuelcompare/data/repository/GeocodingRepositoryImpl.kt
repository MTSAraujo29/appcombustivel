package com.fuelcompare.data.repository

import com.fuelcompare.core.common.DispatchersProvider
import com.fuelcompare.core.network.FuelCompareApi
import com.fuelcompare.data.mapper.ApiMappers
import com.fuelcompare.data.network.NetworkFailureMapper
import com.fuelcompare.domain.model.AppFailure
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.Region
import com.fuelcompare.domain.repository.GeocodingRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

class GeocodingRepositoryImpl(
    private val api: FuelCompareApi,
    private val dispatchers: DispatchersProvider,
) : GeocodingRepository {

    override suspend fun search(query: String, limit: Int): Result<List<LocationSuggestion>> =
        withContext(dispatchers.io) {
            try {
                val response = api.searchLocations(query, limit)
                Result.success(ApiMappers.mapSearchResults(response.data.results))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(NetworkFailureMapper.map(error))
            }
        }

    override suspend fun reverse(latitude: Double, longitude: Double): Result<Region> =
        withContext(dispatchers.io) {
            try {
                val response = api.reverseGeocode(latitude, longitude)
                val region = response.data.region?.let(ApiMappers::mapRegion)
                if (region == null) {
                    Result.failure(
                        AppFailure.InvalidRequest(
                            "Não foi possível identificar o município da sua localização. " +
                                "Busque pela cidade ou CEP.",
                        ),
                    )
                } else {
                    Result.success(region)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(NetworkFailureMapper.map(error))
            }
        }
}
