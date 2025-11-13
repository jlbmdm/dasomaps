package com.dasomaps.app.utils

/**
 * Objeto que contiene constantes utilizadas en toda la aplicación.
 */
object Constants {

    /**
     * Constantes relacionadas con el mapa.
     */
    object Map {
        const val MIN_ZOOM = 3.0
        const val MAX_ZOOM = 22.0
    }

    /**
     * Constantes relacionadas con los directorios de la aplicación.
     */
    object Directories {
        /**
         * Nombre del subdirectorio donde se almacenarán los archivos MBTiles internos.
         */
        const val FILES_DIR_NAME = "mbtiles"
    }
    
    /**
     * Constantes para SharedPreferences
     */
    object Preferences {
        const val PREFS_NAME = "DasoMaps_prefs"
        const val KEY_LAST_LATITUDE = "last_latitude"
        const val KEY_LAST_LONGITUDE = "last_longitude"
        const val KEY_LAST_ZOOM = "last_zoom"
    }
}
