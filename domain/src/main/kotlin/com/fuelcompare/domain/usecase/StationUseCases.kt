package com.fuelcompare.domain.usecase

import com.fuelcompare.domain.model.AppFailure
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.LoadResult
import com.fuelcompare.domain.model.NearbyQuery
import com.fuelcompare.domain.model.NearbyStations
import com.fuelcompare.domain.model.SortMode
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.repository.StationRepository
import com.fuelcompare.domain.rules.CheapestStation
import com.fuelcompare.domain.rules.CheapestStationFinder
import com.fuelcompare.domain.rules.PriceComparisonCalculator
import com.fuelcompare.domain.rules.StationSorter
import java.time.LocalDate

object QueryValidation {
    const val MIN_RADIUS_KM = 1
    const val MAX_RADIUS_KM = 10
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 100
    private val stationIdPattern = Regex("^(node|way|relation)_\\d{1,20}$")

    fun validateCoordinates(latitude: Double, longitude: Double): String? = when {
        !latitude.isFinite() || latitude < -90.0 || latitude > 90.0 ->
            "Latitude inválida. Informe um valor entre -90 e 90."

        !longitude.isFinite() || longitude < -180.0 || longitude > 180.0 ->
            "Longitude inválida. Informe um valor entre -180 e 180."

        else -> null
    }

    fun validateStationId(id: String): String? =
        if (stationIdPattern.matches(id)) null else "Identificador de posto inválido."

    fun validateRadius(radiusKm: Int): String? =
        if (radiusKm in MIN_RADIUS_KM..MAX_RADIUS_KM) null
        else "O raio deve ficar entre $MIN_RADIUS_KM e $MAX_RADIUS_KM km."

    fun validateLimit(limit: Int): String? =
        if (limit in MIN_LIMIT..MAX_LIMIT) null
        else "O limite deve ficar entre $MIN_LIMIT e $MAX_LIMIT postos."
}

class GetNearbyStationsUseCase(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(
        query: NearbyQuery,
        forceRefresh: Boolean = false,
    ): Result<LoadResult<NearbyStations>> {
        QueryValidation.validateCoordinates(query.latitude, query.longitude)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        QueryValidation.validateRadius(query.radiusKm)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        QueryValidation.validateLimit(query.limit)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        return repository.getNearby(query, forceRefresh)
    }
}

class GetStationDetailsUseCase(
    private val repository: StationRepository,
) {
    suspend operator fun invoke(stationId: String): Result<LoadResult<StationDetails>> {
        QueryValidation.validateStationId(stationId)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        return repository.getDetails(stationId)
    }
}

class CompareStationsUseCase(
    private val repository: StationRepository,
) {
    /**
     * Busca a comparação oficial no backend. Sem conexão, tenta montar a
     * comparação com os postos salvos no aparelho, sinalizando a origem.
     */
    suspend operator fun invoke(
        stationAId: String,
        stationBId: String,
    ): Result<LoadResult<StationComparison>> {
        QueryValidation.validateStationId(stationAId)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        QueryValidation.validateStationId(stationBId)?.let {
            return Result.failure(AppFailure.InvalidRequest(it))
        }
        if (stationAId == stationBId) {
            return Result.failure(
                AppFailure.InvalidRequest("Selecione dois postos diferentes para comparar."),
            )
        }

        val remote = repository.compare(stationAId, stationBId)
        if (remote.isSuccess) return remote

        val failure = remote.exceptionOrNull()
        if (failure !is AppFailure.Network &&
            failure !is AppFailure.Timeout &&
            failure !is AppFailure.ServiceUnavailable
        ) {
            return remote
        }

        val cachedA = repository.getCachedStation(stationAId) ?: return remote
        val cachedB = repository.getCachedStation(stationBId) ?: return remote

        // Usa a data do snapshot mais antigo: é a idade mínima garantida dos dados.
        val cachedAt = minOf(cachedA.cachedAt, cachedB.cachedAt)

        return Result.success(
            LoadResult(
                data = PriceComparisonCalculator.compare(cachedA.station, cachedB.station),
                fromCache = true,
                cachedAt = cachedAt,
            ),
        )
    }
}

class GetCheapestStationUseCase {
    operator fun invoke(
        stations: List<Station>,
        fuel: FuelType,
        today: LocalDate = LocalDate.now(),
    ): CheapestStation? = CheapestStationFinder.find(stations, fuel, today)
}

class SortStationsUseCase {
    operator fun invoke(stations: List<Station>, mode: SortMode, fuel: FuelType): List<Station> =
        StationSorter.sort(stations, mode, fuel)
}
