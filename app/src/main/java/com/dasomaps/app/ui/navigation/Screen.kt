package com.dasomaps.app.ui.navigation

/**
 * Rutas de navegación de la aplicación.
 */
sealed class Screen(val route: String) {
    /**
     * Pantalla principal del mapa.
     */
    object Map : Screen("map")
    
    /**
     * Pantalla de gestión de capas.
     */
    object Layers : Screen("layers")
    
    /**
     * Pantalla de geometrías capturadas.
     */
    object Geometries : Screen("geometries")
    
    /**
     * Pantalla de configuración.
     */
    object Settings : Screen("settings")
}
