package com.fuelcompare.data.mapper

import com.fuelcompare.core.network.dto.AddressDto
import com.fuelcompare.core.network.dto.ApiMeta
import com.fuelcompare.core.network.dto.ComparisonDto
import com.fuelcompare.core.network.dto.ComparisonRowDto
import com.fuelcompare.core.network.dto.DetailsDataDto
import com.fuelcompare.core.network.dto.FuelPriceDto
import com.fuelcompare.core.network.dto.LocationSuggestionDto
import com.fuelcompare.core.network.dto.NearbyDataDto
import com.fuelcompare.core.network.dto.RegionDto
import com.fuelcompare.core.network.dto.RegionalPriceDto
import com.fuelcompare.core.network.dto.SourceAttributionDto
import com.fuelcompare.core.network.dto.SourceRefDto
import com.fuelcompare.core.network.dto.StationDto
import com.fuelcompare.domain.model.CheaperSide
import com.fuelcompare.domain.model.ComparisonNote
import com.fuelcompare.domain.model.ComparisonRow
import com.fuelcompare.domain.model.ComparisonSide
import com.fuelcompare.domain.model.FuelPrice
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.FuelUnit
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.MatchMethod
import com.fuelcompare.domain.model.NearbyStations
import com.fuelcompare.domain.model.PriceSource
import com.fuelcompare.domain.model.PriceType
import com.fuelcompare.domain.model.Region
import com.fuelcompare.domain.model.RegionOrigin
import com.fuelcompare.domain.model.RegionalFuelPrice
import com.fuelcompare.domain.model.SourceAttribution
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationAddress
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.model.StationRef
import java.time.Instant
import java.time.LocalDate
import kotlin.math.round

object ApiMappers {

    fun mapNearby(dto: NearbyDataDto, meta: ApiMeta = ApiMeta()): NearbyStations =
        NearbyStations(
            stations = dto.stations.mapNotNull(::mapStation),
            regionalPrices = dto.regionalPrices.mapNotNull(::mapRegionalPrice),
            region = dto.region?.let(::mapRegion),
            lastUpdated = parseInstant(dto.lastUpdated),
            sources = dto.sources.mapNotNull(::mapSource),
            warnings = meta.warnings,
        )

    fun mapDetails(dto: DetailsDataDto, meta: ApiMeta = ApiMeta()): StationDetails? {
        val station = mapStation(dto.station) ?: return null
        return StationDetails(
            station = station,
            regionalPrices = dto.regionalPrices.mapNotNull(::mapRegionalPrice),
            lastUpdated = parseInstant(dto.lastUpdated),
            sources = dto.sources.mapNotNull(::mapSource),
            warnings = meta.warnings,
        )
    }

    fun mapComparison(dto: ComparisonDto): StationComparison =
        StationComparison(
            stationA = StationRef(dto.stationA.id, dto.stationA.name),
            stationB = StationRef(dto.stationB.id, dto.stationB.name),
            rows = dto.rows.mapNotNull(::mapComparisonRow),
        )

    fun mapStation(dto: StationDto): Station? {
        val id = dto.id.trim()
        val name = dto.name.trim()
        if (id.isEmpty() || name.isEmpty()) return null
        if (!dto.latitude.isFinite() || dto.latitude !in -90.0..90.0) return null
        if (!dto.longitude.isFinite() || dto.longitude !in -180.0..180.0) return null

        val individualPrices = dto.prices
            .mapNotNull(::mapPrice)
            .distinctBy { it.fuel }

        return Station(
            id = id,
            name = name,
            brand = dto.brand?.trim()?.takeIf { it.isNotEmpty() },
            address = mapAddress(dto.address),
            latitude = dto.latitude,
            longitude = dto.longitude,
            distanceKm = dto.distanceKm?.takeIf { it.isFinite() && it >= 0.0 },
            prices = individualPrices,
        )
    }

    fun mapPrice(dto: FuelPriceDto): FuelPrice? {
        if (dto.priceType != "individual") return null
        val fuel = FuelType.fromCode(dto.fuel) ?: return null
        val unit = FuelUnit.fromApi(dto.unit) ?: return null
        val date = parseDate(dto.referenceDate) ?: return null
        if (!dto.amount.isFinite()) return null

        val price = FuelPrice(
            fuel = fuel,
            amount = dto.amount,
            currency = dto.currency,
            unit = unit,
            priceType = PriceType.INDIVIDUAL,
            source = mapPriceSource(dto.source),
            referenceDate = date,
            collectedAt = parseInstant(dto.collectedAt),
            validationStatus = dto.validationStatus,
            matchMethod = MatchMethod.fromApi(dto.matchMethod),
        )
        return price.takeIf { it.isPlausible }
    }

    fun mapRegionalPrice(dto: RegionalPriceDto): RegionalFuelPrice? {
        if (dto.priceType != "municipal_average") return null
        val fuel = FuelType.fromCode(dto.fuel) ?: return null
        val unit = FuelUnit.fromApi(dto.unit) ?: return null
        val start = parseDate(dto.referenceStart) ?: return null
        val end = parseDate(dto.referenceEnd) ?: return null
        if (dto.municipality.isBlank() || dto.state.isBlank()) return null
        if (!dto.amount.isFinite() || !dto.minAmount.isFinite() || !dto.maxAmount.isFinite()) return null
        if (dto.amount <= 0.0 || dto.amount > 100.0) return null
        if (start.isAfter(end)) return null

        return RegionalFuelPrice(
            fuel = fuel,
            amount = dto.amount,
            minAmount = dto.minAmount,
            maxAmount = dto.maxAmount,
            currency = dto.currency,
            unit = unit,
            municipality = dto.municipality.trim(),
            state = dto.state.trim(),
            referenceStart = start,
            referenceEnd = end,
            surveyedStations = dto.surveyedStations.coerceAtLeast(0),
            source = mapPriceSource(dto.source),
        )
    }

    fun mapRegion(dto: RegionDto): Region? {
        if (dto.municipality.isBlank() || dto.state.isBlank()) return null
        if (!dto.latitude.isFinite() || !dto.longitude.isFinite()) return null
        return Region(
            municipality = dto.municipality.trim(),
            state = dto.state.trim(),
            latitude = dto.latitude,
            longitude = dto.longitude,
            resolvedFrom = RegionOrigin.fromApi(dto.resolvedFrom) ?: RegionOrigin.REQUEST,
        )
    }

    fun mapSearchResults(dtos: List<LocationSuggestionDto>): List<LocationSuggestion> =
        dtos.mapNotNull { dto ->
            if (dto.id.isBlank() || dto.displayName.isBlank()) return@mapNotNull null
            if (dto.municipality.isBlank() || dto.state.isBlank()) return@mapNotNull null
            if (!dto.latitude.isFinite() || !dto.longitude.isFinite()) return@mapNotNull null
            LocationSuggestion(
                id = dto.id,
                displayName = dto.displayName,
                municipality = dto.municipality,
                state = dto.state,
                latitude = dto.latitude,
                longitude = dto.longitude,
                postalCode = dto.postalCode?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    private fun mapComparisonRow(dto: ComparisonRowDto): ComparisonRow? {
        val fuel = FuelType.fromCode(dto.fuel) ?: return null
        val priceA = dto.stationA.price?.let(::mapPrice)
        val priceB = dto.stationB.price?.let(::mapPrice)
        if (priceA == null && priceB == null) return null

        val sideA = ComparisonSide(dto.stationA.stationId, dto.stationA.stationName, priceA)
        val sideB = ComparisonSide(dto.stationB.stationId, dto.stationB.stationName, priceB)
        val comparable = dto.comparable && priceA != null && priceB != null
        val reportedDifference = dto.difference
        val difference = when {
            !comparable -> null
            reportedDifference != null && reportedDifference.isFinite() ->
                roundToMillis(reportedDifference)

            else -> roundToMillis(
                checkNotNull(priceA).amount - checkNotNull(priceB).amount,
            )
        }
        val cheaper = if (!comparable) {
            null
        } else {
            when {
                difference == null || difference == 0.0 -> CheaperSide.TIE
                difference < 0.0 -> CheaperSide.A
                else -> CheaperSide.B
            }
        }
        val note = when {
            !comparable && priceA != null -> ComparisonNote.ONLY_A
            !comparable && priceB != null -> ComparisonNote.ONLY_B
            cheaper == CheaperSide.TIE -> ComparisonNote.TIE
            else -> ComparisonNote.COMPARABLE
        }

        return ComparisonRow(
            fuel = fuel,
            comparable = comparable,
            stationA = sideA,
            stationB = sideB,
            difference = difference,
            cheaper = cheaper,
            note = note,
        )
    }

    private fun mapAddress(dto: AddressDto): StationAddress = StationAddress(
        street = dto.street?.trim()?.takeIf { it.isNotEmpty() },
        number = dto.number?.trim()?.takeIf { it.isNotEmpty() },
        neighborhood = dto.neighborhood?.trim()?.takeIf { it.isNotEmpty() },
        city = dto.city?.trim()?.takeIf { it.isNotEmpty() },
        state = dto.state?.trim()?.takeIf { it.isNotEmpty() },
        postalCode = dto.postalCode?.trim()?.takeIf { it.isNotEmpty() },
        label = dto.label?.trim()?.takeIf { it.isNotEmpty() },
    )

    private fun mapPriceSource(dto: SourceRefDto): PriceSource = PriceSource(
        name = dto.name.ifBlank { "ANP" },
        url = dto.url?.takeIf { isSafeHttpUrl(it) },
        official = dto.official,
    )

    private fun mapSource(dto: SourceAttributionDto): SourceAttribution? {
        if (dto.id.isBlank() || dto.name.isBlank()) return null
        return SourceAttribution(
            id = dto.id,
            name = dto.name,
            url = dto.url.takeIf { isSafeHttpUrl(it) }.orEmpty(),
            description = dto.description,
        )
    }

    private fun isSafeHttpUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")

    private fun parseDate(value: String?): LocalDate? = runCatching {
        value?.let(LocalDate::parse)
    }.getOrNull()

    private fun parseInstant(value: String?): Instant? = runCatching {
        value?.let(Instant::parse)
    }.getOrNull()

    private fun roundToMillis(value: Double): Double = round(value * 1000.0) / 1000.0
}
