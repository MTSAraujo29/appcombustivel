package com.fuelcompare.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelcompare.core.common.Formatters
import com.fuelcompare.core.designsystem.theme.AppTheme
import java.time.Instant

@Composable
fun LoadingPane(
    modifier: Modifier = Modifier,
    message: String = "Carregando postos próximos…",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Não foi possível carregar",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) {
            Text("Tentar novamente")
        }
    }
}

@Composable
fun EmptyPane(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    cachedAt: Instant? = null,
    onRetry: (() -> Unit)? = null,
) {
    val colors = AppTheme.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exibindo dados salvos no aparelho",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = if (cachedAt != null) {
                        "Salvos em ${Formatters.shortDateTime(cachedAt)}. Podem estar desatualizados."
                    } else {
                        "Estes dados podem estar desatualizados."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("Atualizar", color = colors.onWarningContainer)
                }
            }
        }
    }
}

enum class BannerTone { INFO, WARNING, SUCCESS, ERROR }

@Composable
fun InfoBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: BannerTone = BannerTone.INFO,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.extendedColors
    val container: Color = when (tone) {
        BannerTone.INFO -> colors.infoContainer
        BannerTone.WARNING -> colors.warningContainer
        BannerTone.SUCCESS -> colors.cheapestContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content: Color = when (tone) {
        BannerTone.INFO -> colors.onInfoContainer
        BannerTone.WARNING -> colors.onWarningContainer
        BannerTone.SUCCESS -> colors.onCheapestContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    val resolvedIcon = icon ?: when (tone) {
        BannerTone.INFO -> Icons.Outlined.Info
        BannerTone.WARNING -> Icons.Outlined.WarningAmber
        BannerTone.SUCCESS -> Icons.Outlined.Info
        BannerTone.ERROR -> Icons.Outlined.ErrorOutline
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = resolvedIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        Text(actionLabel, color = content)
                    }
                }
            }
        }
    }
}

@Composable
fun WarningsList(
    warnings: List<String>,
    modifier: Modifier = Modifier,
) {
    if (warnings.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        warnings.forEach { warning ->
            InfoBanner(message = warning, tone = BannerTone.WARNING)
        }
    }
}

@Composable
fun LastUpdatedText(
    lastUpdated: Instant?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = lastUpdated?.let(Formatters::lastUpdated)
            ?: "Data de atualização indisponível",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
