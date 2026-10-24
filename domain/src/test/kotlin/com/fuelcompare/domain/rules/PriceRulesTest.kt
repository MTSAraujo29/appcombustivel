package com.fuelcompare.domain.rules

import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.PriceType
import com.fuelcompare.domain.price
import com.fuelcompare.domain.station
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class PriceRulesTest {

    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun `preco individual plausivel e recente e elegivel`() {
        val candidate = price(referenceDate = today.minusDays(10), amount = 6.19)
        assertThat(PriceRules.isEligibleForCheapest(station(prices = listOf(candidate)), FuelType.GASOLINE, today))
            .isEqualTo(candidate)
    }

    @Test
    fun `media municipal nunca e promovida a preco de posto`() {
        val municipal = price(
            priceType = PriceType.MUNICIPAL_AVERAGE,
            referenceDate = today.minusDays(2),
        )
        val result = CheapestStationFinder.find(
            listOf(station(prices = listOf(municipal))),
            FuelType.GASOLINE,
            today,
        )
        assertThat(result).isNull()
    }

    @Test
    fun `preco com mais de 31 dias nao e recente`() {
        val old = price(referenceDate = today.minusDays(32))
        assertThat(PriceRules.isFresh(old, today)).isFalse()
        assertThat(
            PriceRules.isEligibleForCheapest(station(prices = listOf(old)), FuelType.GASOLINE, today),
        ).isNull()
    }

    @Test
    fun `limite de 31 dias ainda e recente`() {
        val boundary = price(referenceDate = today.minusDays(31))
        assertThat(PriceRules.isFresh(boundary, today)).isTrue()
    }

    @Test
    fun `data futura distante nao e recente`() {
        val future = price(referenceDate = today.plusDays(2))
        assertThat(PriceRules.isFresh(future, today)).isFalse()
    }

    @Test
    fun `preco zero negativo excessivo ou moeda estrangeira e invalido`() {
        listOf(
            price(amount = 0.0),
            price(amount = -1.0),
            price(amount = 100.01),
            price(currency = "USD"),
        ).forEach { candidate ->
            assertThat(PriceRules.isPlausible(candidate)).isFalse()
        }
    }

    @Test
    fun `unidade incompativel com o combustivel e invalida`() {
        val invalid = price(
            fuel = FuelType.GASOLINE,
            unit = com.fuelcompare.domain.model.FuelUnit.CUBIC_METER,
        )
        assertThat(PriceRules.isPlausible(invalid)).isFalse()
    }

    @Test
    fun `nao escolhe preco invalido mesmo se for o menor`() {
        val invalid = price(amount = 0.99, currency = "USD", referenceDate = today)
        val valid = price(amount = 6.20, referenceDate = today)
        val result = CheapestStationFinder.find(
            listOf(
                station(id = "node_1", prices = listOf(invalid), distanceKm = 0.5),
                station(id = "node_2", prices = listOf(valid), distanceKm = 4.0),
            ),
            FuelType.GASOLINE,
            today,
        )
        assertThat(result?.station?.id).isEqualTo("node_2")
    }

    @Test
    fun `cheapest usa preco e depois distancia e nome como desempate`() {
        val candidates = listOf(
            station(
                id = "node_b",
                name = "Zeta",
                distanceKm = 1.2,
                prices = listOf(price(amount = 5.99, referenceDate = today)),
            ),
            station(
                id = "node_a",
                name = "Alfa",
                distanceKm = 2.4,
                prices = listOf(price(amount = 5.99, referenceDate = today)),
            ),
            station(
                id = "node_c",
                name = "Beta",
                distanceKm = 1.2,
                prices = listOf(price(amount = 5.99, referenceDate = today)),
            ),
            station(
                id = "node_d",
                distanceKm = 0.1,
                prices = listOf(price(amount = 6.40, referenceDate = today)),
            ),
        )
        val result = CheapestStationFinder.find(candidates, FuelType.GASOLINE, today)
        assertThat(result?.station?.id).isEqualTo("node_c")
    }

    @Test
    fun `sem precos individuais validos nao ha menor preco`() {
        val result = CheapestStationFinder.find(
            listOf(
                station(id = "node_1", prices = emptyList()),
                station(id = "node_2", prices = listOf(price(referenceDate = today.minusDays(90)))),
            ),
            FuelType.GASOLINE,
            today,
        )
        assertThat(result).isNull()
    }
}
