package com.fuelcompare.data.repository

import com.fuelcompare.core.common.DispatchersProvider
import com.fuelcompare.core.database.dao.DetailsSnapshotDao
import com.fuelcompare.core.database.dao.NearbySnapshotDao
import com.fuelcompare.core.database.entity.DetailsSnapshotEntity
import com.fuelcompare.core.database.entity.NearbySnapshotEntity
import com.fuelcompare.core.network.FuelCompareApi
import com.fuelcompare.core.network.NetworkJson
import com.fuelcompare.core.network.dto.ApiMeta
import com.fuelcompare.data.cache.DetailsCachePayload
import com.fuelcompare.data.cache.NearbyCachePayload
import com.fuelcompare.data.mapper.ApiMappers
import com.fuelcompare.data.network.NetworkFailureMapper
import com.fuelcompare.domain.model.AppFailure
import com.fuelcompare.domain.model.CachedStation
import com.fuelcompare.domain.model.LoadResult
import com.fuelcompare.domain.model.NearbyQuery
import com.fuelcompare.domain.model.NearbyStations
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.repository.StationRepository
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round
import kotlinx.coroutines.withContext

class StationRepositoryImpl(
    private val api: FuelCompareApi,
    private val nearbyDao: NearbySnapshotDao,
    private val detailsDao: DetailsSnapshotDao,
    private val dispatchers: DispatchersProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : StationRepository {

    override suspend fun getNearby(
        query: NearbyQuery,
        forceRefresh: Boolean,
    ): Result<LoadResult<NearbyStations>> = withContext(dispatchers.io) {
        // forceRefresh sempre consulta a rede; o cache local é apenas fallback.
        try {
            val response = api.getNearby(
                latitude = query.latitude,
                longitude = query.longitude,
                radiusKm = query.radiusKm,
                limit = query.limit,
                city = query.city,
                state = query.state,
            )
            val mapped = ApiMappers.mapNearby(response.data, response.meta)
            persistNearby(query, response.data, response.meta)
            Result.success(LoadResult(mapped))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = NetworkFailureMapper.map(error)
            if (NetworkFailureMapper.canUseOfflineCache(failure)) {
                readNearbyFromCache(query)?.let { return@withContext Result.success(it) }
            }
            Result.failure(failure)
        }
    }

    override suspend fun getDetails(
        stationId: String,
    ): Result<LoadResult<StationDetails>> = withContext(dispatchers.io) {
        try {
            val response = api.getStationDetails(stationId)
            val mapped = ApiMappers.mapDetails(response.data, response.meta)
                ?: throw AppFailure.ServiceUnavailable()
            persistDetails(stationId, response.data, response.meta)
            Result.success(LoadResult(mapped))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = NetworkFailureMapper.map(error)
            if (NetworkFailureMapper.canUseOfflineCache(failure)) {
                readDetailsFromCache(stationId)?.let { return@withContext Result.success(it) }
            }
            Result.failure(failure)
        }
    }

    override suspend fun compare(
        stationAId: String,
        stationBId: String,
    ): Result<LoadResult<StationComparison>> = withContext(dispatchers.io) {
        try {
            val response = api.compare(stationAId, stationBId)
            Result.success(LoadResult(ApiMappers.mapComparison(response.data)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(NetworkFailureMapper.map(error))
        }
    }

    override suspend fun getCachedStation(stationId: String): CachedStation? =
        withContext(dispatchers.io) {
            readCachedStation(stationId)
        }

    override suspend fun clearCache() = withContext(dispatchers.io) {
        nearbyDao.clear()
        detailsDao.clear()
    }

    private suspend fun persistNearby(
        query: NearbyQuery,
        data: com.fuelcompare.core.network.dto.NearbyDataDto,
        meta: ApiMeta,
    ) {
        try {
            if (data.stations.isEmpty() && data.region == null) return
            val payload = NearbyCachePayload(data = data, warnings = meta.warnings.take(20))
            val encoded = NetworkJson.instance.encodeToString(NearbyCachePayload.serializer(), payload)
            nearbyDao.upsert(
                NearbySnapshotEntity(
                    cacheKey = nearbyCacheKey(query),
                    payload = encoded,
                    savedAtEpochMillis = nowMillis(),
                ),
            )
            nearbyDao.trim()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Cache é otimização; falhas de disco não devem interromper a consulta online.
        }
    }

    private suspend fun persistDetails(
        stationId: String,
        data: com.fuelcompare.core.network.dto.DetailsDataDto,
        meta: ApiMeta,
    ) {
        try {
            val payload = DetailsCachePayload(data = data, warnings = meta.warnings.take(20))
            val encoded = NetworkJson.instance.encodeToString(DetailsCachePayload.serializer(), payload)
            detailsDao.upsert(
                DetailsSnapshotEntity(
                    stationId = stationId,
                    payload = encoded,
                    savedAtEpochMillis = nowMillis(),
                ),
            )
            detailsDao.trim()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Falha silenciosa e segura: o cache nunca é fonte de erro para o usuário.
        }
    }

    private suspend fun readNearbyFromCache(query: NearbyQuery): LoadResult<NearbyStations>? {
        val entity = try {
            nearbyDao.find(nearbyCacheKey(query)) ?: nearbyDao.latest()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        } ?: return null

        val payload = decodeNearby(entity.payload) ?: return null
        val mapped = ApiMappers.mapNearby(payload.data, ApiMeta(warnings = payload.warnings))
        if (mapped.stations.isEmpty() && mapped.region == null) return null
        return LoadResult(
            data = mapped,
            fromCache = true,
            cachedAt = Instant.ofEpochMilli(entity.savedAtEpochMillis),
        )
    }

    private suspend fun readDetailsFromCache(stationId: String): LoadResult<StationDetails>? {
        val detailsEntity = try {
            detailsDao.find(stationId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        if (detailsEntity != null) {
            val payload = decodeDetails(detailsEntity.payload)
            val mapped = payload?.let { ApiMappers.mapDetails(it.data, ApiMeta(warnings = it.warnings)) }
            if (mapped != null) {
                return LoadResult(
                    data = mapped,
                    fromCache = true,
                    cachedAt = Instant.ofEpochMilli(detailsEntity.savedAtEpochMillis),
                )
            }
        }

        // Sem detalhes salvos, aproveita a última busca de postos próximos.
        val nearbyEntity = try {
            nearbyDao.latest()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null

        val payload = decodeNearby(nearbyEntity.payload) ?: return null
        val station = payload.data.stations
            .asSequence()
            .mapNotNull(ApiMappers::mapStation)
            .firstOrNull { it.id == stationId }
            ?: return null
        val mapped = ApiMappers.mapNearby(payload.data, ApiMeta(warnings = payload.warnings))

        return LoadResult(
            data = StationDetails(
                station = station,
                regionalPrices = mapped.regionalPrices,
                lastUpdated = mapped.lastUpdated,
                sources = mapped.sources,
                warnings = mapped.warnings,
            ),
            fromCache = true,
            cachedAt = Instant.ofEpochMilli(nearbyEntity.savedAtEpochMillis),
        )
    }

    private suspend fun readCachedStation(stationId: String): CachedStation? {
        val detailsEntity = try {
            detailsDao.find(stationId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (detailsEntity != null) {
            decodeDetails(detailsEntity.payload)
                ?.let { ApiMappers.mapStation(it.data.station) }
                ?.let {
                    return CachedStation(
                        station = it,
                        cachedAt = Instant.ofEpochMilli(detailsEntity.savedAtEpochMillis),
                    )
                }
        }

        val nearbyEntity = try {
            nearbyDao.latest()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null

        val payload = decodeNearby(nearbyEntity.payload) ?: return null
        val station = payload.data.stations
            .asSequence()
            .mapNotNull(ApiMappers::mapStation)
            .firstOrNull { it.id == stationId }
            ?: return null
        return CachedStation(
            station = station,
            cachedAt = Instant.ofEpochMilli(nearbyEntity.savedAtEpochMillis),
        )
    }

    private fun decodeNearby(payload: String): NearbyCachePayload? =
        try {
            NetworkJson.instance.decodeFromString(NearbyCachePayload.serializer(), payload)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun decodeDetails(payload: String): DetailsCachePayload? =
        try {
            NetworkJson.instance.decodeFromString(DetailsCachePayload.serializer(), payload)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}

internal fun nearbyCacheKey(query: NearbyQuery): String {
    fun coordinate(value: Double): String = (round(value * 1000.0) / 1000.0).toString()
    val city = query.city?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val state = query.state?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return listOf(
        coordinate(query.latitude),
        coordinate(query.longitude),
        query.radiusKm.toString(),
        query.limit.toString(),
        city,
        state,
    ).joinToString("|")
}
