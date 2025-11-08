package com.dasomaps.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dasomaps.app.ui.map.MapScreen
import com.dasomaps.app.ui.layers.LayersScreen
// Importaciones necesarias para PlaceholderScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment

/**
 * Sistema de navegación principal de la aplicación.
 * 
 * Define todas las rutas y pantallas disponibles.
 *
 * @param navController Controlador de navegación
 * @param modifier Modificador opcional
 */
@Composable
fun DasoMapsNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Map.route,
        modifier = modifier
    ) {
        // Pantalla principal del mapa
        composable(route = Screen.Map.route) {
            MapScreen()
        }
        
        // Pantalla de gestión de capas
        composable(route = Screen.Layers.route) {
            LayersScreen()
        }
        
        // Pantalla de geometrías capturadas (por implementar)
        composable(route = Screen.Geometries.route) {
            PlaceholderScreen(title = "Geometrías")
        }
        
        // Pantalla de configuración (por implementar)
        composable(route = Screen.Settings.route) {
            PlaceholderScreen(title = "Configuración")
        }
    }
}

/**
 * Pantalla temporal de placeholder para rutas no implementadas.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = "$title - Por implementar",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )
    }
}

