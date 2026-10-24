package com.fuelcompare.domain

import com.fuelcompare.domain.model.FuelPrice
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.PriceSource
import com.fuelcompare.domain.model.PriceType
import com.fuelcompare.domain.model.Station
import java.time.Instant
import java.time.LocalDate

internal val anpSource = PriceSource(
    name = "ANP - Levantamento de Preços de Combustíveis",
    url = "https://www.gov.br/anp/exemplo.xlsx",
    official = true,
)

internal fun price(
    fuel: FuelType = FuelType.GASOLINE,
    amount: Double = 6.29,
    currency: String = "BRL",
    priceType: PriceType = PriceType.INDIVIDUAL,
    referenceDate: LocalDate = LocalDate.of(2026, 10, 12),
    unit: com.fuelcompare.domain.model.FuelUnit = fuel.unit,
): FuelPrice = FuelPrice(
    fuel = fuel,
    amount = amount,
    currency = currency,
    unit = unit,
    priceType = priceType,
    source = anpSource,
    referenceDate = referenceDate,
    collectedAt = Instant.parse("2026-10-12T12:00:00Z"),
    validationStatus = "official_survey",
)

internal fun station(
    id: String = "node_1",
    name: String = "Posto Central",
    prices: List<FuelPrice> = listOf(price()),
    distanceKm: Double? = 1.0,
    latitude: Double = -23.55,
    longitude: Double = -46.63,
): Station = Station(
    id = id,
    name = name,
    brand = null,
    latitude = latitude,
    longitude = longitude,
    distanceKm = distanceKm,
    prices = prices,
)
