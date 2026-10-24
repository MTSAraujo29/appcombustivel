package com.fuelcompare.core.network

import com.fuelcompare.core.network.dto.ApiEnvelope
import com.fuelcompare.core.network.dto.ComparisonDto
import com.fuelcompare.core.network.dto.DetailsDataDto
import com.fuelcompare.core.network.dto.GeocodeDataDto
import com.fuelcompare.core.network.dto.NearbyDataDto
import com.fuelcompare.core.network.dto.ReverseGeocodeDataDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FuelCompareApi {

    @GET("api/v1/stations/nearby")
    suspend fun getNearby(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("radiusKm") radiusKm: Int,
        @Query("limit") limit: Int,
        @Query("city") city: String? = null,
        @Query("state") state: String? = null,
    ): ApiEnvelope<NearbyDataDto>

    @GET("api/v1/stations/{id}")
    suspend fun getStationDetails(
        @Path("id") stationId: String,
    ): ApiEnvelope<DetailsDataDto>

    @GET("api/v1/stations/{id}/prices")
    suspend fun getStationPrices(
        @Path("id") stationId: String,
    ): ApiEnvelope<DetailsDataDto>

    @GET("api/v1/prices/compare")
    suspend fun compare(
        @Query("stationA") stationAId: String,
        @Query("stationB") stationBId: String,
    ): ApiEnvelope<ComparisonDto>

    @GET("api/v1/geocode/search")
    suspend fun searchLocations(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
    ): ApiEnvelope<GeocodeDataDto>

    @GET("api/v1/geocode/reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): ApiEnvelope<ReverseGeocodeDataDto>
}
