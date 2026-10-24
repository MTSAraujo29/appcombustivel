package com.fuelcompare.domain.rules

import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.SortMode
import com.fuelcompare.domain.price
import com.fuelcompare.domain.station
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class StationSorterTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val alpha = station(
        id = "node_1",
        name = "Alfa",
        distanceKm = 3.0,
        prices = listOf(price(amount = 6.20, referenceDate = today)),
    )
    private val beta = station(
        id = "node_2",
        name = "Beta",
        distanceKm = 1.0,
        prices = listOf(price(amount = 5.90, referenceDate = today)),
    )
    private val noPrice = station(
        id = "node_3",
        name = "Gama",
        distanceKm = 0.5,
        prices = emptyList(),
    )
    private val nullDistance = station(
        id = "node_4",
        name = "Delta",
        distanceKm = null,
        prices = listOf(price(amount = 5.90, referenceDate = today)),
    )

    @Test
    fun `ordenacao por preco coloca sem preco no fim`() {
        val sorted = StationSorter.sort(listOf(alpha, noPrice, beta), SortMode.PRICE, FuelType.GASOLINE)
        assertThat(sorted.map { it.id }).containsExactly("node_2", "node_1", "node_3").inOrder()
    }

    @Test
    fun `ordenacao por distancia coloca distancia desconhecida no fim`() {
        val sorted = StationSorter.sort(
            listOf(alpha, nullDistance, beta, noPrice),
            SortMode.DISTANCE,
            FuelType.GASOLINE,
        )
        assertThat(sorted.map { it.id })
            .containsExactly("node_3", "node_2", "node_1", "node_4")
            .inOrder()
    }

    @Test
    fun `ordenacao por nome usa caixa baixa e ignora acentos de ordem alfabetica`() {
        val zulu = station(id = "node_5", name = "zulu", distanceKm = 4.0)
        val alex = station(id = "node_6", name = "Alex", distanceKm = 2.0)
        val sorted = StationSorter.sort(listOf(zulu, alex, beta), SortMode.NAME, FuelType.GASOLINE)
        assertThat(sorted.map { it.name }).containsExactly("Alex", "Beta", "zulu").inOrder()
    }

    @Test
    fun `empate de preco usa distancia`() {
        val sorted = StationSorter.sort(listOf(alpha, nullDistance, beta), SortMode.PRICE, FuelType.GASOLINE)
        assertThat(sorted.first().id).isEqualTo("node_2")
    }
}
