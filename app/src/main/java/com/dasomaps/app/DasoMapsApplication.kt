package com.dasomaps.app

import android.app.Application
import com.dasomaps.app.BuildConfig
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.io.File

/**
 * Clase Application principal de DasoMaps.
 * Se encarga de la inicialización global de la aplicación.
 */
class DasoMapsApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar Timber para logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Configurar osmdroid
        initializeOsmdroid()

        Timber.d("DasoMaps Application iniciada")
    }

    /**
     * Configura osmdroid con los ajustes necesarios para la aplicación.
     */
    private fun initializeOsmdroid() {
        // Configurar el directorio de cache de osmdroid
        val osmConfig = Configuration.getInstance()

        /*
        // IMPORTANTE: Establecer un User-Agent de navegador para evitar bloqueos
        // Este da problemas con OSM: "Access blocked. App is not flowwoing the tile usage policy of OpenStreetMap's volunteer-run servers: osm.wiki/Blocked".
        osmConfig.userAgentValue = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"
         */
        // IMPORTANTE: Establecer el User-Agent para cumplir las políticas de uso
        osmConfig.userAgentValue = "DasoMaps"

        // Establecer directorios de cache
        val basePath = File(cacheDir.absolutePath, "osmdroid")
        osmConfig.osmdroidBasePath = basePath

        val tileCache = File(osmConfig.osmdroidBasePath, "tiles")
        osmConfig.osmdroidTileCache = tileCache

        // Configurar el tamaño del cache (200 MB)
        osmConfig.tileFileSystemCacheMaxBytes = 200L * 1024 * 1024

        // Habilitar conexión de red para descargar tiles
        osmConfig.isDebugMode = BuildConfig.DEBUG
//        // Silenciar los logs de "Tile doesn't exist" para limpiar el logcat
//        osmConfig.isDebugMode = false

        Timber.d("osmdroid configurado. Cache path: ${tileCache.absolutePath}")
    }
}
