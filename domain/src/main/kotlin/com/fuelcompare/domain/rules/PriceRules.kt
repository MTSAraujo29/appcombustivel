package com.fuelcompare.domain.rules

import com.fuelcompare.domain.model.CheaperSide
import com.fuelcompare.domain.model.ComparisonNote
import com.fuelcompare.domain.model.ComparisonRow
import com.fuelcompare.domain.model.ComparisonSide
import com.fuelcompare.domain.model.FuelPrice
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.PriceType
import com.fuelcompare.domain.model.SortMode
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.StationRef
import java.time.LocalDate

/**
 * Regras de integridade dos preços. Elas são aplicadas antes de qualquer
 * destaque de "menor preço" ou comparação.
 */
object PriceRules {
    const val MAX_PRICE_AGE_DAYS = 31L
    const val MAX_REASONABLE_PRICE = 100.0

    fun isPlausible(price: FuelPrice): Boolean =
        price.priceType == PriceType.INDIVIDUAL && price.isPlausible

    fun isFresh(price: FuelPrice, today: LocalDate = LocalDate.now()): Boolean {
        val oldest = today.minusDays(MAX_PRICE_AGE_DAYS)
        val newest = today.plusDays(1)
        return !price.referenceDate.isBefore(oldest) && !price.referenceDate.isAfter(newest)
    }

    fun isEligibleForCheapest(
        station: Station,
        fuel: FuelType,
        today: LocalDate = LocalDate.now(),
    ): FuelPrice? = station.priceFor(fuel)?.takeIf { isPlausible(it) && isFresh(it, today) }
}

data class CheapestStation(
    val station: Station,
    val price: FuelPrice,
)

object CheapestStationFinder {
    /**
     * Só considera preços individuais, plausíveis e recentes. Sem preço válido,
     * retorna null — médias municipais jamais são promovidas a preço de um posto.
     */
    fun find(
        stations: List<Station>,
        fuel: FuelType,
        today: LocalDate = LocalDate.now(),
    ): CheapestStation? = stations
        .asSequence()
        .mapNotNull { station ->
            PriceRules.isEligibleForCheapest(station, fuel, today)?.let { CheapestStation(station, it) }
        }
        .minWithOrNull(
            compareBy<CheapestStation> { it.price.amount }
                .thenBy { it.station.distanceKm ?: Double.MAX_VALUE }
                .thenBy { it.station.name.lowercase() }
                .thenBy { it.station.id },
        )
}

object StationSorter {
    fun sort(stations: List<Station>, mode: SortMode, fuel: FuelType): List<Station> {
        val comparator: Comparator<Station> = when (mode) {
            SortMode.PRICE -> compareBy<Station> { station ->
                station.priceFor(fuel)?.takeIf { it.isPlausible }?.amount ?: Double.MAX_VALUE
            }
                .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }

            SortMode.DISTANCE -> compareBy<Station> { it.distanceKm ?: Double.MAX_VALUE }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }

            SortMode.NAME -> compareBy<Station> { it.name.lowercase() }
                .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                .thenBy { it.id }
        }
        return stations.sortedWith(comparator)
    }
}

object PriceComparisonCalculator {
    private fun round(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0

    /**
     * Compara apenas combustíveis presentes nos dois postos para calcular
     * diferença. Quando só um lado tem preço, não existe diferença confiável.
     */
    fun compare(
        stationA: Station,
        stationB: Station,
    ): StationComparison {
        val rows = mutableListOf<ComparisonRow>()
        for (fuel in FuelType.entries) {
            val priceA = stationA.priceFor(fuel)?.takeIf { it.isPlausible }
            val priceB = stationB.priceFor(fuel)?.takeIf { it.isPlausible }
            if (priceA == null && priceB == null) continue

            val sideA = ComparisonSide(stationA.id, stationA.name, priceA)
            val sideB = ComparisonSide(stationB.id, stationB.name, priceB)

            when {
                priceA != null && priceB != null -> {
                    val difference = round(priceA.amount - priceB.amount)
                    val cheaper = when {
                        difference == 0.0 -> CheaperSide.TIE
                        difference < 0.0 -> CheaperSide.A
                        else -> CheaperSide.B
                    }
                    rows += ComparisonRow(
                        fuel = fuel,
                        comparable = true,
                        stationA = sideA,
                        stationB = sideB,
                        difference = difference,
                        cheaper = cheaper,
                        note = if (difference == 0.0) ComparisonNote.TIE else ComparisonNote.COMPARABLE,
                    )
                }

                priceA != null -> rows += ComparisonRow(
                    fuel = fuel,
                    comparable = false,
                    stationA = sideA,
                    stationB = sideB,
                    note = ComparisonNote.ONLY_A,
                )

                else -> rows += ComparisonRow(
                    fuel = fuel,
                    comparable = false,
                    stationA = sideA,
                    stationB = sideB,
                    note = ComparisonNote.ONLY_B,
                )
            }
        }

        return StationComparison(
            stationA = StationRef(stationA.id, stationA.name),
            stationB = StationRef(stationB.id, stationB.name),
            rows = rows,
        )
    }
}
