package com.dasomaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dasomaps.app.ui.navigation.DasoMapsNavHost
import com.dasomaps.app.ui.navigation.Screen
import com.dasomaps.app.ui.theme.DasoMapsTheme
import timber.log.Timber

/**
 * Actividad principal de DasoMaps.
 * 
 * Punto de entrada de la aplicación. Configura el tema, la navegación
 * y la barra de navegación inferior.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity creada")
        
        setContent {
            DasoMapsTheme {
                DasoMapsApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("MainActivity paused")
    }
}

/**
 * Composable principal de la aplicación.
 * Gestiona la navegación y la estructura general de la UI.
 */
@Composable
fun DasoMapsApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // Elemento de navegación: Mapa
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Mapa") },
                    selected = currentDestination?.hierarchy?.any { it.route == Screen.Map.route } == true,
                    onClick = {
                        navController.navigate(Screen.Map.route) {
                            // Evitar múltiples copias de la misma pantalla
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Evitar múltiples copias del mismo destino
                            launchSingleTop = true
                            // Restaurar estado al reseleccionar
                            restoreState = true
                        }
                    }
                )

                // Elemento de navegación: Capas
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                    label = { Text("Capas") },
                    selected = currentDestination?.hierarchy?.any { it.route == Screen.Layers.route } == true,
                    onClick = {
                        navController.navigate(Screen.Layers.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                // Elemento de navegación: Geometrías
                NavigationBarItem(
                    icon = { Icon(Icons.Default.EditLocation, contentDescription = null) },
                    label = { Text("Geometrías") },
                    selected = currentDestination?.hierarchy?.any { it.route == Screen.Geometries.route } == true,
                    onClick = {
                        navController.navigate(Screen.Geometries.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                // Elemento de navegación: Configuración
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Config") },
                    selected = currentDestination?.hierarchy?.any { it.route == Screen.Settings.route } == true,
                    onClick = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        DasoMapsNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
