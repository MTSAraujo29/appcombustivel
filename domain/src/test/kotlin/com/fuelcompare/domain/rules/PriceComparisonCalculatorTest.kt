package com.fuelcompare.domain.rules

import com.fuelcompare.domain.model.CheaperSide
import com.fuelcompare.domain.model.ComparisonNote
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.PriceType
import com.fuelcompare.domain.price
import com.fuelcompare.domain.station
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class PriceComparisonCalculatorTest {

    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun `diferenca e calculada como A menos B arredondada`() {
        val a = station(id = "node_1", name = "A", prices = listOf(price(amount = 6.299, referenceDate = today)))
        val b = station(id = "node_2", name = "B", prices = listOf(price(amount = 5.879, referenceDate = today)))
        val row = PriceComparisonCalculator.compare(a, b).rows.single()
        assertThat(row.fuel).isEqualTo(FuelType.GASOLINE)
        assertThat(row.comparable).isTrue()
        assertThat(row.difference).isEqualTo(0.42)
        assertThat(row.cheaper).isEqualTo(CheaperSide.B)
        assertThat(row.note).isEqualTo(ComparisonNote.COMPARABLE)
    }

    @Test
    fun `precos iguais resultam em empate`() {
        val a = station(id = "node_1", name = "A", prices = listOf(price(amount = 6.0, referenceDate = today)))
        val b = station(id = "node_2", name = "B", prices = listOf(price(amount = 6.0, referenceDate = today)))
        val row = PriceComparisonCalculator.compare(a, b).rows.single()
        assertThat(row.difference).isEqualTo(0.0)
        assertThat(row.cheaper).isEqualTo(CheaperSide.TIE)
        assertThat(row.note).isEqualTo(ComparisonNote.TIE)
    }

    @Test
    fun `preco presente apenas em A nao gera diferenca`() {
        val a = station(id = "node_1", prices = listOf(price(amount = 6.0, referenceDate = today)))
        val b = station(id = "node_2", prices = emptyList())
        val row = PriceComparisonCalculator.compare(a, b).rows.single()
        assertThat(row.comparable).isFalse()
        assertThat(row.difference).isNull()
        assertThat(row.note).isEqualTo(ComparisonNote.ONLY_A)
    }

    @Test
    fun `preco presente apenas em B nao gera diferenca`() {
        val a = station(id = "node_1", prices = emptyList())
        val b = station(id = "node_2", prices = listOf(price(amount = 6.0, referenceDate = today)))
        val row = PriceComparisonCalculator.compare(a, b).rows.single()
        assertThat(row.comparable).isFalse()
        assertThat(row.note).isEqualTo(ComparisonNote.ONLY_B)
    }

    @Test
    fun `combustiveis ausentes nos dois postos nao aparecem`() {
        val a = station(
            id = "node_1",
            prices = listOf(
                price(fuel = FuelType.GASOLINE, referenceDate = today),
                price(fuel = FuelType.ETHANOL, amount = 4.29, referenceDate = today),
            ),
        )
        val b = station(
            id = "node_2",
            prices = listOf(price(fuel = FuelType.GASOLINE, referenceDate = today)),
        )
        val rows = PriceComparisonCalculator.compare(a, b).rows
        assertThat(rows.map { it.fuel }).containsExactly(FuelType.GASOLINE, FuelType.ETHANOL).inOrder()
        assertThat(rows.last().note).isEqualTo(ComparisonNote.ONLY_A)
    }

    @Test
    fun `preco implausivel e tratado como ausente`() {
        val a = station(id = "node_1", prices = listOf(price(amount = 0.0, referenceDate = today)))
        val b = station(id = "node_2", prices = listOf(price(amount = 6.0, referenceDate = today)))
        val row = PriceComparisonCalculator.compare(a, b).rows.single()
        assertThat(row.comparable).isFalse()
        assertThat(row.note).isEqualTo(ComparisonNote.ONLY_B)
    }

    @Test
    fun `media municipal nao entra na comparacao`() {
        val a = station(
            id = "node_1",
            prices = listOf(price(priceType = PriceType.MUNICIPAL_AVERAGE, referenceDate = today)),
        )
        val b = station(
            id = "node_2",
            prices = listOf(price(priceType = PriceType.MUNICIPAL_AVERAGE, referenceDate = today)),
        )
        assertThat(PriceComparisonCalculator.compare(a, b).rows).isEmpty()
    }
}
