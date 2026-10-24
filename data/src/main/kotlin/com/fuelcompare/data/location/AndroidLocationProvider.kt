package com.fuelcompare.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.fuelcompare.domain.model.DeviceLocation
import com.fuelcompare.domain.repository.LocationProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {

    override fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return manager.isLocationEnabled
    }

    override suspend fun getCurrentLocation(): DeviceLocation? {
        if (!hasLocationPermission()) return null
        if (!isLocationEnabled()) return null

        return withTimeoutOrNull(TIMEOUT_MS) {
            requestSingleLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(): DeviceLocation? =
        suspendCancellableCoroutine { continuation ->
            if (!hasLocationPermission()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val client = LocationServices.getFusedLocationProviderClient(context)
            val tokenSource = CancellationTokenSource()

            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(
                            location?.let { DeviceLocation(it.latitude, it.longitude) },
                        )
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.resume(null)
                }

            continuation.invokeOnCancellation { tokenSource.cancel() }
        }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TIMEOUT_MS = 12_000L
    }
}
