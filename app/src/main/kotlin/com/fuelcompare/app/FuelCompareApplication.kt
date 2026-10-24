package com.fuelcompare.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import org.osmdroid.config.Configuration

@HiltAndroidApp
class FuelCompareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureMapCache()
    }

    /**
     * O osmdroid usa os tiles oficiais do OpenStreetMap, sem chave paga.
     * O cache fica restrito ao diretório privado do app e o User-Agent
     * identifica o cliente, conforme as políticas de uso dos tiles.
     */
    private fun configureMapCache() {
        val preferences = getSharedPreferences(OSMDROID_PREFERENCES, Context.MODE_PRIVATE)
        Configuration.getInstance().apply {
            load(this@FuelCompareApplication, preferences)
            userAgentValue = "FuelCompare/${BuildConfig.VERSION_NAME} (${packageName})"
            val basePath = File(cacheDir, "osmdroid")
            osmdroidBasePath = basePath
            osmdroidTileCache = File(basePath, "tiles")
            tileFileSystemCacheMaxBytes = 100L * 1024L * 1024L
            tileFileSystemCacheTrimBytes = 80L * 1024L * 1024L
        }
    }

    private companion object {
        const val OSMDROID_PREFERENCES = "fuelcompare_osmdroid"
    }
}
