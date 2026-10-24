package com.fuelcompare.core.network

import com.fuelcompare.core.network.dto.ApiEnvelope
import com.fuelcompare.core.network.dto.ApiErrorEnvelope
import com.fuelcompare.core.network.dto.NearbyDataDto
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import org.junit.Test

class ApiDtosTest {

    @Test
    fun `converte resposta de postos proximos`() {
        val json = """
            {
              "data": {
                "stations": [{
                  "id": "way_367319792",
                  "name": "Shell",
                  "brand": "Shell",
                  "address": {
                    "street": "Avenida do Estado",
                    "number": "5300",
                    "city": "São Paulo",
                    "state": "SP",
                    "label": "Avenida do Estado, 5300 · São Paulo"
                  },
                  "latitude": -23.55779,
                  "longitude": -46.6189812,
                  "distanceKm": 1.67,
                  "prices": [{
                    "fuel": "gasoline",
                    "amount": 6.29,
                    "currency": "BRL",
                    "unit": "liter",
                    "priceType": "individual",
                    "source": {
                      "name": "ANP - Levantamento de Preços de Combustíveis",
                      "url": "https://www.gov.br/anp/exemplo.xlsx",
                      "official": true
                    },
                    "referenceDate": "2026-10-12",
                    "collectedAt": "2026-10-12T12:00:00.000Z",
                    "validationStatus": "official_survey",
                    "matchMethod": "address"
                  }]
                }],
                "regionalPrices": [],
                "region": {
                  "municipality": "São Paulo",
                  "state": "SP",
                  "latitude": -23.5505,
                  "longitude": -46.6333,
                  "resolvedFrom": "geocoder"
                },
                "lastUpdated": "2026-10-17T12:00:00.000Z",
                "sources": [{
                  "id": "openstreetmap",
                  "name": "OpenStreetMap",
                  "url": "https://www.openstreetmap.org/copyright",
                  "description": "Dados de postos"
                }]
              },
              "meta": {
                "cached": false,
                "generatedAt": "2026-10-24T12:00:00.000Z",
                "warnings": []
              }
            }
        """.trimIndent()

        val envelope = NetworkJson.instance.decodeFromString<ApiEnvelope<NearbyDataDto>>(json)
        assertThat(envelope.data.stations).hasSize(1)
        val station = envelope.data.stations.single()
        assertThat(station.prices.single().matchMethod).isEqualTo("address")
        assertThat(station.address.state).isEqualTo("SP")
        assertThat(envelope.data.region?.municipality).isEqualTo("São Paulo")
        assertThat(envelope.meta.cached).isFalse()
    }

    @Test
    fun `ignora campos desconhecidos para compatibilidade futura`() {
        val json = """{"data":{"stations":[],"novoCampo":123},"meta":{"warnings":[]},"extra":true}"""
        val envelope = NetworkJson.instance.decodeFromString<ApiEnvelope<NearbyDataDto>>(json)
        assertThat(envelope.data.stations).isEmpty()
    }

    @Test
    fun `converte envelope de erro sem expor detalhes internos`() {
        val json = """
            {"error":{"code":"UPSTREAM_UNAVAILABLE","message":"O serviço de mapas está indisponível no momento. Tente novamente.","requestId":"abc"}}
        """.trimIndent()
        val error = NetworkJson.instance.decodeFromString<ApiErrorEnvelope>(json)
        assertThat(error.error.code).isEqualTo("UPSTREAM_UNAVAILABLE")
        assertThat(error.error.message).contains("indisponível")
    }
}
