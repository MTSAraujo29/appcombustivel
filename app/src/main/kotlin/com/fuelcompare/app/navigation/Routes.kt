package com.fuelcompare.app.navigation

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"

    const val STATION_DETAILS = "station/{stationId}"
    const val COMPARISON = "comparison?stationA={stationA}&stationB={stationB}"

    const val ARG_STATION_ID = "stationId"
    const val ARG_STATION_A = "stationA"
    const val ARG_STATION_B = "stationB"

    fun stationDetails(stationId: String): String = "station/${Uri.encode(stationId)}"

    fun comparison(stationAId: String? = null, stationBId: String? = null): String {
        val a = stationAId?.let { "stationA=${Uri.encode(it)}" }.orEmpty()
        val b = stationBId?.let { "stationB=${Uri.encode(it)}" }.orEmpty()
        return when {
            a.isNotBlank() && b.isNotBlank() -> "comparison?$a&$b"
            a.isNotBlank() -> "comparison?$a"
            b.isNotBlank() -> "comparison?$b"
            else -> "comparison"
        }
    }
}
