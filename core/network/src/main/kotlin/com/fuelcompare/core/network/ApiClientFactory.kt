package com.fuelcompare.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
        coerceInputValues = false
    }
}

object ApiClientFactory {
    private const val JSON_MEDIA_TYPE = "application/json"

    fun create(
        baseUrl: String,
        debug: Boolean,
        userAgent: String = "FuelCompare/1.0 (Android)",
    ): FuelCompareApi {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", JSON_MEDIA_TYPE)
                        .header("User-Agent", userAgent)
                        .build(),
                )
            }
            .apply {
                if (debug) {
                    // BASIC registra método, URL e status — nunca corpos ou cabeçalhos sensíveis.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(NetworkJson.instance.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(FuelCompareApi::class.java)
    }
}
