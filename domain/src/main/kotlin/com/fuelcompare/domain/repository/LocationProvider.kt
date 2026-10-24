package com.fuelcompare.domain.repository

import com.fuelcompare.domain.model.DeviceLocation

interface LocationProvider {
    /**
     * Obtém uma localização aproximada. Retorna null quando a permissão não foi
     * concedida, a localização está desativada ou o GPS não respondeu a tempo.
     * O app nunca solicita localização em segundo plano.
     */
    suspend fun getCurrentLocation(): DeviceLocation?

    fun isLocationEnabled(): Boolean
}
