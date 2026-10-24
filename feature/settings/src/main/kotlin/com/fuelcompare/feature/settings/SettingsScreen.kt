package com.fuelcompare.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuelcompare.core.designsystem.component.BannerTone
import com.fuelcompare.core.designsystem.component.BrandWordmark
import com.fuelcompare.core.designsystem.component.InfoBanner
import com.fuelcompare.core.designsystem.component.LabeledValue
import com.fuelcompare.core.designsystem.component.LoadingPane
import com.fuelcompare.core.designsystem.component.SectionTitle
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.ThemeMode
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    appVersion: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    SettingsScreen(
        state = state,
        appVersion = appVersion,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onThemeSelected = viewModel::setThemeMode,
        onFuelSelected = viewModel::setFuel,
        onRadiusSelected = viewModel::setRadiusKm,
        onClearCache = viewModel::clearCache,
        onDismissMessage = viewModel::dismissMessage,
        onOpenUrl = { url ->
            if (runCatching { uriHandler.openUri(url) }.isFailure) {
                scope.launch {
                    snackbarHostState.showSnackbar("Não foi possível abrir o link.")
                }
            }
        },
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    appVersion: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onFuelSelected: (FuelType) -> Unit,
    onRadiusSelected: (Int) -> Unit,
    onClearCache: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var showClearCacheDialog by remember { mutableStateOf(false) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Limpar dados salvos?") },
            text = {
                Text(
                    "Serão removidos os postos, preços e detalhes guardados neste " +
                        "aparelho para uso offline. Suas preferências e a região salva " +
                        "serão mantidas.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        onClearCache()
                    },
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

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
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                    )
                }
                Text(
                    text = "Configurações",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            if (state.isLoading) {
                LoadingPane(message = "Carregando suas preferências…")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.message != null) {
                        item(key = "message") {
                            InfoBanner(
                                message = state.message,
                                tone = if (state.messageIsError) {
                                    BannerTone.ERROR
                                } else {
                                    BannerTone.SUCCESS
                                },
                                actionLabel = "Ocultar",
                                onAction = onDismissMessage,
                            )
                        }
                    }

                    item(key = "appearance") {
                        SettingsSection(
                            title = "Aparência",
                            subtitle = "Escolha como o aplicativo deve ser exibido.",
                        ) {
                            Column(modifier = Modifier.selectableGroup()) {
                                ThemeMode.entries.forEach { mode ->
                                    ThemeOptionRow(
                                        mode = mode,
                                        selected = state.themeMode == mode,
                                        onClick = { onThemeSelected(mode) },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "search-preferences") {
                        SettingsSection(
                            title = "Preferências de busca",
                            subtitle = "Usadas ao abrir a tela inicial e ao escolher postos " +
                                "para comparar.",
                        ) {
                            FuelSelector(
                                selectedFuel = state.fuel,
                                onFuelSelected = onFuelSelected,
                            )
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            )
                            Spacer(Modifier.height(16.dp))
                            RadiusSelector(
                                radiusKm = state.radiusKm,
                                onRadiusSelected = onRadiusSelected,
                            )
                        }
                    }

                    item(key = "data-privacy") {
                        SettingsSection(
                            title = "Dados e privacidade",
                            subtitle = "Controle as cópias locais e entenda o uso da localização.",
                        ) {
                            LabeledValue(
                                label = "Região salva",
                                value = state.regionLabel ?: "Nenhuma região salva",
                            )
                            Text(
                                text = "Para trocar a região, use o seletor no topo da tela inicial.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Cache local",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Postos e preços consultados ficam salvos apenas neste " +
                                    "aparelho para consulta offline. Limpar não altera suas " +
                                    "preferências nem a região.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                            )
                            OutlinedButton(
                                onClick = { showClearCacheDialog = true },
                                enabled = !state.isClearingCache,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                if (state.isClearingCache) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(if (state.isClearingCache) "Limpando…" else "Limpar dados salvos")
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "A localização é aproximada, sem GPS de alta " +
                                        "precisão, e só é usada quando você toca no botão de " +
                                        "localizar. O app não rastreia você em segundo plano. " +
                                        "Não há cadastro nem anúncios.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item(key = "about") {
                        SettingsSection(
                            title = "Sobre",
                            subtitle = "Transparência sobre o aplicativo e os dados.",
                        ) {
                            BrandWordmark(showTagline = false)
                            Spacer(Modifier.height(8.dp))
                            LabeledValue(
                                label = "Versão",
                                value = appVersion.ifBlank { "1.0.0" },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "O FuelCompare cruza postos do OpenStreetMap com " +
                                    "preços individuais do levantamento semanal da ANP. Médias " +
                                    "municipais aparecem apenas como referência e nunca são " +
                                    "atribuídas a um posto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            SourceLink(
                                label = "Levantamento de Preços — ANP",
                                onClick = {
                                    onOpenUrl(
                                        "https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/" +
                                            "precos/levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas",
                                    )
                                },
                            )
                            SourceLink(
                                label = "OpenStreetMap — direitos autorais e ODbL",
                                onClick = {
                                    onOpenUrl("https://www.openstreetmap.org/copyright")
                                },
                            )
                            SourceLink(
                                label = "Nominatim — geocodificação",
                                onClick = {
                                    onOpenUrl("https://nominatim.org/release-docs/latest/api/Overview/")
                                },
                            )
                        }
                    }

                    item(key = "footer") {
                        Text(
                            text = "FuelCompare · preços podem variar. Confirme o valor no " +
                                "posto antes de abastecer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(title = title, subtitle = subtitle)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = mode.label(),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = mode.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Seguir o sistema"
    ThemeMode.LIGHT -> "Tema claro"
    ThemeMode.DARK -> "Tema escuro"
}

private fun ThemeMode.description(): String = when (this) {
    ThemeMode.SYSTEM -> "Alterna automaticamente com o tema do aparelho."
    ThemeMode.LIGHT -> "Fundo claro em todo o aplicativo."
    ThemeMode.DARK -> "Fundo escuro para ambientes com pouca luz."
}

@Composable
private fun FuelSelector(
    selectedFuel: FuelType,
    onFuelSelected: (FuelType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = "Combustível padrão",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "Filtro inicial exibido no mapa e na lista.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = selectedFuel.label,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FuelType.entries.forEach { fuel ->
                DropdownMenuItem(
                    text = { Text(fuel.label) },
                    onClick = {
                        expanded = false
                        onFuelSelected(fuel)
                    },
                    leadingIcon = {
                        if (fuel == selectedFuel) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selecionado",
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
private fun RadiusSelector(
    radiusKm: Int,
    onRadiusSelected: (Int) -> Unit,
) {
    var sliderValue by remember(radiusKm) { mutableFloatStateOf(radiusKm.toFloat()) }
    Text(
        text = "Raio de busca: ${sliderValue.roundToInt()} km",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "Entre 1 e 10 km. Você também pode ajustar na tela inicial.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = 1f..10f,
        steps = 8,
        onValueChangeFinished = { onRadiusSelected(sliderValue.roundToInt()) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Composable
private fun SourceLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}
