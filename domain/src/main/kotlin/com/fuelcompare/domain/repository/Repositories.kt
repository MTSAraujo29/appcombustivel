package com.fuelcompare.domain.repository

import com.fuelcompare.domain.model.CachedStation
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.LoadResult
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.NearbyQuery
import com.fuelcompare.domain.model.NearbyStations
import com.fuelcompare.domain.model.Region
import com.fuelcompare.domain.model.SavedRegion
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.model.ThemeMode
import com.fuelcompare.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    suspend fun getNearby(
        query: NearbyQuery,
        forceRefresh: Boolean = false,
    ): Result<LoadResult<NearbyStations>>

    suspend fun getDetails(stationId: String): Result<LoadResult<StationDetails>>

    suspend fun compare(
        stationAId: String,
        stationBId: String,
    ): Result<LoadResult<StationComparison>>

    /** Usado como fallback local quando a comparação online não está disponível. */
    suspend fun getCachedStation(stationId: String): CachedStation?

    suspend fun clearCache()
}

interface GeocodingRepository {
    suspend fun search(query: String, limit: Int = 5): Result<List<LocationSuggestion>>

    suspend fun reverse(latitude: Double, longitude: Double): Result<Region>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setFuel(fuel: FuelType)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setRadiusKm(radiusKm: Int)

    suspend fun setRegion(region: SavedRegion)

    suspend fun clearRegion()
}
