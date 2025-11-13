package com.dasomaps.app.ui.map

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.model.RasterValue
import com.dasomaps.app.data.repository.LayerRepository
import com.dasomaps.app.data.repository.RasterRepository
import com.dasomaps.app.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import timber.log.Timber

class MapViewModel(
    private val context: Context, // Context added
    private val layerRepository: LayerRepository,
    private val rasterRepository: RasterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(Constants.Preferences.PREFS_NAME, Context.MODE_PRIVATE)

    fun setBaseMapType(baseMapType: BaseMapType) {
        _uiState.update { it.copy(baseMapType = baseMapType) }
        // Guardar la selección del usuario
        with(sharedPreferences.edit()) {
            putString("KEY_BASE_MAP_TYPE", baseMapType.name)
            apply()
        }
    }

    fun zoomIn() {
        _uiState.update {
            val newZoom = (it.zoom + 1).coerceAtMost(Constants.Map.MAX_ZOOM)
            it.copy(zoom = newZoom)
        }
    }

    fun zoomOut() {
        _uiState.update {
            val newZoom = (it.zoom - 1).coerceAtLeast(Constants.Map.MIN_ZOOM)
            it.copy(zoom = newZoom)
        }
    }

    fun setMyLocationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isMyLocationEnabled = enabled) }
    }

    fun setFollowingLocation(following: Boolean) {
        _uiState.update {
            it.copy(
                isFollowingLocation = following,
                isMyLocationEnabled = if (following) true else it.isMyLocationEnabled
            )
        }
    }

    fun toggleFollowingLocation() {
        _uiState.update {
            val newFollowing = !it.isFollowingLocation
            it.copy(
                isFollowingLocation = newFollowing,
                isMyLocationEnabled = if (newFollowing) true else it.isMyLocationEnabled
            )
        }
    }

    fun centerOnMyLocation() {
        // Esta función necesitará acceso a la ubicación real del dispositivo.
        // Por ahora, simplemente activamos el estado.
        setMyLocationEnabled(true)
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
        Timber.e("Error mostrado: $message")
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    init {
        // Cargar estado inicial del mapa
        loadMapState()

        // Cargar capas visibles cuando se crea el ViewModel
        viewModelScope.launch {
            layerRepository.getVisibleLayers().collect { layers ->
                _uiState.update { it.copy(visibleLayers = layers) }
                Timber.d("Capas visibles actualizadas: ${layers.size}")
            }
        }
    }

    private fun loadMapState() {
        val lastLatitude = sharedPreferences.getString(Constants.Preferences.KEY_LAST_LATITUDE, _uiState.value.center.latitude.toString())?.toDoubleOrNull() ?: _uiState.value.center.latitude
        val lastLongitude = sharedPreferences.getString(Constants.Preferences.KEY_LAST_LONGITUDE, _uiState.value.center.longitude.toString())?.toDoubleOrNull() ?: _uiState.value.center.longitude
        val lastZoom = sharedPreferences.getString(Constants.Preferences.KEY_LAST_ZOOM, _uiState.value.zoom.toString())?.toDoubleOrNull() ?: _uiState.value.zoom
        val lastBaseMapType = sharedPreferences.getString("KEY_BASE_MAP_TYPE", BaseMapType.STREET.name) ?: BaseMapType.STREET.name

        _uiState.update {
            it.copy(
                center = GeoPoint(lastLatitude, lastLongitude),
                zoom = lastZoom,
                baseMapType = BaseMapType.valueOf(lastBaseMapType)
            )
        }
        Timber.d("Estado del mapa cargado: centro=($lastLatitude, $lastLongitude), zoom=$lastZoom, mapa base=$lastBaseMapType")
    }

    private fun saveMapState() {
        with(sharedPreferences.edit()) {
            // Guardar como String para evitar problemas de precisión con Float
            putString(Constants.Preferences.KEY_LAST_LATITUDE, _uiState.value.center.latitude.toString())
            putString(Constants.Preferences.KEY_LAST_LONGITUDE, _uiState.value.center.longitude.toString())
            putString(Constants.Preferences.KEY_LAST_ZOOM, _uiState.value.zoom.toString())
            apply()
        }
        Timber.d("Estado del mapa guardado: centro=(${_uiState.value.center.latitude}, ${_uiState.value.center.longitude}), zoom=${_uiState.value.zoom}")
    }

    /**
     * Actualiza el centro del mapa, solo si ha cambiado.
     */
    fun updateMapCenter(newCenter: GeoPoint) {
        if (newCenter.latitude != _uiState.value.center.latitude || newCenter.longitude != _uiState.value.center.longitude) {
            _uiState.update { it.copy(center = newCenter) }
            saveMapState()
        }
    }

    /**
     * Actualiza el zoom del mapa, solo si ha cambiado.
     */
    fun updateMapZoom(newZoom: Double) {
        if (newZoom != _uiState.value.zoom) {
            _uiState.update { it.copy(zoom = newZoom) }
            saveMapState()
        }
    }

    /**
     * Centra el mapa en un punto específico.
     */
    fun centerMapAt(point: GeoPoint, zoom: Double) {
        _uiState.update {
            it.copy(center = point, zoom = zoom)
        }
        saveMapState() // También guardar aquí
        Timber.d("Mapa centrado en: ${point.latitude}, ${point.longitude}, zoom: $zoom")
    }

    /**
     * Consulta valores ráster al tocar el mapa.
     *
     * @param latitude Latitud del punto tocado
     * @param longitude Longitud del punto tocado
     */
    fun queryRasterValues(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                Timber.d("Consultando valores ráster en ($latitude, $longitude)")

                // Mostrar indicador de carga
                _uiState.update { it.copy(isQueryingRaster = true) }

                // Obtener capas ráster visibles
                val activeLayers = _uiState.value.visibleLayers.filter { layer ->
                    layer.type == LayerType.RASTER && layer.isVisible
                }

                if (activeLayers.isEmpty()) {
                    Timber.d("No hay capas ráster activas")
                    _uiState.update {
                        it.copy(
                            isQueryingRaster = false,
                            showRasterInfoPanel = false
                        )
                    }
                    return@launch
                }

                // Consultar valores
                val result = rasterRepository.queryRasterValues(
                    latitude = latitude,
                    longitude = longitude,
                    activeLayers = activeLayers
                )

                when (result) {
                    is RasterRepository.QueryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                rasterValues = result.values,
                                showRasterInfoPanel = true,
                                isQueryingRaster = false
                            )
                        }
                        Timber.d("Valores encontrados: ${result.values.size}")
                    }
                    is RasterRepository.QueryResult.NoData -> {
                        _uiState.update {
                            it.copy(
                                rasterValues = emptyList(),
                                showRasterInfoPanel = false,
                                isQueryingRaster = false
                            )
                        }
                        showError("No hay datos en esta ubicación")
                        Timber.d("No hay datos en la coordenada consultada")
                    }
                    is RasterRepository.QueryResult.Error -> {
                        _uiState.update { it.copy(isQueryingRaster = false) }
                        showError("Error al consultar: ${result.message}")
                        Timber.e(result.exception, "Error en consulta ráster")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isQueryingRaster = false) }
                showError("Error inesperado al consultar valores")
                Timber.e(e, "Error inesperado en queryRasterValues")
            }
        }
    }

    /**
     * Cierra el panel de información ráster.
     */
    fun dismissRasterInfoPanel() {
        _uiState.update {
            it.copy(
                showRasterInfoPanel = false,
                rasterValues = emptyList()
            )
        }
        Timber.d("Panel de info ráster cerrado")
    }

    /**
     * Habilita o deshabilita el modo de consulta ráster.
     * Cuando está habilitado, los taps en el mapa consultan valores.
     */
    fun setRasterQueryMode(enabled: Boolean) {
        _uiState.update { it.copy(isRasterQueryMode = enabled) }
        Timber.d("Modo consulta ráster: $enabled")
    }

    /**
     * Alterna el modo de consulta ráster.
     */
    fun toggleRasterQueryMode() {
        setRasterQueryMode(!_uiState.value.isRasterQueryMode)
    }

}
