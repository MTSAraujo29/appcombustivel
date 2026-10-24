package com.fuelcompare.domain.usecase

import com.fuelcompare.domain.model.AppFailure
import com.fuelcompare.domain.model.CachedStation
import com.fuelcompare.domain.model.DeviceLocation
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.LoadResult
import com.fuelcompare.domain.model.LocationSuggestion
import com.fuelcompare.domain.model.NearbyQuery
import com.fuelcompare.domain.model.NearbyStations
import com.fuelcompare.domain.model.Region
import com.fuelcompare.domain.model.RegionOrigin
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.price
import com.fuelcompare.domain.repository.GeocodingRepository
import com.fuelcompare.domain.repository.LocationProvider
import com.fuelcompare.domain.repository.StationRepository
import com.fuelcompare.domain.station
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeStationRepository : StationRepository {
    var nearbyResult: Result<LoadResult<NearbyStations>> = Result.success(
        LoadResult(
            NearbyStations(
                stations = emptyList(),
                regionalPrices = emptyList(),
                region = null,
                lastUpdated = null,
                sources = emptyList(),
            ),
        ),
    )
    var detailsResult: Result<LoadResult<StationDetails>> = Result.failure(AppFailure.NotFound())
    var compareResult: Result<LoadResult<StationComparison>> =
        Result.failure(AppFailure.ServiceUnavailable())
    var cachedStations: Map<String, CachedStation> = emptyMap()
    var nearbyCalls = 0
    var lastQuery: NearbyQuery? = null

    override suspend fun getNearby(
        query: NearbyQuery,
        forceRefresh: Boolean,
    ): Result<LoadResult<NearbyStations>> {
        nearbyCalls++
        lastQuery = query
        return nearbyResult
    }

    override suspend fun getDetails(stationId: String): Result<LoadResult<StationDetails>> = detailsResult

    override suspend fun compare(
        stationAId: String,
        stationBId: String,
    ): Result<LoadResult<StationComparison>> = compareResult

    override suspend fun getCachedStation(stationId: String): CachedStation? = cachedStations[stationId]

    override suspend fun clearCache() = Unit
}

private class FakeGeocodingRepository : GeocodingRepository {
    var results: Result<List<LocationSuggestion>> = Result.success(emptyList())
    var reverseResult: Result<Region> = Result.success(
        Region("São Paulo", "SP", -23.55, -46.63, RegionOrigin.GEOCODER),
    )
    var lastQuery: String? = null
    var lastLimit: Int? = null

    override suspend fun search(query: String, limit: Int): Result<List<LocationSuggestion>> {
        lastQuery = query
        lastLimit = limit
        return results
    }

    override suspend fun reverse(latitude: Double, longitude: Double): Result<Region> = reverseResult
}

private class FakeLocationProvider : LocationProvider {
    var enabled = true
    var location: DeviceLocation? = DeviceLocation(-23.55, -46.63)
    var currentCalls = 0

    override suspend fun getCurrentLocation(): DeviceLocation? {
        currentCalls++
        return location
    }

    override fun isLocationEnabled(): Boolean = enabled
}

class UseCasesTest {

    private val repository = FakeStationRepository()
    private val geocoding = FakeGeocodingRepository()
    private val locationProvider = FakeLocationProvider()

    @Test
    fun `nearby rejeita coordenadas fora do intervalo`() = runTest {
        val useCase = GetNearbyStationsUseCase(repository)
        val result = useCase(NearbyQuery(latitude = 91.0, longitude = -46.0))
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.InvalidRequest::class.java)
        assertThat(repository.nearbyCalls).isEqualTo(0)
    }

    @Test
    fun `nearby rejeita raio e limite invalidos`() = runTest {
        val useCase = GetNearbyStationsUseCase(repository)
        assertThat(useCase(NearbyQuery(-23.5, -46.6, radiusKm = 0)).isFailure).isTrue()
        assertThat(useCase(NearbyQuery(-23.5, -46.6, radiusKm = 11)).isFailure).isTrue()
        assertThat(useCase(NearbyQuery(-23.5, -46.6, limit = 0)).isFailure).isTrue()
        assertThat(useCase(NearbyQuery(-23.5, -46.6, limit = 101)).isFailure).isTrue()
        assertThat(repository.nearbyCalls).isEqualTo(0)
    }

    @Test
    fun `nearby aceita limites das extremidades`() = runTest {
        val useCase = GetNearbyStationsUseCase(repository)
        val result = useCase(NearbyQuery(-90.0, 180.0, radiusKm = 10, limit = 100))
        assertThat(result.isSuccess).isTrue()
        assertThat(repository.lastQuery?.radiusKm).isEqualTo(10)
        assertThat(repository.lastQuery?.limit).isEqualTo(100)
    }

    @Test
    fun `details rejeita id fora do formato do openstreetmap`() = runTest {
        val useCase = GetStationDetailsUseCase(repository)
        listOf("", "123", "node_", "node_abc", "../etc").forEach { id ->
            assertThat(useCase(id).exceptionOrNull())
                .isInstanceOf(AppFailure.InvalidRequest::class.java)
        }
    }

    @Test
    fun `details repassa identificador valido`() = runTest {
        repository.detailsResult = Result.success(
            LoadResult(
                StationDetails(
                    station = station(id = "node_42"),
                    regionalPrices = emptyList(),
                    lastUpdated = null,
                    sources = emptyList(),
                ),
            ),
        )
        val result = GetStationDetailsUseCase(repository)("node_42")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.data?.station?.id).isEqualTo("node_42")
    }

    @Test
    fun `comparacao rejeita ids iguais sem chamar backend`() = runTest {
        val result = CompareStationsUseCase(repository)("node_1", "node_1")
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.InvalidRequest::class.java)
    }

    @Test
    fun `comparacao usa fallback local quando backend esta indisponivel`() = runTest {
        repository.cachedStations = mapOf(
            "node_1" to CachedStation(
                station(
                    id = "node_1",
                    name = "Posto A",
                    prices = listOf(
                        price(amount = 6.19, referenceDate = LocalDate.of(2026, 10, 24)),
                    ),
                ),
                cachedAt = Instant.parse("2026-10-24T12:00:00Z"),
            ),
            "node_2" to CachedStation(
                station(
                    id = "node_2",
                    name = "Posto B",
                    prices = listOf(
                        price(amount = 5.99, referenceDate = LocalDate.of(2026, 10, 24)),
                    ),
                ),
                cachedAt = Instant.parse("2026-10-24T13:00:00Z"),
            ),
        )
        val result = CompareStationsUseCase(repository)("node_1", "node_2")
        assertThat(result.isSuccess).isTrue()
        val loaded = result.getOrThrow()
        assertThat(loaded.fromCache).isTrue()
        assertThat(loaded.cachedAt).isEqualTo(Instant.parse("2026-10-24T12:00:00Z"))
        assertThat(loaded.data.rows.single().difference).isEqualTo(0.2)
    }

    @Test
    fun `comparacao nao usa fallback sem os dois postos em cache`() = runTest {
        repository.cachedStations = mapOf(
            "node_1" to CachedStation(
                station(id = "node_1"),
                Instant.parse("2026-10-24T12:00:00Z"),
            ),
        )
        val result = CompareStationsUseCase(repository)("node_1", "node_2")
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.ServiceUnavailable::class.java)
    }

    @Test
    fun `comparacao nao mascara posto inexistente com cache`() = runTest {
        repository.compareResult = Result.failure(AppFailure.NotFound())
        repository.cachedStations = mapOf(
            "node_1" to CachedStation(
                station(id = "node_1"),
                Instant.parse("2026-10-24T12:00:00Z"),
            ),
            "node_2" to CachedStation(
                station(id = "node_2"),
                Instant.parse("2026-10-24T12:00:00Z"),
            ),
        )
        val result = CompareStationsUseCase(repository)("node_1", "node_2")
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.NotFound::class.java)
    }

    @Test
    fun `busca remove espacos e valida tamanho e caracteres de controle`() = runTest {
        val useCase = SearchLocationsUseCase(geocoding)
        val ok = useCase("  Osasco  ")
        assertThat(ok.isSuccess).isTrue()
        assertThat(geocoding.lastQuery).isEqualTo("Osasco")
        assertThat(useCase("a").isFailure).isTrue()
        assertThat(useCase("a".repeat(161)).isFailure).isTrue()
        assertThat(useCase("Osasco\u0000").isFailure).isTrue()
        assertThat(useCase("Osasco<>").isFailure).isTrue()
    }

    @Test
    fun `busca rejeita limite invalido`() = runTest {
        val useCase = SearchLocationsUseCase(geocoding)
        assertThat(useCase("Osasco", limit = 0).isFailure).isTrue()
        assertThat(useCase("Osasco", limit = 9).isFailure).isTrue()
        assertThat(geocoding.lastLimit).isNull()
    }

    @Test
    fun `geocodificacao reversa valida coordenadas`() = runTest {
        val useCase = ReverseGeocodeLocationUseCase(geocoding)
        assertThat(useCase(91.0, 0.0).isFailure).isTrue()
        assertThat(useCase(0.0, -181.0).isFailure).isTrue()
        assertThat(useCase(-23.5, -46.6).getOrNull()?.municipality).isEqualTo("São Paulo")
    }

    @Test
    fun `localizacao falha quando gps desligado`() = runTest {
        locationProvider.enabled = false
        val result = ResolveDeviceLocationUseCase(locationProvider)()
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.LocationUnavailable::class.java)
        assertThat(locationProvider.currentCalls).isEqualTo(0)
    }

    @Test
    fun `localizacao falha quando provedor nao retorna posicao`() = runTest {
        locationProvider.location = null
        val result = ResolveDeviceLocationUseCase(locationProvider)()
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.LocationUnavailable::class.java)
    }

    @Test
    fun `localizacao rejeita coordenadas invalidas`() = runTest {
        locationProvider.location = DeviceLocation(999.0, 0.0)
        val result = ResolveDeviceLocationUseCase(locationProvider)()
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.LocationUnavailable::class.java)
    }

    @Test
    fun `localizacao valida e retornada`() = runTest {
        val result = ResolveDeviceLocationUseCase(locationProvider)()
        assertThat(result.getOrThrow().latitude).isEqualTo(-23.55)
    }
}
