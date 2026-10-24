package com.fuelcompare.feature.comparison

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuelcompare.core.common.Formatters
import com.fuelcompare.core.designsystem.component.BannerTone
import com.fuelcompare.core.designsystem.component.CheapestBadge
import com.fuelcompare.core.designsystem.component.EmptyPane
import com.fuelcompare.core.designsystem.component.ErrorPane
import com.fuelcompare.core.designsystem.component.InfoBanner
import com.fuelcompare.core.designsystem.component.LoadingPane
import com.fuelcompare.core.designsystem.component.OfflineBanner
import com.fuelcompare.core.designsystem.component.PriceBadgeStyle
import com.fuelcompare.core.designsystem.component.PriceTag
import com.fuelcompare.core.designsystem.component.PriceTypeBadge
import com.fuelcompare.core.designsystem.component.SectionTitle
import com.fuelcompare.core.designsystem.theme.AppTheme
import com.fuelcompare.domain.model.CheaperSide
import com.fuelcompare.domain.model.ComparisonNote
import com.fuelcompare.domain.model.ComparisonRow
import com.fuelcompare.domain.model.FuelPrice
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.Station
import com.fuelcompare.domain.model.StationComparison
import com.fuelcompare.domain.rules.PriceRules
import java.time.Instant
import kotlin.math.abs

@Composable
fun ComparisonRoute(
    onBack: () -> Unit,
    onOpenStation: (String) -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ComparisonScreen(
        state = state,
        onBack = onBack,
        onOpenStation = onOpenStation,
        onRetry = viewModel::retry,
        onRetryNearby = viewModel::retryNearby,
        onStartNewComparison = viewModel::startNewComparison,
        onSelectSlot = viewModel::selectSlot,
        onClearSlot = viewModel::clearSlot,
        onChooseStation = viewModel::chooseStation,
        onCompare = viewModel::compareSelected,
        onDismissSelectionMessage = viewModel::dismissSelectionMessage,
    )
}

@Composable
private fun ComparisonScreen(
    state: ComparisonUiState,
    onBack: () -> Unit,
    onOpenStation: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryNearby: () -> Unit,
    onStartNewComparison: () -> Unit,
    onSelectSlot: (ComparisonSlot) -> Unit,
    onClearSlot: (ComparisonSlot) -> Unit,
    onChooseStation: (Station) -> Unit,
    onCompare: () -> Unit,
    onDismissSelectionMessage: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    text = "Comparação",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (state.comparison != null) {
                    TextButton(onClick = onStartNewComparison) {
                        Text("Nova comparação")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> LoadingPane(message = "Comparando os preços dos postos…")

                    state.comparison != null -> ComparisonResult(
                        comparison = state.comparison,
                        fromCache = state.fromCache,
                        cachedAt = state.cachedAt,
                        onOpenStation = onOpenStation,
                        onStartNewComparison = onStartNewComparison,
                        onRetry = onRetry,
                    )

                    state.selectionMode -> SelectionContent(
                        state = state,
                        onBack = onBack,
                        onRetryNearby = onRetryNearby,
                        onSelectSlot = onSelectSlot,
                        onClearSlot = onClearSlot,
                        onChooseStation = onChooseStation,
                        onCompare = onCompare,
                        onDismissSelectionMessage = onDismissSelectionMessage,
                    )

                    else -> ErrorPane(
                        message = state.errorMessage
                            ?: "Não foi possível montar a comparação.",
                        onRetry = onRetry,
                        title = "Não foi possível comparar",
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionContent(
    state: ComparisonUiState,
    onBack: () -> Unit,
    onRetryNearby: () -> Unit,
    onSelectSlot: (ComparisonSlot) -> Unit,
    onClearSlot: (ComparisonSlot) -> Unit,
    onChooseStation: (Station) -> Unit,
    onCompare: () -> Unit,
    onDismissSelectionMessage: () -> Unit,
) {
    if (!state.hasRegion && !state.nearbyLoading) {
        EmptyPane(
            title = "Escolha uma região primeiro",
            message = "A comparação manual usa os postos próximos da região salva. " +
                "Volte à tela inicial e busque uma cidade ou use sua localização.",
            icon = Icons.Outlined.LocationOn,
            actionLabel = "Voltar e escolher região",
            onAction = onBack,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.fromCache) {
                OfflineBanner(cachedAt = state.cachedAt, onRetry = onRetryNearby)
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SlotCard(
                    slot = ComparisonSlot.A,
                    station = state.selectedA,
                    isActive = state.activeSlot == ComparisonSlot.A,
                    enabled = !state.isComparing && !state.nearbyLoading,
                    onClick = { onSelectSlot(ComparisonSlot.A) },
                    onClear = { onClearSlot(ComparisonSlot.A) },
                    modifier = Modifier.weight(1f),
                )
                SlotCard(
                    slot = ComparisonSlot.B,
                    station = state.selectedB,
                    isActive = state.activeSlot == ComparisonSlot.B,
                    enabled = !state.isComparing && !state.nearbyLoading,
                    onClick = { onSelectSlot(ComparisonSlot.B) },
                    onClear = { onClearSlot(ComparisonSlot.B) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Toque em um cartão e depois escolha o posto na lista. " +
                    "Somente preços individuais entram na comparação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.selectionMessage != null) {
                InfoBanner(
                    message = state.selectionMessage,
                    tone = BannerTone.WARNING,
                    actionLabel = "Ocultar",
                    onAction = onDismissSelectionMessage,
                )
            }
            if (state.errorMessage != null) {
                InfoBanner(
                    message = state.errorMessage,
                    tone = BannerTone.ERROR,
                    actionLabel = "Tentar novamente",
                    onAction = onCompare,
                )
            }
            if (state.nearbyError != null && state.nearby.isNotEmpty()) {
                InfoBanner(
                    message = state.nearbyError,
                    tone = BannerTone.WARNING,
                    actionLabel = "Atualizar",
                    onAction = onRetryNearby,
                )
            }
            Button(
                onClick = onCompare,
                enabled = state.selectedA != null &&
                    state.selectedB != null &&
                    state.selectedA.id != state.selectedB.id &&
                    !state.isComparing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                if (state.isComparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(if (state.isComparing) "Comparando…" else "Comparar postos")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Postos próximos",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.regionLabel != null) {
                    Text(
                        text = state.regionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onRetryNearby, enabled = !state.nearbyLoading) {
                if (state.nearbyLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text("Atualizar")
            }
        }

        if (state.nearbyLoading && state.nearby.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LoadingPane(
                message = "Carregando postos da região salva…",
                modifier = Modifier.weight(1f),
            )
        } else if (state.nearbyError != null && state.nearby.isEmpty()) {
            ErrorPane(
                message = state.nearbyError,
                onRetry = onRetryNearby,
                title = "Não foi possível carregar os postos",
                modifier = Modifier.weight(1f),
            )
        } else if (state.nearby.isEmpty()) {
            EmptyPane(
                title = "Nenhum posto próximo",
                message = "Não encontramos postos na região salva. Volte à tela inicial, " +
                    "amplie o raio ou escolha outra cidade.",
                icon = Icons.Outlined.LocationOn,
                actionLabel = "Tentar novamente",
                onAction = onRetryNearby,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 2.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.nearby, key = { it.id }) { station ->
                    SelectableStationRow(
                        station = station,
                        activeSlot = state.activeSlot,
                        isSelectedA = state.selectedA?.id == station.id,
                        isSelectedB = state.selectedB?.id == station.id,
                        enabled = !state.isComparing,
                        onClick = { onChooseStation(station) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotCard(
    slot: ComparisonSlot,
    station: Station?,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        station != null -> AppTheme.extendedColors.cheapest.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                enabled = enabled,
                onClickLabel = "Escolher ${slot.label} para a comparação",
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (station != null) {
                    IconButton(
                        onClick = onClear,
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Remover ${slot.label}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                text = station?.name ?: "Selecionar posto",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            station?.distanceKm?.let { distance ->
                Text(
                    text = Formatters.distance(distance),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = when {
                    isActive -> "Escolhendo agora"
                    station != null -> "Toque para substituir"
                    else -> "Toque para escolher"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun SelectableStationRow(
    station: Station,
    activeSlot: ComparisonSlot,
    isSelectedA: Boolean,
    isSelectedB: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = when {
            isSelectedA -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            isSelectedB -> BorderStroke(1.5.dp, AppTheme.extendedColors.cheapest)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                onClickLabel = "Escolher ${station.name} como ${activeSlot.label}",
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isSelectedA) {
                    SlotBadge(label = "A", isPrimary = true)
                }
                if (isSelectedB) {
                    SlotBadge(label = "B", isPrimary = false)
                }
            }
        }
    }
}

@Composable
private fun SlotBadge(
    label: String,
    isPrimary: Boolean,
) {
    val colors = AppTheme.extendedColors
    Surface(
        shape = CircleShape,
        color = if (isPrimary) {
            MaterialTheme.colorScheme.primary
        } else {
            colors.cheapest
        },
        contentColor = if (isPrimary) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            colors.onCheapest
        },
        modifier = Modifier.semantics {
            contentDescription = "Selecionado como Posto $label"
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ComparisonResult(
    comparison: StationComparison,
    fromCache: Boolean,
    cachedAt: Instant?,
    onOpenStation: (String) -> Unit,
    onStartNewComparison: () -> Unit,
    onRetry: () -> Unit,
) {
    val orderedRows = comparison.rows.sortedBy { FuelType.entries.indexOf(it.fuel) }
    val comparableCount = orderedRows.count { it.comparable }
    val sourceNames = orderedRows
        .flatMap { row ->
            listOfNotNull(row.stationA.price?.source?.name, row.stationB.price?.source?.name)
        }
        .distinct()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (fromCache) {
            item(key = "cache") {
                OfflineBanner(cachedAt = cachedAt, onRetry = onRetry)
            }
        }
        item(key = "header") {
            ComparisonHeaderCard(
                comparison = comparison,
                comparableCount = comparableCount,
            )
        }
        item(key = "station-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onOpenStation(comparison.stationA.id) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Ver posto A", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onOpenStation(comparison.stationB.id) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Ver posto B", maxLines = 1)
                }
            }
        }
        item(key = "title") {
            SectionTitle(
                title = "Comparação por combustível",
                subtitle = "A diferença só é calculada quando os dois postos têm preço. " +
                    "Preços antigos aparecem com a data e um aviso.",
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (orderedRows.isEmpty()) {
            item(key = "empty") {
                InfoBanner(
                    title = "Sem preços comparáveis",
                    message = "Nenhum dos dois postos tem preço individual plausível para " +
                        "os combustíveis consultados. Abra os detalhes para conferir as fontes.",
                    tone = BannerTone.WARNING,
                )
            }
        } else {
            items(items = orderedRows, key = { it.fuel.code }) { row ->
                ComparisonFuelCard(row = row)
            }
        }
        item(key = "disclaimer") {
            InfoBanner(
                title = "Comparação transparente",
                message = "Apenas preços individuais dos postos entram no cálculo. Médias " +
                    "municipais não são usadas como preço de posto. Confirme os valores " +
                    "no estabelecimento antes de abastecer.",
                tone = BannerTone.INFO,
            )
        }
        if (sourceNames.isNotEmpty()) {
            item(key = "sources") {
                Text(
                    text = "Fontes dos preços individuais: ${sourceNames.joinToString("; ")}. " +
                        "Localizações: © OpenStreetMap contributors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "new-comparison") {
            OutlinedButton(
                onClick = onStartNewComparison,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Escolher outros postos")
            }
        }
    }
}

@Composable
private fun ComparisonHeaderCard(
    comparison: StationComparison,
    comparableCount: Int,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Comparação de preços",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "POSTO A",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = comparison.stationA.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                    contentDescription = "comparado com",
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "POSTO B",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = comparison.stationB.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (comparableCount == 1) {
                    "1 combustível com preço comparável nos dois postos"
                } else {
                    "$comparableCount combustíveis com preço comparável nos dois postos"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ComparisonFuelCard(row: ComparisonRow) {
    val colors = AppTheme.extendedColors
    val winner = row.cheaper
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.fuel.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ComparisonSummary(row = row)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparisonSideCell(
                    label = "Posto A",
                    stationName = row.stationA.stationName,
                    price = row.stationA.price,
                    isWinner = winner == CheaperSide.A,
                    unitLabel = row.fuel.unit.symbol,
                    modifier = Modifier.weight(1f),
                )
                ComparisonSideCell(
                    label = "Posto B",
                    stationName = row.stationB.stationName,
                    price = row.stationB.price,
                    isWinner = winner == CheaperSide.B,
                    unitLabel = row.fuel.unit.symbol,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!row.comparable) {
                Text(
                    text = when (row.note) {
                        ComparisonNote.ONLY_A ->
                            "Sem diferença confiável: apenas o Posto A tem preço individual."

                        ComparisonNote.ONLY_B ->
                            "Sem diferença confiável: apenas o Posto B tem preço individual."

                        else -> "Sem preço individual suficiente para comparar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ComparisonSummary(row: ComparisonRow) {
    val colors = AppTheme.extendedColors
    val text = when {
        row.comparable && row.cheaper == CheaperSide.TIE -> "Preços iguais"
        row.comparable && row.cheaper == CheaperSide.A -> {
            val difference = row.difference?.let(::abs) ?: 0.0
            "A mais barato · ${Formatters.currency(difference)}/${row.fuel.unit.symbol}"
        }

        row.comparable && row.cheaper == CheaperSide.B -> {
            val difference = row.difference?.let(::abs) ?: 0.0
            "B mais barato · ${Formatters.currency(difference)}/${row.fuel.unit.symbol}"
        }

        row.note == ComparisonNote.ONLY_A -> "Só A tem preço"
        row.note == ComparisonNote.ONLY_B -> "Só B tem preço"
        else -> "Sem comparação"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            row.comparable && row.cheaper == CheaperSide.TIE ->
                MaterialTheme.colorScheme.surfaceVariant

            row.comparable -> colors.cheapestContainer
            else -> colors.warningContainer
        },
        contentColor = when {
            row.comparable && row.cheaper == CheaperSide.TIE ->
                MaterialTheme.colorScheme.onSurfaceVariant

            row.comparable -> colors.onCheapestContainer
            else -> colors.onWarningContainer
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ComparisonSideCell(
    label: String,
    stationName: String,
    price: FuelPrice?,
    isWinner: Boolean,
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.extendedColors
    val stale = price != null && !PriceRules.isFresh(price)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (isWinner) {
            BorderStroke(1.5.dp, colors.cheapest.copy(alpha = 0.7f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stationName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (price != null) {
                PriceTag(
                    amount = price.amount,
                    unitLabel = unitLabel,
                    highlight = isWinner,
                )
                Text(
                    text = "Individual · ${Formatters.date(price.referenceDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stale) {
                    Spacer(Modifier.height(4.dp))
                    PriceTypeBadge(
                        text = "Desatualizado",
                        style = PriceBadgeStyle.WARNING,
                    )
                } else if (isWinner) {
                    Spacer(Modifier.height(4.dp))
                    CheapestBadge()
                }
            } else {
                Text(
                    text = "Sem preço individual",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
