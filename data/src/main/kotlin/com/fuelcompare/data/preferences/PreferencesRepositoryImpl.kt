package com.fuelcompare.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fuelcompare.domain.model.FuelType
import com.fuelcompare.domain.model.SavedRegion
import com.fuelcompare.domain.model.ThemeMode
import com.fuelcompare.domain.model.UserPreferences
import com.fuelcompare.domain.repository.PreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toDomain)

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setFuel(fuel: FuelType) {
        dataStore.edit { it[Keys.FUEL] = fuel.code }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name.lowercase() }
    }

    override suspend fun setRadiusKm(radiusKm: Int) {
        dataStore.edit { it[Keys.RADIUS_KM] = radiusKm.coerceIn(1, 10) }
    }

    override suspend fun setRegion(region: SavedRegion) {
        dataStore.edit {
            it[Keys.REGION_MUNICIPALITY] = region.municipality
            it[Keys.REGION_STATE] = region.state
            it[Keys.REGION_LATITUDE] = region.latitude
            it[Keys.REGION_LONGITUDE] = region.longitude
        }
    }

    override suspend fun clearRegion() {
        dataStore.edit {
            it.remove(Keys.REGION_MUNICIPALITY)
            it.remove(Keys.REGION_STATE)
            it.remove(Keys.REGION_LATITUDE)
            it.remove(Keys.REGION_LONGITUDE)
        }
    }

    private fun toDomain(preferences: Preferences): UserPreferences {
        val municipality = preferences[Keys.REGION_MUNICIPALITY]
        val state = preferences[Keys.REGION_STATE]
        val latitude = preferences[Keys.REGION_LATITUDE]
        val longitude = preferences[Keys.REGION_LONGITUDE]
        val region = if (
            !municipality.isNullOrBlank() &&
            !state.isNullOrBlank() &&
            latitude != null &&
            longitude != null
        ) {
            SavedRegion(municipality, state, latitude, longitude)
        } else {
            null
        }

        return UserPreferences(
            onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
            fuel = FuelType.fromCode(preferences[Keys.FUEL].orEmpty()) ?: FuelType.GASOLINE,
            themeMode = parseTheme(preferences[Keys.THEME]),
            radiusKm = (preferences[Keys.RADIUS_KM] ?: 5).coerceIn(1, 10),
            region = region,
        )
    }

    private fun parseTheme(value: String?): ThemeMode = when (value) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FUEL = stringPreferencesKey("default_fuel")
        val THEME = stringPreferencesKey("theme_mode")
        val RADIUS_KM = intPreferencesKey("radius_km")
        val REGION_MUNICIPALITY = stringPreferencesKey("region_municipality")
        val REGION_STATE = stringPreferencesKey("region_state")
        val REGION_LATITUDE = doublePreferencesKey("region_latitude")
        val REGION_LONGITUDE = doublePreferencesKey("region_longitude")
    }
}
