package com.fuelcompare.data.network

import com.fuelcompare.core.network.NetworkJson
import com.fuelcompare.core.network.dto.ApiErrorEnvelope
import com.fuelcompare.domain.model.AppFailure
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.decodeFromString
import retrofit2.HttpException

object NetworkFailureMapper {

    fun map(throwable: Throwable): AppFailure = when (throwable) {
        is AppFailure -> throwable
        is SocketTimeoutException -> AppFailure.Timeout(throwable)
        is HttpException -> fromHttpException(throwable)
        is IOException -> AppFailure.Network(throwable)
        else -> AppFailure.Unknown(throwable)
    }

    fun canUseOfflineCache(failure: Throwable): Boolean =
        failure is AppFailure.Network ||
            failure is AppFailure.Timeout ||
            failure is AppFailure.ServiceUnavailable

    private fun fromHttpException(exception: HttpException): AppFailure = when (exception.code()) {
        400, 422 -> AppFailure.InvalidRequest(extractPublicMessage(exception))
        404 -> AppFailure.NotFound()
        408 -> AppFailure.Timeout(exception)
        429 -> AppFailure.RateLimited()
        in 500..599 -> AppFailure.ServiceUnavailable(exception)
        else -> AppFailure.Unknown(exception)
    }

    /**
     * Somente mensagens públicas enviadas pelo próprio backend são reaproveitadas.
     * Detalhes internos, corpos inesperados e stack traces nunca são propagados.
     */
    private fun extractPublicMessage(exception: HttpException): String {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val fallback = "Parâmetros da consulta inválidos. Revise os dados e tente novamente."
        if (body.isNullOrBlank() || body.length > 16_384) return fallback
        val parsed = runCatching {
            NetworkJson.instance.decodeFromString<ApiErrorEnvelope>(body)
        }.getOrNull() ?: return fallback
        val message = parsed.error.message?.trim().orEmpty()
        if (message.isEmpty() || message.length > 200) return fallback
        return message
    }
}
