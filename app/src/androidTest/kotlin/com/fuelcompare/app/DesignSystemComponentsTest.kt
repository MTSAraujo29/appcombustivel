package com.fuelcompare.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuelcompare.core.designsystem.component.InfoBanner
import com.fuelcompare.core.designsystem.component.OfflineBanner
import com.fuelcompare.core.designsystem.component.PriceTag
import com.fuelcompare.core.designsystem.theme.FuelCompareTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun offlineBannerIdentificaCacheEPermiteAtualizar() {
        var retried = false
        compose.setContent {
            FuelCompareTheme(darkTheme = false) {
                OfflineBanner(
                    cachedAt = Instant.parse("2026-10-24T12:00:00Z"),
                    onRetry = { retried = true },
                )
            }
        }

        compose.onNodeWithText("Exibindo dados salvos no aparelho").assertIsDisplayed()
        compose.onNodeWithText("Atualizar").performClick()
        assertTrue(retried)
    }

    @Test
    fun priceTagFormataBRLESinalizaAusenciaDePreco() {
        compose.setContent {
            FuelCompareTheme(darkTheme = false) {
                Column {
                    PriceTag(amount = 5.99, unitLabel = "L")
                    PriceTag(amount = null, unitLabel = "L")
                }
            }
        }

        compose.onNodeWithText("5,99", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sem preço").assertIsDisplayed()
    }

    @Test
    fun infoBannerExibeAcaoEInvocarCallback() {
        var dismissed = false
        compose.setContent {
            FuelCompareTheme(darkTheme = true) {
                InfoBanner(
                    message = "Mensagem de teste",
                    actionLabel = "Ocultar",
                    onAction = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Mensagem de teste").assertIsDisplayed()
        compose.onNodeWithText("Ocultar").performClick()
        assertTrue(dismissed)
    }
}
