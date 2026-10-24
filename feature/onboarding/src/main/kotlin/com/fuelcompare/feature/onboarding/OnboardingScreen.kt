package com.fuelcompare.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuelcompare.core.designsystem.component.AppLogo
import com.fuelcompare.core.designsystem.component.BannerTone
import com.fuelcompare.core.designsystem.component.BrandWordmark
import com.fuelcompare.core.designsystem.component.InfoBanner
import com.fuelcompare.core.designsystem.component.PrimaryButton
import com.fuelcompare.domain.model.LocationSuggestion
import kotlinx.coroutines.delay

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished()
    }
    OnboardingScreen(
        state = state,
        onContinue = viewModel::goToLocationStep,
        onBack = viewModel::goBack,
        onSkip = viewModel::skipLocation,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onSuggestionClick = viewModel::selectSuggestion,
        onUseLocation = viewModel::useDeviceLocation,
        onPermissionDenied = viewModel::onPermissionDenied,
        onClearMessage = viewModel::clearMessage,
    )
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionClick: (LocationSuggestion) -> Unit,
    onUseLocation: () -> Unit,
    onPermissionDenied: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onUseLocation() else onPermissionDenied()
    }
    val requestLocation = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onUseLocation() else permissionLauncher.launch(
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    BackHandler(enabled = state.step > 0 && !state.isSaving) { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OnboardingTopBar(
                step = state.step,
                showSkip = state.step == 0,
                skipEnabled = !state.isSaving,
                onBack = onBack,
                onSkip = onSkip,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                if (state.step == 0) {
                    IntroContent(onContinue = onContinue)
                } else {
                    LocationContent(
                        state = state,
                        isBusy = state.isLocating || state.isSaving,
                        onQueryChange = onQueryChange,
                        onSearch = onSearch,
                        onSuggestionClick = onSuggestionClick,
                        onRequestLocation = requestLocation,
                        onSkip = onSkip,
                        onClearMessage = onClearMessage,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    step: Int,
    showSkip: Boolean,
    skipEnabled: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Voltar",
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
            AppLogo(size = 36.dp)
        }
        Spacer(Modifier.weight(1f))
        if (showSkip) {
            TextButton(onClick = onSkip, enabled = skipEnabled) {
                Text("Pular")
            }
        }
    }
}

@Composable
private fun IntroContent(onContinue: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    BrandWordmark()
    Spacer(Modifier.height(36.dp))
    Text(
        text = "Compare o preço do combustível perto de você",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Encontre postos no mapa, veja os preços oficiais da ANP e descubra " +
            "onde vale mais a pena abastecer.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))

    FeatureRow(
        icon = Icons.Outlined.Map,
        title = "Mapa e lista de postos",
        description = "Ordene por preço, distância ou nome e veja o menor preço.",
    )
    Spacer(Modifier.height(20.dp))
    FeatureRow(
        icon = Icons.Outlined.Verified,
        title = "Preços oficiais com data",
        description = "Dados individuais do levantamento semanal da ANP.",
    )
    Spacer(Modifier.height(20.dp))
    FeatureRow(
        icon = Icons.Outlined.CloudOff,
        title = "Funciona com dados salvos",
        description = "Sem internet, você vê a última busca identificada como cache.",
    )
    Spacer(Modifier.height(28.dp))
    InfoBanner(
        title = "Transparência de preços",
        message = "Médias municipais aparecem apenas como referência e nunca são " +
            "atribuídas a um posto específico.",
        tone = BannerTone.INFO,
    )
    Spacer(Modifier.height(28.dp))
    PrimaryButton(
        text = "Continuar",
        icon = Icons.AutoMirrored.Outlined.ArrowForward,
        onClick = onContinue,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Sem cadastro. Suas preferências ficam neste aparelho.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocationContent(
    state: OnboardingUiState,
    isBusy: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionClick: (LocationSuggestion) -> Unit,
    onRequestLocation: () -> Unit,
    onSkip: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val suggestionsBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(state.suggestions) {
        if (state.suggestions.isNotEmpty()) {
            delay(150)
            suggestionsBringIntoView.bringIntoView()
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        text = "Onde você está?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Use sua localização aproximada ou busque por cidade/CEP. " +
            "O app não rastreia você em segundo plano.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null)
                Column {
                    Text(
                        text = "Localização aproximada",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Só pedimos a permissão aproximada. Nada é rastreado em segundo plano.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (state.isLocating) "Obtendo localização…" else "Usar minha localização",
                icon = Icons.Outlined.MyLocation,
                enabled = !isBusy,
                onClick = onRequestLocation,
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        )
        Text(
            text = "ou busque",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        )
    }
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Cidade, bairro ou CEP") },
        placeholder = { Text("Ex.: Osasco ou 01001-000") },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Limpar busca")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                onSearch()
            },
        ),
        enabled = !state.isSaving,
        supportingText = {
            Text("Digite ao menos 2 caracteres. CEPs são convertidos automaticamente.")
        },
    )

    if (state.isSearching) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    if (state.message != null) {
        Spacer(Modifier.height(12.dp))
        InfoBanner(
            message = state.message,
            tone = BannerTone.WARNING,
            actionLabel = "Ocultar",
            onAction = onClearMessage,
        )
    }

    if (state.suggestions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.bringIntoViewRequester(suggestionsBringIntoView),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.suggestions.forEach { suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    enabled = !state.isSaving,
                    onClick = { onSuggestionClick(suggestion) },
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    TextButton(
        onClick = onSkip,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isSaving) "Salvando…" else "Continuar sem localização")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Você poderá escolher a região depois, na tela inicial.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: LocationSuggestion,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${suggestion.municipality}/${suggestion.state}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = suggestion.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
