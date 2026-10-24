package com.fuelcompare.data.network

import com.fuelcompare.domain.model.AppFailure
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class NetworkFailureMapperTest {

    private fun httpError(code: Int, body: String = "{}"): HttpException =
        HttpException(
            Response.error<Any>(
                code,
                body.toResponseBody("application/json".toMediaType()),
            ),
        )

    @Test
    fun `timeout de socket vira falha de timeout`() {
        assertThat(NetworkFailureMapper.map(SocketTimeoutException()))
            .isInstanceOf(AppFailure.Timeout::class.java)
    }

    @Test
    fun `io vira falha de rede`() {
        assertThat(NetworkFailureMapper.map(IOException("sem rede")))
            .isInstanceOf(AppFailure.Network::class.java)
    }

    @Test
    fun `http 404 vira posto nao encontrado`() {
        assertThat(NetworkFailureMapper.map(httpError(404)))
            .isInstanceOf(AppFailure.NotFound::class.java)
    }

    @Test
    fun `http 429 vira limite de requisicoes`() {
        assertThat(NetworkFailureMapper.map(httpError(429)))
            .isInstanceOf(AppFailure.RateLimited::class.java)
    }

    @Test
    fun `http 5xx vira indisponibilidade sem detalhes internos`() {
        val failure = NetworkFailureMapper.map(
            httpError(500, """{"error":{"message":"stack trace /caminho/interno"}}"""),
        )
        assertThat(failure).isInstanceOf(AppFailure.ServiceUnavailable::class.java)
        assertThat(failure.publicMessage).doesNotContain("stack trace")
        assertThat(failure.publicMessage).doesNotContain("/caminho")
    }

    @Test
    fun `http 400 usa mensagem publica do backend`() {
        val failure = NetworkFailureMapper.map(
            httpError(400, """{"error":{"message":"Parâmetros de busca inválidos."}}"""),
        )
        assertThat(failure).isInstanceOf(AppFailure.InvalidRequest::class.java)
        assertThat(failure.publicMessage).isEqualTo("Parâmetros de busca inválidos.")
    }

    @Test
    fun `http 400 sem corpo seguro usa mensagem padrao`() {
        val failure = NetworkFailureMapper.map(httpError(400, "<html>erro interno</html>"))
        assertThat(failure).isInstanceOf(AppFailure.InvalidRequest::class.java)
        assertThat(failure.publicMessage).contains("Revise os dados")
    }

    @Test
    fun `falha de rede permissiva a cache e erro de validacao nao e`() {
        assertThat(NetworkFailureMapper.canUseOfflineCache(AppFailure.Network())).isTrue()
        assertThat(NetworkFailureMapper.canUseOfflineCache(AppFailure.Timeout())).isTrue()
        assertThat(NetworkFailureMapper.canUseOfflineCache(AppFailure.ServiceUnavailable())).isTrue()
        assertThat(
            NetworkFailureMapper.canUseOfflineCache(AppFailure.InvalidRequest("inválido")),
        ).isFalse()
        assertThat(NetworkFailureMapper.canUseOfflineCache(AppFailure.NotFound())).isFalse()
    }
}
