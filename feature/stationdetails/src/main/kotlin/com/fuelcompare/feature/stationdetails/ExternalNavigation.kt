package com.fuelcompare.feature.stationdetails

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fuelcompare.domain.model.Station

/**
 * Abre a rota em um app de mapas via intent `geo:`. Quando não há app capaz
 * de tratar o intent, recorre ao mapa web do OpenStreetMap (sem chave paga).
 * Retorna false apenas se ambas as alternativas falharem.
 */
internal fun openRouteToStation(context: Context, station: Station): Boolean {
    val label = Uri.encode(station.name)
    val coordinates = "${station.latitude},${station.longitude}"
    val geoUri = Uri.parse("geo:$coordinates?q=$coordinates($label)")
    if (launchView(context, geoUri)) return true
    return openStationOnMap(context, station)
}

internal fun openStationOnMap(context: Context, station: Station): Boolean {
    val uri = Uri.parse(
        "https://www.openstreetmap.org/?mlat=${station.latitude}" +
            "&mlon=${station.longitude}" +
            "#map=17/${station.latitude}/${station.longitude}",
    )
    return launchView(context, uri)
}

private fun launchView(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}
