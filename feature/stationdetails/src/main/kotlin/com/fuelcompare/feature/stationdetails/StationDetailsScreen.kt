package com.fuelcompare.feature.stationdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuelcompare.core.common.Formatters
import com.fuelcompare.core.designsystem.component.AppLogo
import com.fuelcompare.core.designsystem.component.BannerTone
import com.fuelcompare.core.designsystem.component.EmptyPane
import com.fuelcompare.core.designsystem.component.ErrorPane
import com.fuelcompare.core.designsystem.component.InfoBanner
import com.fuelcompare.core.designsystem.component.LabeledValue
import com.fuelcompare.core.designsystem.component.LastUpdatedText
import com.fuelcompare.core.designsystem.component.LoadingPane
import com.fuelcompare.core.designsystem.component.OfflineBanner
import com.fuelcompare.core.designsystem.component.PriceBadgeStyle
import com.fuelcompare.core.designsystem.component.PriceTag
import com.fuelcompare.core.designsystem.component.PriceTypeBadge
import com.fuelcompare.core.designsystem.component.SectionTitle
import com.fuelcompare.core.designsystem.component.SourceAttributionSection
import com.fuelcompare.core.designsystem.component.SourceUiModel
import com.fuelcompare.core.designsystem.component.WarningsList
import com.fuelcompare.core.designsystem.theme.AppTheme
import com.fuelcompare.domain.model.FuelPrice
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.RegionalFuelPrice
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationDetails
import com.fuelcompare.domain.rules.PriceRules
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun StationDetailsRoute(
    onBack: () -> Unit,
    viewModel: StationDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val notifyFailure = {
        scope.launch {
            snackbarHostState.showSnackbar(
                "Não foi possível abrir o aplicativo ou o link. Tente novamente.",
            )
        }
    }

    StationDetailsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onOpenRoute = { station ->
            if (!openRouteToStation(context, station)) notifyFailure()
        },
        onOpenMap = { station ->
            if (!openStationOnMap(context, station)) notifyFailure()
        },
        onOpenUrl = { url ->
            if (url.isBlank() || runCatching { uriHandler.openUri(url) }.isFailure) {
                notifyFailure()
            }
        },
    )
}

@Composable
private fun StationDetailsScreen(
    state: StationDetailsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenRoute: (Station) -> Unit,
    onOpenMap: (Station) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                    )
                }
                Text(
                    text = "Detalhes do posto",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isRefreshing && state.details != null,
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Atualizar dados do posto",
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isMissingStationId -> EmptyPane(
                        title = "Posto não identificado",
                        message = "Volte à tela inicial e escolha um posto na lista para ver os detalhes.",
                        icon = Icons.Outlined.LocationOff,
                        actionLabel = "Voltar",
                        onAction = onBack,
                    )

                    state.isLoading && state.details == null -> LoadingPane(
                        message = "Carregando detalhes e preços do posto…",
                    )

                    state.details == null -> ErrorPane(
                        message = state.errorMessage
                            ?: "Não foi possível carregar os detalhes deste posto.",
                        onRetry = onRetry,
                    )

                    else -> DetailsContent(
                        state = state,
                        details = state.details,
                        onRefresh = onRefresh,
                        onRetry = onRetry,
                        onOpenRoute = onOpenRoute,
                        onOpenMap = onOpenMap,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsContent(
    state: StationDetailsUiState,
    details: StationDetails,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenRoute: (Station) -> Unit,
    onOpenMap: (Station) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.fromCache) {
                item(key = "cache-banner") {
                    OfflineBanner(cachedAt = state.cachedAt, onRetry = onRefresh)
                }
            }
            if (state.errorMessage != null) {
                item(key = "refresh-error") {
                    InfoBanner(
                        message = state.errorMessage,
                        tone = BannerTone.ERROR,
                        actionLabel = "Tentar novamente",
                        onAction = onRetry,
                    )
                }
            }
            if (details.warnings.isNotEmpty()) {
                item(key = "warnings") {
                    WarningsList(warnings = details.warnings.take(3))
                }
            }
            item(key = "hero") {
                StationHero(
                    station = details.station,
                    lastUpdated = details.lastUpdated,
                )
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onOpenRoute(details.station) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NearMe,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Abrir rota")
                    }
                    OutlinedButton(
                        onClick = { onOpenMap(details.station) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Ver no mapa")
                    }
                }
                Text(
                    text = "A rota abre no app de mapas instalado. Se não houver, " +
                        "abrimos o mapa do OpenStreetMap no navegador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "prices-title") {
                SectionTitle(
                    title = "Preços por combustível",
                    subtitle = "Somente preços individuais deste posto, com fonte e data " +
                        "do levantamento. Valores antigos são sinalizados.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item(key = "prices") {
                PricesCard(station = details.station)
            }
            item(key = "regional-title") {
                SectionTitle(
                    title = "Média municipal",
                    subtitle = "Referência do levantamento da ANP para o município — " +
                        "não é o preço deste posto.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item(key = "regional-disclaimer") {
                InfoBanner(
                    title = "Não atribua médias a um posto",
                    message = "As médias municipais resumem vários postos da cidade e não " +
                        "representam o preço cobrado por este estabelecimento.",
                    tone = BannerTone.WARNING,
                )
            }
            if (details.regionalPrices.isEmpty()) {
                item(key = "regional-empty") {
                    InfoBanner(
                        title = "Sem média municipal disponível",
                        message = "O levantamento da ANP não traz média para este município " +
                            "no período consultado.",
                    )
                }
            } else {
                item(key = "regional") {
                    MunicipalAveragesCard(regionalPrices = details.regionalPrices)
                }
            }
            item(key = "sources-title") {
                SectionTitle(
                    title = "Fontes dos dados",
                    subtitle = "Consulte a origem oficial dos preços e da localização.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "sources") {
                val uiSources = details.sources.map { source ->
                    SourceUiModel(
                        id = source.id,
                        name = source.name,
                        description = source.description,
                        url = source.url,
                    )
                }
                if (uiSources.isEmpty()) {
                    InfoBanner(
                        message = "O serviço não informou as fontes desta consulta.",
                        tone = BannerTone.WARNING,
                    )
                } else {
                    SourceAttributionSection(
                        sources = uiSources,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
            item(key = "disclaimer") {
                InfoBanner(
                    title = "Preços podem mudar",
                    message = "Confirme o valor no posto antes de abastecer. O app não " +
                        "garante preço nem disponibilidade. Localização dos postos: " +
                        "© OpenStreetMap contributors.",
                    tone = BannerTone.INFO,
                )
            }
        }
    }
}

@Composable
private fun StationHero(
    station: Station,
    lastUpdated: Instant?,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 44.dp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    station.brand
                        ?.takeIf { it.isNotBlank() }
                        ?.takeUnless { brand ->
                            station.name.contains(brand, ignoreCase = true)
                        }
                        ?.let { brand ->
                            Text(
                                text = brand,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = station.address.shortLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            station.distanceKm?.let { distance ->
                Spacer(Modifier.height(6.dp))
                LabeledValue(
                    label = "Distância da busca",
                    value = Formatters.distance(distance),
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))
            LastUpdatedText(lastUpdated = lastUpdated)
        }
    }
}

@Composable
private fun PricesCard(station: Station) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            FuelType.entries.forEachIndexed { index, fuel ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                FuelPriceRow(
                    fuel = fuel,
                    price = station.priceFor(fuel),
                )
            }
        }
    }
}

@Composable
private fun FuelPriceRow(
    fuel: FuelType,
    price: FuelPrice?,
) {
    val colors = AppTheme.extendedColors
    val stale = price != null && !PriceRules.isFresh(price)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fuel.label,
                style = MaterialTheme.typography.titleMedium,
            )
            if (price != null) {
                Text(
                    text = "${price.source.name} · ${Formatters.date(price.referenceDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stale) {
                    Text(
                        text = "Fora da janela de 31 dias: pode não refletir o preço atual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warning,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                Text(
                    text = "Sem preço individual disponível para este combustível.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (price != null) {
                PriceTag(
                    amount = price.amount,
                    unitLabel = price.unit.symbol,
                )
                if (stale) {
                    PriceTypeBadge(
                        text = "Desatualizado",
                        style = PriceBadgeStyle.WARNING,
                    )
                } else {
                    PriceTypeBadge(
                        text = "Individual",
                        style = PriceBadgeStyle.INDIVIDUAL,
                    )
                }
            } else {
                Text(
                    text = "Indisponível",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MunicipalAveragesCard(regionalPrices: List<RegionalFuelPrice>) {
    val colors = AppTheme.extendedColors
    val reference = regionalPrices.firstOrNull { it.isPlausible } ?: return
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${reference.municipality}/${reference.state}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PriceTypeBadge(
                    text = "Média municipal",
                    style = PriceBadgeStyle.MUNICIPAL_AVERAGE,
                )
            }
            Text(
                text = "Valores médios por combustível; não são preços deste posto.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            regionalPrices.forEachIndexed { index, regional ->
                if (index > 0) {
                    HorizontalDivider(
                        color = colors.onWarningContainer.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = regional.fuel.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Faixa de ${Formatters.currency(regional.minAmount)} a " +
                                "${Formatters.currency(regional.maxAmount)} · " +
                                "${regional.surveyedStations} postos",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Semana de ${Formatters.date(regional.referenceStart)} a " +
                                "${Formatters.date(regional.referenceEnd)} · ${regional.source.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatters.currency(regional.amount) +
                                "/${regional.unit.symbol}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "média",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
