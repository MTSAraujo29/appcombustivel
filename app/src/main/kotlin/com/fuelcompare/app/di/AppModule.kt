package com.fuelcompare.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.fuelcompare.app.BuildConfig
import com.fuelcompare.core.common.DefaultDispatchersProvider
import com.fuelcompare.core.common.DispatchersProvider
import com.fuelcompare.core.database.FuelCompareDatabase
import com.fuelcompare.core.database.dao.DetailsSnapshotDao
import com.fuelcompare.core.database.dao.NearbySnapshotDao
import com.fuelcompare.core.network.ApiClientFactory
import com.fuelcompare.core.network.FuelCompareApi
import com.fuelcompare.data.location.AndroidLocationProvider
import com.fuelcompare.data.preferences.PreferencesRepositoryImpl
import com.fuelcompare.data.repository.GeocodingRepositoryImpl
import com.fuelcompare.data.repository.StationRepositoryImpl
import com.fuelcompare.domain.repository.GeocodingRepository
import com.fuelcompare.domain.repository.LocationProvider
import com.fuelcompare.domain.repository.PreferencesRepository
import com.fuelcompare.domain.repository.StationRepository
import com.fuelcompare.domain.usecase.CompareStationsUseCase
import com.fuelcompare.domain.usecase.GetCheapestStationUseCase
import com.fuelcompare.domain.usecase.GetNearbyStationsUseCase
import com.fuelcompare.domain.usecase.GetStationDetailsUseCase
import com.fuelcompare.domain.usecase.ResolveDeviceLocationUseCase
import com.fuelcompare.domain.usecase.ReverseGeocodeLocationUseCase
import com.fuelcompare.domain.usecase.SearchLocationsUseCase
import com.fuelcompare.domain.usecase.SortStationsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatchers(): DispatchersProvider = DefaultDispatchersProvider()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("fuelcompare_preferences") },
    )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FuelCompareDatabase = Room.databaseBuilder(
        context,
        FuelCompareDatabase::class.java,
        FuelCompareDatabase.NAME,
    ).build()

    @Provides
    fun provideNearbyDao(database: FuelCompareDatabase): NearbySnapshotDao =
        database.nearbySnapshotDao()

    @Provides
    fun provideDetailsDao(database: FuelCompareDatabase): DetailsSnapshotDao =
        database.detailsSnapshotDao()

    @Provides
    @Singleton
    fun provideApi(): FuelCompareApi = ApiClientFactory.create(
        baseUrl = BuildConfig.API_BASE_URL,
        debug = BuildConfig.DEBUG,
        userAgent = "FuelCompare/${BuildConfig.VERSION_NAME} (Android)",
    )

    @Provides
    @Singleton
    fun provideStationRepository(
        api: FuelCompareApi,
        nearbyDao: NearbySnapshotDao,
        detailsDao: DetailsSnapshotDao,
        dispatchers: DispatchersProvider,
    ): StationRepository = StationRepositoryImpl(
        api = api,
        nearbyDao = nearbyDao,
        detailsDao = detailsDao,
        dispatchers = dispatchers,
    )

    @Provides
    @Singleton
    fun provideGeocodingRepository(
        api: FuelCompareApi,
        dispatchers: DispatchersProvider,
    ): GeocodingRepository = GeocodingRepositoryImpl(api, dispatchers)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        dataStore: DataStore<Preferences>,
    ): PreferencesRepository = PreferencesRepositoryImpl(dataStore)

    @Provides
    @Singleton
    fun provideLocationProvider(
        @ApplicationContext context: Context,
    ): LocationProvider = AndroidLocationProvider(context)

    @Provides
    fun provideGetNearbyStations(
        repository: StationRepository,
    ): GetNearbyStationsUseCase = GetNearbyStationsUseCase(repository)

    @Provides
    fun provideGetStationDetails(
        repository: StationRepository,
    ): GetStationDetailsUseCase = GetStationDetailsUseCase(repository)

    @Provides
    fun provideCompareStations(
        repository: StationRepository,
    ): CompareStationsUseCase = CompareStationsUseCase(repository)

    @Provides
    fun provideGetCheapestStation(): GetCheapestStationUseCase = GetCheapestStationUseCase()

    @Provides
    fun provideSortStations(): SortStationsUseCase = SortStationsUseCase()

    @Provides
    fun provideSearchLocations(
        repository: GeocodingRepository,
    ): SearchLocationsUseCase = SearchLocationsUseCase(repository)

    @Provides
    fun provideReverseGeocode(
        repository: GeocodingRepository,
    ): ReverseGeocodeLocationUseCase = ReverseGeocodeLocationUseCase(repository)

    @Provides
    fun provideResolveDeviceLocation(
        locationProvider: LocationProvider,
    ): ResolveDeviceLocationUseCase = ResolveDeviceLocationUseCase(locationProvider)
}
