package com.fuelcompare.data.mapper

import com.fuelcompare.core.network.dto.ApiMeta
import com.fuelcompare.core.network.dto.ComparisonDto
import com.fuelcompare.core.network.dto.ComparisonRowDto
import com.fuelcompare.core.network.dto.ComparisonSideDto
import com.fuelcompare.core.network.dto.FuelPriceDto
import com.fuelcompare.core.network.dto.NearbyDataDto
import com.fuelcompare.core.network.dto.RegionDto
import com.fuelcompare.core.network.dto.SourceRefDto
import com.fuelcompare.core.network.dto.StationDto
import com.fuelcompare.core.network.dto.StationRefDto
import com.fuelcompare.domain.model.CheaperSide
import com.fuelcompare.domain.model.ComparisonNote
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.PriceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApiMappersTest {

    private fun priceDto(
        fuel: String = "gasoline",
        amount: Double = 6.29,
        priceType: String = "individual",
        referenceDate: String = "2026-10-12",
        unit: String = "liter",
        url: String? = "https://www.gov.br/anp/exemplo.xlsx",
    ) = FuelPriceDto(
        fuel = fuel,
        amount = amount,
        unit = unit,
        priceType = priceType,
        source = SourceRefDto(name = "ANP", url = url, official = true),
        referenceDate = referenceDate,
        collectedAt = "2026-10-12T12:00:00Z",
        matchMethod = "address",
    )

    private fun stationDto(
        id: String = "way_367319792",
        prices: List<FuelPriceDto> = listOf(priceDto()),
        latitude: Double = -23.55,
        longitude: Double = -46.63,
    ) = StationDto(
        id = id,
        name = "Shell",
        brand = "Shell",
        latitude = latitude,
        longitude = longitude,
        distanceKm = 1.67,
        prices = prices,
    )

    @Test
    fun `mapeia posto e preco individual`() {
        val nearby = ApiMappers.mapNearby(
            NearbyDataDto(
                stations = listOf(stationDto()),
                region = RegionDto("São Paulo", "SP", -23.55, -46.63, "geocoder"),
                lastUpdated = "2026-10-17T12:00:00.000Z",
            ),
            ApiMeta(warnings = listOf("Aviso")),
        )
        val station = nearby.stations.single()
        assertThat(station.id).isEqualTo("way_367319792")
        assertThat(station.priceFor(FuelType.GASOLINE)?.amount).isEqualTo(6.29)
        assertThat(station.priceFor(FuelType.GASOLINE)?.matchMethod)
            .isEqualTo(com.fuelcompare.domain.model.MatchMethod.ADDRESS)
        assertThat(nearby.region?.municipality).isEqualTo("São Paulo")
        assertThat(nearby.warnings).containsExactly("Aviso")
        assertThat(nearby.lastUpdated).isNotNull()
    }

    @Test
    fun `nunca trata media municipal como preco do posto`() {
        val station = ApiMappers.mapStation(
            stationDto(
                prices = listOf(
                    priceDto(priceType = "municipal_average", amount = 6.10),
                    priceDto(priceType = "individual", amount = 6.29),
                ),
            ),
        )!!
        assertThat(station.prices).hasSize(1)
        assertThat(station.prices.single().priceType).isEqualTo(PriceType.INDIVIDUAL)
    }

    @Test
    fun `descarta preco com combustivel ou data desconhecidos`() {
        val station = ApiMappers.mapStation(
            stationDto(
                prices = listOf(
                    priceDto(fuel = "hydrogen"),
                    priceDto(referenceDate = "data-invalida"),
                    priceDto(amount = 0.0),
                    priceDto(unit = "m3"),
                    priceDto(amount = 5.99),
                ),
            ),
        )!!
        assertThat(station.prices).hasSize(1)
        assertThat(station.prices.single().amount).isEqualTo(5.99)
    }

    @Test
    fun `descarta posto com coordenadas invalidas`() {
        assertThat(ApiMappers.mapStation(stationDto(latitude = 91.0))).isNull()
        assertThat(ApiMappers.mapStation(stationDto(longitude = 200.0))).isNull()
        assertThat(ApiMappers.mapStation(stationDto(id = " "))).isNull()
    }

    @Test
    fun `nao mantem url de fonte que nao seja http`() {
        val station = ApiMappers.mapStation(
            stationDto(prices = listOf(priceDto(url = "javascript:alert(1)"))),
        )!!
        assertThat(station.prices.single().source.url).isNull()
    }

    @Test
    fun `mapeia comparacao e deriva diferenca de forma consistente`() {
        val comparison = ApiMappers.mapComparison(
            ComparisonDto(
                stationA = StationRefDto("node_1", "A"),
                stationB = StationRefDto("node_2", "B"),
                rows = listOf(
                    ComparisonRowDto(
                        fuel = "gasoline",
                        comparable = true,
                        stationA = ComparisonSideDto(
                            "node_1",
                            "A",
                            priceDto(amount = 6.30),
                        ),
                        stationB = ComparisonSideDto(
                            "node_2",
                            "B",
                            priceDto(amount = 6.00),
                        ),
                        note = "comparable",
                    ),
                    ComparisonRowDto(
                        fuel = "ethanol",
                        comparable = false,
                        stationA = ComparisonSideDto(
                            "node_1",
                            "A",
                            priceDto(fuel = "ethanol", amount = 4.29),
                        ),
                        stationB = ComparisonSideDto("node_2", "B", null),
                        note = "only_a",
                    ),
                ),
            ),
        )
        assertThat(comparison.rows).hasSize(2)
        val gasoline = comparison.rows.first()
        // A − B = +0,30: o posto B é o mais barato.
        assertThat(gasoline.difference).isEqualTo(0.3)
        assertThat(gasoline.cheaper).isEqualTo(CheaperSide.B)
        assertThat(gasoline.note).isEqualTo(ComparisonNote.COMPARABLE)
        val ethanol = comparison.rows.last()
        assertThat(ethanol.comparable).isFalse()
        assertThat(ethanol.difference).isNull()
        assertThat(ethanol.note).isEqualTo(ComparisonNote.ONLY_A)
    }

    @Test
    fun `mapeia resultados de geocodificacao e ignora invalidos`() {
        val results = ApiMappers.mapSearchResults(
            listOf(
                com.fuelcompare.core.network.dto.LocationSuggestionDto(
                    "relation_1",
                    "Osasco, São Paulo, Brasil",
                    "Osasco",
                    "SP",
                    -23.53,
                    -46.79,
                    "06000-000",
                ),
                com.fuelcompare.core.network.dto.LocationSuggestionDto(
                    "relation_2",
                    "Inválido",
                    "",
                    "SP",
                    -23.53,
                    -46.79,
                ),
            ),
        )
        assertThat(results).hasSize(1)
        assertThat(results.single().municipality).isEqualTo("Osasco")
    }
}
