package com.fuelcompare.domain.model

import java.time.Instant
import java.time.LocalDate

enum class FuelUnit(
    val apiValue: String,
    val symbol: String,
    val spokenName: String,
) {
    LITER("liter", "L", "litro"),
    CUBIC_METER("m3", "m³", "metro cúbico"),
    ;

    companion object {
        fun fromApi(value: String?): FuelUnit? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class FuelType(
    val code: String,
    val label: String,
    val shortLabel: String,
    val unit: FuelUnit,
) {
    GASOLINE("gasoline", "Gasolina comum", "Gasolina", FuelUnit.LITER),
    GASOLINE_ADDITIVATED("gasoline_additivated", "Gasolina aditivada", "Aditivada", FuelUnit.LITER),
    ETHANOL("ethanol", "Etanol", "Etanol", FuelUnit.LITER),
    DIESEL_S10("diesel_s10", "Diesel S10", "Diesel S10", FuelUnit.LITER),
    DIESEL("diesel", "Diesel comum", "Diesel", FuelUnit.LITER),
    GNV("gnv", "GNV", "GNV", FuelUnit.CUBIC_METER),
    ;

    companion object {
        fun fromCode(code: String): FuelType? = entries.firstOrNull { it.code == code }
    }
}

enum class PriceType { INDIVIDUAL, MUNICIPAL_AVERAGE }

enum class MatchMethod {
    CNPJ,
    ADDRESS,
    NAME,
    ;

    companion object {
        fun fromApi(value: String?): MatchMethod? = when (value) {
            "cnpj" -> CNPJ
            "address" -> ADDRESS
            "name" -> NAME
            else -> null
        }
    }
}

enum class RegionOrigin {
    REQUEST,
    GEOCODER,
    STATION,
    ;

    companion object {
        fun fromApi(value: String?): RegionOrigin? = when (value) {
            "request" -> REQUEST
            "geocoder" -> GEOCODER
            "station" -> STATION
            else -> null
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortMode { PRICE, DISTANCE, NAME }

data class PriceSource(
    val name: String,
    val url: String?,
    val official: Boolean,
)

data class FuelPrice(
    val fuel: FuelType,
    val amount: Double,
    val currency: String,
    val unit: FuelUnit,
    val priceType: PriceType,
    val source: PriceSource,
    val referenceDate: LocalDate,
    val collectedAt: Instant? = null,
    val validationStatus: String = "official_survey",
    val matchMethod: MatchMethod? = null,
) {
    val isPlausible: Boolean
        get() = currency == "BRL" &&
            amount.isFinite() &&
            amount > 0.0 &&
            amount <= 100.0 &&
            unit == fuel.unit
}

data class StationAddress(
    val street: String? = null,
    val number: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val label: String? = null,
) {
    val shortLabel: String
        get() {
            val streetPart = listOfNotNull(street, number).joinToString(", ").takeIf { it.isNotBlank() }
            val cityPart = listOfNotNull(city, state).joinToString("/").takeIf { it.isNotBlank() }
            return listOfNotNull(streetPart, cityPart).joinToString(" · ").ifBlank {
                label ?: "Endereço não informado"
            }
        }
}

data class Station(
    val id: String,
    val name: String,
    val brand: String? = null,
    val address: StationAddress = StationAddress(),
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double? = null,
    val prices: List<FuelPrice> = emptyList(),
) {
    /** Preço individual associado ao posto; médias municipais nunca entram aqui. */
    fun priceFor(fuel: FuelType): FuelPrice? =
        prices.firstOrNull { it.fuel == fuel && it.priceType == PriceType.INDIVIDUAL }
}

data class RegionalFuelPrice(
    val fuel: FuelType,
    val amount: Double,
    val minAmount: Double,
    val maxAmount: Double,
    val currency: String,
    val unit: FuelUnit,
    val municipality: String,
    val state: String,
    val referenceStart: LocalDate,
    val referenceEnd: LocalDate,
    val surveyedStations: Int,
    val source: PriceSource,
) {
    val isPlausible: Boolean
        get() = currency == "BRL" && amount.isFinite() && amount > 0.0 && amount <= 100.0
}

data class Region(
    val municipality: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val resolvedFrom: RegionOrigin,
) {
    val label: String get() = "$municipality/$state"
}

data class SavedRegion(
    val municipality: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
) {
    val label: String get() = "$municipality/$state"

    fun toRegion(origin: RegionOrigin = RegionOrigin.REQUEST): Region = Region(
        municipality = municipality,
        state = state,
        latitude = latitude,
        longitude = longitude,
        resolvedFrom = origin,
    )
}

data class SourceAttribution(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
)

data class NearbyStations(
    val stations: List<Station>,
    val regionalPrices: List<RegionalFuelPrice>,
    val region: Region?,
    val lastUpdated: Instant?,
    val sources: List<SourceAttribution>,
    val warnings: List<String> = emptyList(),
)

data class StationDetails(
    val station: Station,
    val regionalPrices: List<RegionalFuelPrice>,
    val lastUpdated: Instant?,
    val sources: List<SourceAttribution>,
    val warnings: List<String> = emptyList(),
)

/** Posto recuperado do cache local, com a data real do snapshot salvo. */
data class CachedStation(
    val station: Station,
    val cachedAt: Instant,
)

data class LocationSuggestion(
    val id: String,
    val displayName: String,
    val municipality: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val postalCode: String? = null,
) {
    fun toSavedRegion(): SavedRegion = SavedRegion(municipality, state, latitude, longitude)
}

data class NearbyQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Int = 5,
    val limit: Int = 50,
    val city: String? = null,
    val state: String? = null,
)

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
)

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val fuel: FuelType = FuelType.GASOLINE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val radiusKm: Int = 5,
    val region: SavedRegion? = null,
)

data class LoadResult<T>(
    val data: T,
    val fromCache: Boolean = false,
    /** Momento em que os dados foram salvos no aparelho, quando fromCache = true. */
    val cachedAt: Instant? = null,
)

data class StationRef(
    val id: String,
    val name: String,
)

enum class ComparisonNote { COMPARABLE, TIE, ONLY_A, ONLY_B, MISSING_BOTH }
enum class CheaperSide { A, B, TIE }

data class ComparisonSide(
    val stationId: String,
    val stationName: String,
    val price: FuelPrice?,
)

data class ComparisonRow(
    val fuel: FuelType,
    val comparable: Boolean,
    val stationA: ComparisonSide,
    val stationB: ComparisonSide,
    val difference: Double? = null,
    val cheaper: CheaperSide? = null,
    val note: ComparisonNote,
)

data class StationComparison(
    val stationA: StationRef,
    val stationB: StationRef,
    val rows: List<ComparisonRow>,
)

sealed class AppFailure(
    val publicMessage: String,
    cause: Throwable? = null,
) : Exception(publicMessage, cause) {

    class Network(cause: Throwable? = null) : AppFailure(
        "Sem conexão com o serviço. Verifique sua internet e tente novamente.",
        cause,
    )

    class Timeout(cause: Throwable? = null) : AppFailure(
        "O serviço demorou para responder. Tente novamente em instantes.",
        cause,
    )

    class ServiceUnavailable(cause: Throwable? = null) : AppFailure(
        "Os dados de postos estão indisponíveis no momento. Tente novamente.",
        cause,
    )

    class RateLimited : AppFailure(
        "Muitas consultas em pouco tempo. Aguarde alguns instantes e tente novamente.",
    )

    class NotFound : AppFailure("Posto não encontrado.")

    class InvalidRequest(message: String) : AppFailure(message)

    class LocationUnavailable : AppFailure(
        "Não foi possível obter sua localização. Verifique a permissão e se o GPS está ativo.",
    )

    class Unknown(cause: Throwable? = null) : AppFailure(
        "Não foi possível concluir a consulta. Tente novamente.",
        cause,
    )
}

/** Mensagem segura para exibição; nunca expõe detalhes internos da exceção. */
fun Throwable.toUserMessage(): String =
    (this as? AppFailure)?.publicMessage
        ?: AppFailure.Unknown(this).publicMessage
