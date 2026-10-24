package com.fuelcompare.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val data: T,
    val meta: ApiMeta = ApiMeta(),
)

@Serializable
data class ApiMeta(
    val cached: Boolean = false,
    val generatedAt: String? = null,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val requestId: String? = null,
)

@Serializable
data class NearbyDataDto(
    val stations: List<StationDto> = emptyList(),
    val regionalPrices: List<RegionalPriceDto> = emptyList(),
    val region: RegionDto? = null,
    val lastUpdated: String? = null,
    val sources: List<SourceAttributionDto> = emptyList(),
)

@Serializable
data class DetailsDataDto(
    val station: StationDto,
    val regionalPrices: List<RegionalPriceDto> = emptyList(),
    val lastUpdated: String? = null,
    val sources: List<SourceAttributionDto> = emptyList(),
)

@Serializable
data class StationDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    val address: AddressDto = AddressDto(),
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double? = null,
    val prices: List<FuelPriceDto> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
data class AddressDto(
    val street: String? = null,
    val number: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val label: String? = null,
)

@Serializable
data class FuelPriceDto(
    val fuel: String,
    val amount: Double,
    val currency: String = "BRL",
    val unit: String = "liter",
    val priceType: String = "individual",
    val source: SourceRefDto = SourceRefDto(),
    val referenceDate: String,
    val collectedAt: String? = null,
    val validationStatus: String = "official_survey",
    val matchMethod: String? = null,
)

@Serializable
data class SourceRefDto(
    val name: String = "ANP",
    val url: String? = null,
    val official: Boolean = true,
)

@Serializable
data class RegionalPriceDto(
    val fuel: String,
    val amount: Double,
    val minAmount: Double,
    val maxAmount: Double,
    val currency: String = "BRL",
    val unit: String = "liter",
    val priceType: String = "municipal_average",
    val municipality: String,
    val state: String,
    val referenceStart: String,
    val referenceEnd: String,
    val surveyedStations: Int = 0,
    val source: SourceRefDto = SourceRefDto(),
)

@Serializable
data class RegionDto(
    val municipality: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val resolvedFrom: String? = null,
)

@Serializable
data class SourceAttributionDto(
    val id: String,
    val name: String,
    val url: String = "",
    val description: String = "",
)

@Serializable
data class ComparisonDto(
    val stationA: StationRefDto,
    val stationB: StationRefDto,
    val rows: List<ComparisonRowDto> = emptyList(),
)

@Serializable
data class StationRefDto(
    val id: String,
    val name: String,
)

@Serializable
data class ComparisonRowDto(
    val fuel: String,
    val comparable: Boolean = false,
    val stationA: ComparisonSideDto,
    val stationB: ComparisonSideDto,
    val difference: Double? = null,
    val cheaper: String? = null,
    val note: String = "missing_both",
)

@Serializable
data class ComparisonSideDto(
    val stationId: String,
    val stationName: String,
    val price: FuelPriceDto? = null,
)

@Serializable
data class GeocodeDataDto(
    val results: List<LocationSuggestionDto> = emptyList(),
)

@Serializable
data class ReverseGeocodeDataDto(
    val region: RegionDto? = null,
)

@Serializable
data class LocationSuggestionDto(
    val id: String,
    val displayName: String,
    val municipality: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val postalCode: String? = null,
)
