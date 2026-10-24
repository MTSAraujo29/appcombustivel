package com.fuelcompare.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuelcompare.core.common.Formatters
import com.fuelcompare.core.designsystem.theme.AppTheme

@Composable
fun PriceTag(
    amount: Double?,
    unitLabel: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    large: Boolean = false,
) {
    if (amount == null || !amount.isFinite() || amount <= 0.0) {
        Text(
            text = "Sem preço",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val color = if (highlight) AppTheme.extendedColors.cheapest else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = Formatters.currency(amount),
            style = if (large) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = "/$unitLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = if (large) 7.dp else 4.dp),
        )
    }
}

@Composable
fun CheapestBadge(modifier: Modifier = Modifier) {
    val colors = AppTheme.extendedColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.cheapestContainer,
        contentColor = colors.onCheapestContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.cheapest,
            )
            Text(
                text = "Menor preço",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

enum class PriceBadgeStyle { INDIVIDUAL, MUNICIPAL_AVERAGE, WARNING, NEUTRAL }

@Composable
fun PriceTypeBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: PriceBadgeStyle = PriceBadgeStyle.NEUTRAL,
) {
    val colors = AppTheme.extendedColors
    val container: Color = when (style) {
        PriceBadgeStyle.INDIVIDUAL -> MaterialTheme.colorScheme.secondaryContainer
        PriceBadgeStyle.MUNICIPAL_AVERAGE -> colors.warningContainer
        PriceBadgeStyle.WARNING -> colors.warningContainer
        PriceBadgeStyle.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content: Color = when (style) {
        PriceBadgeStyle.INDIVIDUAL -> MaterialTheme.colorScheme.onSecondaryContainer
        PriceBadgeStyle.MUNICIPAL_AVERAGE -> colors.onWarningContainer
        PriceBadgeStyle.WARNING -> colors.onWarningContainer
        PriceBadgeStyle.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
