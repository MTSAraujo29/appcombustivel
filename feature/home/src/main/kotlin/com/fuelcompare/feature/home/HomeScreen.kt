package com.fuelcompare.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuelcompare.core.common.Formatters
import com.fuelcompare.core.designsystem.component.AppLogo
import com.fuelcompare.core.designsystem.component.BannerTone
import com.fuelcompare.core.designsystem.component.CheapestBadge
import com.fuelcompare.core.designsystem.component.EmptyPane
import com.fuelcompare.core.designsystem.component.ErrorPane
import com.fuelcompare.core.designsystem.component.InfoBanner
import com.fuelcompare.core.designsystem.component.LastUpdatedText
import com.fuelcompare.core.designsystem.component.LoadingPane
import com.fuelcompare.core.designsystem.component.OfflineBanner
import com.fuelcompare.core.designsystem.component.PriceBadgeStyle
import com.fuelcompare.core.designsystem.component.PriceTag
import com.fuelcompare.core.designsystem.component.PriceTypeBadge
import com.fuelcompare.core.designsystem.component.PrimaryButton
import com.fuelcompare.core.designsystem.component.SecondaryButton
import com.fuelcompare.core.designsystem.component.SourceAttributionSection
import com.fuelcompare.core.designsystem.component.SourceUiModel
import com.fuelcompare.core.designsystem.theme.AppTheme
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.RegionalFuelPrice
import com.fuelcompare.domain.model.SortMode
import com.fuelcompare.domain.model.SourceAttribution
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.rules.CheapestStation
import com.fuelcompare.domain.rules.PriceRules

@Composable
fun HomeRoute(
    onOpenStation: (String) -> Unit,
    onCompareStations: (String, String) -> Unit,
    onOpenManualComparison: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.locateUser() else viewModel.onLocationPermissionDenied()
    }
    val requestLocation = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.locateUser()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    if (state.searchVisible) {
        RegionSearchScreen(
            state = state,
            onQueryChange = viewModel::onSearchQueryChange,
            onSearch = viewModel::searchNow,
            onSuggestionClick = viewModel::selectSearchSuggestion,
            onClose = viewModel::closeSearch,
            onUseLocation = requestLocation,
            onDismissError = viewModel::dismissError,
        )
    } else {
        HomeScreen(
            state = state,
            onFuelSelected = viewModel::selectFuel,
            onSortSelected = viewModel::selectSortMode,
            onViewModeSelected = viewModel::setViewMode,
            onRadiusSelected = viewModel::selectRadius,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onDismissError = viewModel::dismissError,
            onOpenSearch = viewModel::openSearch,
            onOpenSettings = onOpenSettings,
            onOpenStation = onOpenStation,
            onCompareStations = onCompareStations,
            onOpenManualComparison = onOpenManualComparison,
            onLocate = requestLocation,
            onMapMoved = viewModel::onMapMoved,
        )
    }
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onFuelSelected: (FuelType) -> Unit,
    onSortSelected: (SortMode) -> Unit,
    onViewModeSelected: (HomeViewMode) -> Unit,
    onRadiusSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStation: (String) -> Unit,
    onCompareStations: (String, String) -> Unit,
    onOpenManualComparison: () -> Unit,
    onLocate: () -> Unit,
    onMapMoved: (Double, Double) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HomeTopBar(
                regionLabel = state.region?.label,
                isLocating = state.isLocating,
                onOpenSearch = onOpenSearch,
                onLocate = onLocate,
                onOpenSettings = onOpenSettings,
            )
            if (!state.needsLocation) {
                FuelFilterRow(
                    selectedFuel = state.selectedFuel,
                    onFuelSelected = onFuelSelected,
                )
                HomeControlsRow(
                    viewMode = state.viewMode,
                    radiusKm = state.radiusKm,
                    onViewModeSelected = onViewModeSelected,
                    onRadiusSelected = onRadiusSelected,
                )
            }
            HomeStatusBanners(
                state = state,
                onRetry = onRefresh,
                onDismissError = onDismissError,
            )
            if (state.stations.isNotEmpty()) {
                if (state.cheapest != null) {
                    BestResultCard(
                        cheapest = state.cheapest,
                        selectedFuel = state.selectedFuel,
                        canCompare = state.topPricedStationIds.size >= 2,
                        canChooseStations = state.stations.size >= 2,
                        onOpenStation = onOpenStation,
                        onCompare = {
                            val ids = state.topPricedStationIds
                            if (ids.size >= 2) onCompareStations(ids[0], ids[1])
                        },
                        onChooseStations = onOpenManualComparison,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else {
                    InfoBanner(
                        title = "Sem preço individual recente",
                        message = "Não há preço individual recente de ${state.selectedFuel.label} " +
                            "para os postos desta área. Os postos vêm do mapa colaborativo; " +
                            "a média municipal, quando exibida, é apenas referência.",
                        tone = BannerTone.WARNING,
                        actionLabel = if (state.stations.size >= 2) {
                            "Comparar dois postos"
                        } else {
                            null
                        },
                        onAction = if (state.stations.size >= 2) {
                            onOpenManualComparison
                        } else {
                            null
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.needsLocation -> NeedsLocationPane(
                        isLocating = state.isLocating,
                        errorMessage = state.errorMessage,
                        onLocate = onLocate,
                        onOpenSearch = onOpenSearch,
                        onDismissError = onDismissError,
                    )

                    state.isLoading -> LoadingPane(
                        message = "Buscando postos e preços próximos…",
                    )

                    state.stations.isEmpty() && state.errorMessage != null -> ErrorPane(
                        message = state.errorMessage,
                        onRetry = onRetry,
                    )

                    state.stations.isEmpty() -> EmptyPane(
                        title = "Nenhum posto encontrado",
                        message = "Não encontramos postos em um raio de ${state.radiusKm} km" +
                            (state.region?.let { " de ${it.label}" } ?: "") +
                            ". Amplie o raio ou busque outra cidade.",
                        icon = Icons.Outlined.LocationOff,
                        actionLabel = "Buscar outra região",
                        onAction = onOpenSearch,
                    )

                    state.viewMode == HomeViewMode.MAP -> MapPane(
                        state = state,
                        onOpenStation = onOpenStation,
                        onRefresh = onRefresh,
                        onLocate = onLocate,
                        onMapMoved = onMapMoved,
                    )

                    else -> StationList(
                        state = state,
                        onSortSelected = onSortSelected,
                        onOpenStation = onOpenStation,
                        onRefresh = onRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    regionLabel: String?,
    isLocating: Boolean,
    onOpenSearch: () -> Unit,
    onLocate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 34.dp)
        Spacer(Modifier.size(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    onClickLabel = "Buscar cidade ou CEP",
                    onClick = onOpenSearch,
                )
                .padding(vertical = 4.dp, horizontal = 2.dp),
        ) {
            Text(
                text = "FuelCompare",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = regionLabel ?: "Escolher região",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(onClick = onOpenSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Buscar cidade ou CEP",
            )
        }
        IconButton(onClick = onLocate, enabled = !isLocating) {
            if (isLocating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = "Buscar postos perto de mim",
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Abrir configurações",
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
private fun FuelFilterRow(
    selectedFuel: FuelType,
    onFuelSelected: (FuelType) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        items(FuelType.entries, key = { it.code }) { fuel ->
            val selected = selectedFuel == fuel
            FilterChip(
                selected = selected,
                onClick = { onFuelSelected(fuel) },
                label = { Text(fuel.shortLabel) },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun HomeControlsRow(
    viewMode: HomeViewMode,
    radiusKm: Int,
    onViewModeSelected: (HomeViewMode) -> Unit,
    onRadiusSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = viewMode == HomeViewMode.MAP,
            onClick = { onViewModeSelected(HomeViewMode.MAP) },
            label = { Text("Mapa") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Spacer(Modifier.size(8.dp))
        FilterChip(
            selected = viewMode == HomeViewMode.LIST,
            onClick = { onViewModeSelected(HomeViewMode.LIST) },
            label = { Text("Lista") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ViewAgenda,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Spacer(Modifier.weight(1f))
        RadiusSelector(radiusKm = radiusKm, onRadiusSelected = onRadiusSelected)
    }
}

@Composable
internal fun RadiusSelector(
    radiusKm: Int,
    onRadiusSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Raio: $radiusKm km")
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (1..10).forEach { option ->
                DropdownMenuItem(
                    text = { Text("$option km") },
                    onClick = {
                        expanded = false
                        onRadiusSelected(option)
                    },
                    leadingIcon = {
                        if (option == radiusKm) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Raio selecionado",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeStatusBanners(
    state: HomeUiState,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    if (state.stations.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.fromCache) {
            OfflineBanner(cachedAt = state.cachedAt, onRetry = onRetry)
        }
        if (state.errorMessage != null) {
            InfoBanner(
                message = state.errorMessage,
                tone = BannerTone.ERROR,
                actionLabel = "Ocultar",
                onAction = onDismissError,
            )
        }
        state.warnings.take(2).forEach { warning ->
            InfoBanner(message = warning, tone = BannerTone.WARNING)
        }
    }
}

@Composable
private fun BestResultCard(
    cheapest: CheapestStation,
    selectedFuel: FuelType,
    canCompare: Boolean,
    canChooseStations: Boolean,
    onOpenStation: (String) -> Unit,
    onCompare: () -> Unit,
    onChooseStations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.extendedColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.cheapestContainer,
        contentColor = colors.onCheapestContainer,
        border = BorderStroke(1.dp, colors.cheapest.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheapestBadge()
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Menor preço de ${selectedFuel.shortLabel}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cheapest.station.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            cheapest.station.distanceKm?.let(Formatters::distance),
                            cheapest.station.address.shortLabel,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    PriceTag(
                        amount = cheapest.price.amount,
                        unitLabel = cheapest.price.unit.symbol,
                        highlight = true,
                    )
                    Text(
                        text = "Preço de ${Formatters.date(cheapest.price.referenceDate)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpenStation(cheapest.station.id) }) {
                    Text("Ver detalhes", color = colors.onCheapestContainer)
                }
                if (canCompare) {
                    TextButton(onClick = onCompare) {
                        Text("Comparar os 2 mais baratos", color = colors.onCheapestContainer)
                    }
                }
            }
            if (canChooseStations) {
                TextButton(
                    onClick = onChooseStations,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text("Escolher postos para comparar", color = colors.onCheapestContainer)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MapPane(
    state: HomeUiState,
    onOpenStation: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onMapMoved: (Double, Double) -> Unit,
) {
    StationMap(
        stations = state.sortedStations,
        cheapestStationId = state.cheapest?.station?.id,
        initialCenter = state.queryCenter,
        focus = state.mapFocus,
        isRefreshing = state.isRefreshing || state.isLocating,
        lastUpdated = state.lastUpdated,
        fromCache = state.fromCache,
        onOpenStation = onOpenStation,
        onRefresh = onRefresh,
        onLocate = onLocate,
        onMapMoved = onMapMoved,
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationList(
    state: HomeUiState,
    onSortSelected: (SortMode) -> Unit,
    onOpenStation: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "list-header") {
                ListHeader(
                    count = state.sortedStations.size,
                    lastUpdated = state.lastUpdated,
                    sortMode = state.sortMode,
                    onSortSelected = onSortSelected,
                )
            }
            items(items = state.sortedStations, key = { it.id }) { station ->
                StationCard(
                    station = station,
                    selectedFuel = state.selectedFuel,
                    isCheapest = state.cheapest?.station?.id == station.id,
                    onClick = { onOpenStation(station.id) },
                )
            }
            item(key = "regional-reference") {
                RegionalReferenceCard(
                    regionalPrices = state.regionalPrices,
                    selectedFuel = state.selectedFuel,
                )
            }
            item(key = "sources") {
                SourcesFooter(sources = state.sources)
            }
            item(key = "licenses") {
                Text(
                    text = "Preços individuais: ANP, quando disponíveis. Postos e mapa: " +
                        "© OpenStreetMap contributors (ODbL). Médias municipais são " +
                        "referências e não preços de um posto específico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ListHeader(
    count: Int,
    lastUpdated: java.time.Instant?,
    sortMode: SortMode,
    onSortSelected: (SortMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count == 1) "1 posto próximo" else "$count postos próximos",
                style = MaterialTheme.typography.titleMedium,
            )
            LastUpdatedText(lastUpdated = lastUpdated)
        }
        SortMenu(sortMode = sortMode, onSortSelected = onSortSelected)
    }
}

@Composable
private fun SortMenu(
    sortMode: SortMode,
    onSortSelected: (SortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text("Ordenar: ${sortMode.label()}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SortMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        expanded = false
                        onSortSelected(option)
                    },
                    leadingIcon = {
                        if (option == sortMode) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Ordenação selecionada",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun SortMode.label(): String = when (this) {
    SortMode.PRICE -> "Preço"
    SortMode.DISTANCE -> "Distância"
    SortMode.NAME -> "Nome"
}

@Composable
private fun StationCard(
    station: Station,
    selectedFuel: FuelType,
    isCheapest: Boolean,
    onClick: () -> Unit,
) {
    val price = station.priceFor(selectedFuel)
    val colors = AppTheme.extendedColors
    val stale = price != null && !PriceRules.isFresh(price)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = if (isCheapest) 1.5.dp else 1.dp,
            color = if (isCheapest) {
                colors.cheapest.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                onClickLabel = "Abrir detalhes de ${station.name}",
                onClick = onClick,
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    if (isCheapest) {
                        CheapestBadge()
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    station.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = station.address.shortLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    station.distanceKm?.let { distance ->
                        Text(
                            text = Formatters.distance(distance),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    PriceTag(
                        amount = price?.amount,
                        unitLabel = price?.unit?.symbol ?: selectedFuel.unit.symbol,
                        highlight = isCheapest,
                    )
                    Text(
                        text = when {
                            price == null -> "Sem preço individual"
                            else -> "Individual ANP · ${Formatters.date(price.referenceDate)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (stale) {
                        Spacer(Modifier.height(4.dp))
                        PriceTypeBadge(
                            text = "Preço desatualizado",
                            style = PriceBadgeStyle.WARNING,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RegionalReferenceCard(
    regionalPrices: List<RegionalFuelPrice>,
    selectedFuel: FuelType,
) {
    val regional = regionalPrices.firstOrNull {
        it.fuel == selectedFuel && it.isPlausible
    } ?: return
    val colors = AppTheme.extendedColors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Média municipal de ${regional.fuel.shortLabel}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                PriceTypeBadge(
                    text = "Média municipal",
                    style = PriceBadgeStyle.MUNICIPAL_AVERAGE,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${regional.municipality}/${regional.state} · " +
                    Formatters.currency(regional.amount) + "/${regional.unit.symbol}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Faixa de ${Formatters.currency(regional.minAmount)} a " +
                    "${Formatters.currency(regional.maxAmount)} · " +
                    "${regional.surveyedStations} postos pesquisados",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Semana de ${Formatters.date(regional.referenceStart)} a " +
                    "${Formatters.date(regional.referenceEnd)} · ${regional.source.name}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Referência municipal: não é o preço de um posto específico.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SourcesFooter(sources: List<SourceAttribution>) {
    if (sources.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val uiSources = remember(sources) {
        sources.map { source ->
            SourceUiModel(
                id = source.id,
                name = source.name,
                description = source.description,
                url = source.url,
            )
        }
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SourceAttributionSection(
            sources = uiSources,
            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
        )
    }
}

@Composable
private fun NeedsLocationPane(
    isLocating: Boolean,
    errorMessage: String?,
    onLocate: () -> Unit,
    onOpenSearch: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Onde você quer economizar?",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Escolha sua localização aproximada ou busque por cidade/CEP " +
                "para ver os postos e preços mais próximos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            InfoBanner(
                message = errorMessage,
                tone = BannerTone.WARNING,
                actionLabel = "Ocultar",
                onAction = onDismissError,
            )
        }
        Spacer(Modifier.height(24.dp))
        if (isLocating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Obtendo sua localização aproximada…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(
            text = "Usar minha localização",
            icon = Icons.Outlined.MyLocation,
            enabled = !isLocating,
            onClick = onLocate,
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Buscar por cidade ou CEP",
            icon = Icons.Outlined.Search,
            onClick = onOpenSearch,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Usamos apenas localização aproximada e nunca rastreamos você em " +
                "segundo plano. Você também pode digitar a cidade manualmente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
