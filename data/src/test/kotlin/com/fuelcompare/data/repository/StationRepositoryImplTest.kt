package com.fuelcompare.data.repository

import com.fuelcompare.core.common.DispatchersProvider
import com.fuelcompare.core.database.dao.DetailsSnapshotDao
import com.fuelcompare.core.database.dao.NearbySnapshotDao
import com.fuelcompare.core.database.entity.DetailsSnapshotEntity
import com.fuelcompare.core.database.entity.NearbySnapshotEntity
import com.fuelcompare.core.network.FuelCompareApi
import com.fuelcompare.core.network.NetworkJson
import com.fuelcompare.core.network.dto.ApiEnvelope
import com.fuelcompare.core.network.dto.ApiMeta
import com.fuelcompare.core.network.dto.ComparisonDto
import com.fuelcompare.core.network.dto.DetailsDataDto
import com.fuelcompare.core.network.dto.FuelPriceDto
import com.fuelcompare.core.network.dto.GeocodeDataDto
import com.fuelcompare.core.network.dto.NearbyDataDto
import com.fuelcompare.core.network.dto.RegionDto
import com.fuelcompare.core.network.dto.ReverseGeocodeDataDto
import com.fuelcompare.core.network.dto.SourceAttributionDto
import com.fuelcompare.core.network.dto.SourceRefDto
import com.fuelcompare.core.network.dto.StationDto
import com.fuelcompare.data.cache.NearbyCachePayload
import com.fuelcompare.domain.model.AppFailure
import com.fuelcompare.domain.model.NearbyQuery
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeApi : FuelCompareApi {
    var nearbyHandler: suspend () -> ApiEnvelope<NearbyDataDto> =
        { throw IOException("sem rede") }
    var detailsHandler: suspend () -> ApiEnvelope<DetailsDataDto> =
        { throw IOException("sem rede") }
    var compareHandler: suspend () -> ApiEnvelope<ComparisonDto> =
        { throw IOException("sem rede") }

    override suspend fun getNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
        city: String?,
        state: String?,
    ): ApiEnvelope<NearbyDataDto> = nearbyHandler()

    override suspend fun getStationDetails(stationId: String): ApiEnvelope<DetailsDataDto> =
        detailsHandler()

    override suspend fun getStationPrices(stationId: String): ApiEnvelope<DetailsDataDto> =
        detailsHandler()

    override suspend fun compare(
        stationAId: String,
        stationBId: String,
    ): ApiEnvelope<ComparisonDto> = compareHandler()

    override suspend fun searchLocations(
        query: String,
        limit: Int,
    ): ApiEnvelope<GeocodeDataDto> = throw UnsupportedOperationException()

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
    ): ApiEnvelope<ReverseGeocodeDataDto> = throw UnsupportedOperationException()
}

private class FakeNearbyDao : NearbySnapshotDao {
    val items = linkedMapOf<String, NearbySnapshotEntity>()

    override suspend fun find(cacheKey: String): NearbySnapshotEntity? = items[cacheKey]

    override suspend fun latest(): NearbySnapshotEntity? =
        items.values.maxByOrNull { it.savedAtEpochMillis }

    override suspend fun upsert(snapshot: NearbySnapshotEntity) {
        items[snapshot.cacheKey] = snapshot
    }

    override suspend fun trim() {
        while (items.size > 10) {
            val oldest = items.values.minByOrNull { it.savedAtEpochMillis } ?: break
            items.remove(oldest.cacheKey)
        }
    }

    override suspend fun clear() = items.clear()

    override suspend fun count(): Int = items.size
}

private class FakeDetailsDao : DetailsSnapshotDao {
    val items = linkedMapOf<String, DetailsSnapshotEntity>()

    override suspend fun find(stationId: String): DetailsSnapshotEntity? = items[stationId]

    override suspend fun upsert(snapshot: DetailsSnapshotEntity) {
        items[snapshot.stationId] = snapshot
    }

    override suspend fun trim() {
        while (items.size > 200) {
            val oldest = items.values.minByOrNull { it.savedAtEpochMillis } ?: break
            items.remove(oldest.stationId)
        }
    }

    override suspend fun delete(stationId: String) {
        items.remove(stationId)
    }

    override suspend fun clear() = items.clear()

    override suspend fun count(): Int = items.size
}

private object TestDispatchers : DispatchersProvider {
    override val io = Dispatchers.Unconfined
    override val default = Dispatchers.Unconfined
}

class StationRepositoryImplTest {

    private val api = FakeApi()
    private val nearbyDao = FakeNearbyDao()
    private val detailsDao = FakeDetailsDao()
    private val fixedNow = 1_760_000_000_000L
    private val repository = StationRepositoryImpl(
        api = api,
        nearbyDao = nearbyDao,
        detailsDao = detailsDao,
        dispatchers = TestDispatchers,
        nowMillis = { fixedNow },
    )
    private val query = NearbyQuery(-23.5505, -46.6333, radiusKm = 5, limit = 30)

    private val stationDto = StationDto(
        id = "node_1",
        name = "Posto A",
        latitude = -23.55,
        longitude = -46.63,
        distanceKm = 1.0,
        prices = listOf(
            FuelPriceDto(
                fuel = "gasoline",
                amount = 6.19,
                source = SourceRefDto("ANP", "https://www.gov.br/anp/exemplo.xlsx", true),
                referenceDate = "2026-10-12",
                matchMethod = "address",
            ),
        ),
    )

    private val nearbyDto = NearbyDataDto(
        stations = listOf(stationDto),
        region = RegionDto("São Paulo", "SP", -23.5505, -46.6333, "geocoder"),
        lastUpdated = "2026-10-17T12:00:00.000Z",
        sources = listOf(
            SourceAttributionDto(
                "openstreetmap",
                "OpenStreetMap",
                "https://www.openstreetmap.org/copyright",
                "Dados de postos",
            ),
        ),
    )

    @Test
    fun `consulta online salva snapshot local`() = runTest {
        api.nearbyHandler = { ApiEnvelope(nearbyDto, ApiMeta()) }
        val result = repository.getNearby(query)
        assertThat(result.getOrThrow().fromCache).isFalse()
        assertThat(result.getOrThrow().data.stations).hasSize(1)
        assertThat(nearbyDao.items).hasSize(1)
        assertThat(detailsDao.items).isEmpty()
    }

    @Test
    fun `sem rede usa snapshot exato e sinaliza cache`() = runTest {
        seedNearbyCache()
        api.nearbyHandler = { throw IOException("offline") }
        val loaded = repository.getNearby(query).getOrThrow()
        assertThat(loaded.fromCache).isTrue()
        assertThat(loaded.cachedAt).isEqualTo(Instant.ofEpochMilli(fixedNow))
        assertThat(loaded.data.stations.single().id).isEqualTo("node_1")
    }

    @Test
    fun `sem cache e sem rede retorna falha de rede`() = runTest {
        api.nearbyHandler = { throw IOException("offline") }
        val result = repository.getNearby(query)
        assertThat(result.exceptionOrNull()).isInstanceOf(AppFailure.Network::class.java)
    }

    @Test
    fun `detalhes offline caem para o snapshot de postos proximos`() = runTest {
        seedNearbyCache()
        api.detailsHandler = { throw IOException("offline") }
        val loaded = repository.getDetails("node_1").getOrThrow()
        assertThat(loaded.fromCache).isTrue()
        assertThat(loaded.data.station.id).isEqualTo("node_1")
        assertThat(loaded.data.regionalPrices).isEmpty()
        assertThat(loaded.data.sources).hasSize(1)
    }

    @Test
    fun `detalhes online sao salvos e reutilizados offline`() = runTest {
        api.detailsHandler = {
            ApiEnvelope(DetailsDataDto(station = stationDto), ApiMeta())
        }
        assertThat(repository.getDetails("node_1").isSuccess).isTrue()
        assertThat(detailsDao.items).hasSize(1)

        api.detailsHandler = { throw IOException("offline") }
        val cached = repository.getDetails("node_1").getOrThrow()
        assertThat(cached.fromCache).isTrue()
        assertThat(cached.data.station.id).isEqualTo("node_1")
    }

    @Test
    fun `posto em cache fica disponivel para comparacao offline`() = runTest {
        seedNearbyCache()
        val cached = repository.getCachedStation("node_1")
        assertThat(cached?.station?.id).isEqualTo("node_1")
        assertThat(cached?.cachedAt).isNotNull()
        assertThat(repository.getCachedStation("node_999")).isNull()
    }

    @Test
    fun `limpar cache remove snapshots`() = runTest {
        seedNearbyCache()
        repository.clearCache()
        assertThat(nearbyDao.items).isEmpty()
        assertThat(detailsDao.items).isEmpty()
    }

    @Test
    fun `chave de cache normaliza cidade estado e coordenadas`() {
        val a = nearbyCacheKey(query.copy(city = "  São Paulo ", state = "sp"))
        val b = nearbyCacheKey(query.copy(city = "são paulo", state = "SP"))
        assertThat(a).isEqualTo(b)
        assertThat(a).contains("-23.55")
    }

    private suspend fun seedNearbyCache() {
        val dto = NearbyDataDto(
            stations = listOf(stationDto),
            region = RegionDto("São Paulo", "SP", -23.5505, -46.6333, "geocoder"),
            sources = nearbyDto.sources,
        )
        val payload = NearbyCachePayload(dto, warnings = emptyList())
        nearbyDao.upsert(
            NearbySnapshotEntity(
                cacheKey = nearbyCacheKey(query),
                payload = NetworkJson.instance.encodeToString(
                    NearbyCachePayload.serializer(),
                    payload,
                ),
                savedAtEpochMillis = fixedNow,
            ),
        )
    }
}
