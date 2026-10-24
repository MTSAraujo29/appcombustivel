package com.fuelcompare.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fuelcompare.core.common.Formatters
import com.fuelcompare.domain.model.Station
import java.time.Instant
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Mapa real via osmdroid/OpenStreetMap, sem chave paga. Os marcadores são
 * apenas um complemento visual: a lista oferece a mesma informação de forma
 * acessível.
 */
@Composable
internal fun StationMap(
    stations: List<Station>,
    cheapestStationId: String?,
    initialCenter: MapPoint?,
    focus: MapPoint?,
    isRefreshing: Boolean,
    lastUpdated: Instant?,
    fromCache: Boolean,
    onOpenStation: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onMapMoved: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val currentOnOpenStation by rememberUpdatedState(onOpenStation)
    val currentOnMapMoved by rememberUpdatedState(onMapMoved)

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(14.0)
            val start = initialCenter ?: MapPoint(-23.5505, -46.6333)
            controller.setCenter(GeoPoint(start.latitude, start.longitude))
            setUseDataConnection(true)
        }
    }

    var moveSignal by remember { mutableIntStateOf(0) }
    val mapListener = remember(mapView) {
        object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                moveSignal += 1
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                moveSignal += 1
                return false
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        mapView.addMapListener(mapListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(mapListener)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(moveSignal) {
        if (moveSignal == 0) return@LaunchedEffect
        delay(MAP_MOVE_DEBOUNCE_MS)
        val center = mapView.mapCenter
        currentOnMapMoved(center.latitude, center.longitude)
    }

    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(target.latitude, target.longitude))
    }

    Box(modifier = modifier) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Mapa com ${stations.size} postos próximos. " +
                        "Toque em um marcador para abrir os detalhes ou use a visão em lista."
                },
            update = { map ->
                val normalIcon = ContextCompat.getDrawable(context, R.drawable.ic_marker_station)
                val cheapestIcon = ContextCompat.getDrawable(context, R.drawable.ic_marker_cheapest)
                map.overlays.clear()
                stations.forEach { station ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(station.latitude, station.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = if (station.id == cheapestStationId) cheapestIcon else normalIcon
                        title = station.name
                        snippet = station.address.shortLabel
                        setOnMarkerClickListener { _, _ ->
                            currentOnOpenStation(station.id)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            },
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (stations.size == 1) "1 posto no mapa" else "${stations.size} postos no mapa",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = lastUpdated?.let { "Atualizado em ${Formatters.shortDateTime(it)}" }
                        ?: "Atualização indisponível",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fromCache) {
                    Text(
                        text = "Dados salvos no aparelho",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.primary,
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Atualizar postos e preços",
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onLocate,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = "Buscar postos perto da minha localização",
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(
                        onClickLabel = "Abrir direitos autorais do OpenStreetMap",
                    ) {
                        runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

private const val MAP_MOVE_DEBOUNCE_MS = 700L
