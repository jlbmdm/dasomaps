package com.dasomaps.app.ui.map

import com.dasomaps.app.data.model.Layer
import org.osmdroid.util.GeoPoint

/**
 * Data class que representa el estado de la interfaz de usuario para la pantalla del mapa.
 *
 * @property zoom Nivel de zoom actual del mapa.
 * @property center Centro geográfico actual del mapa.
 * @property isMyLocationEnabled Si la función de "mi ubicación" está activa.
 * @property visibleLayers Lista de capas que deben mostrarse en el mapa.
 * @property errorMessage Mensaje de error para mostrar en un Snackbar, o null si no hay error.
 * @property isLoading Si se está realizando una operación de carga.
 */
data class MapUiState(
    val zoom: Double = 15.0,
    val center: GeoPoint = GeoPoint(40.416775, -3.703790), // Centro inicial en Madrid
    val isMyLocationEnabled: Boolean = false,
    val visibleLayers: List<Layer> = emptyList(),
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)
