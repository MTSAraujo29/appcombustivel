package com.fuelcompare.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fuelcompare.app.BuildConfig
import com.fuelcompare.feature.comparison.ComparisonRoute
import com.fuelcompare.feature.home.HomeRoute
import com.fuelcompare.feature.onboarding.OnboardingRoute
import com.fuelcompare.feature.settings.SettingsRoute
import com.fuelcompare.feature.stationdetails.StationDetailsRoute

@Composable
fun FuelCompareNavHost(
    initialOnboardingCompleted: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    // A rota inicial é definida apenas na primeira composição após o carregamento
    // das preferências; mudanças de tema ou conclusão do onboarding não recriam o grafo.
    val startDestination = remember {
        if (initialOnboardingCompleted) Routes.HOME else Routes.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeRoute(
                onOpenStation = { stationId ->
                    navController.navigate(Routes.stationDetails(stationId))
                },
                onCompareStations = { stationA, stationB ->
                    navController.navigate(Routes.comparison(stationA, stationB))
                },
                onOpenManualComparison = {
                    navController.navigate(Routes.comparison())
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
            )
        }

        composable(
            route = Routes.STATION_DETAILS,
            arguments = listOf(
                navArgument(Routes.ARG_STATION_ID) { type = NavType.StringType },
            ),
        ) {
            StationDetailsRoute(
                onBack = { navController.navigateUp() },
            )
        }

        composable(
            route = Routes.COMPARISON,
            arguments = listOf(
                navArgument(Routes.ARG_STATION_A) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.ARG_STATION_B) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ComparisonRoute(
                onBack = { navController.navigateUp() },
                onOpenStation = { stationId ->
                    navController.navigate(Routes.stationDetails(stationId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                appVersion = BuildConfig.VERSION_NAME,
                onBack = { navController.navigateUp() },
            )
        }
    }
}
