package com.dasomaps.app.ui.layers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.repository.LayerRepository
import com.dasomaps.app.data.repository.RasterRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Estado de la UI de capas.
 *
 * @property layers Lista de capas disponibles
 * @property isLoading Si está cargando datos
 * @property errorMessage Mensaje de error si existe
 * @property filterType Filtro de tipo de capa activo
 * @property searchQuery Texto de búsqueda actual
 * @property isReorderMode Si está en modo reordenamiento
 * @property lastAddedLayer Última capa añadida (para mostrar diálogo de información)
 * @property showLayerInfoDialog Si se debe mostrar el diálogo de información de capa
 */
data class LayersUiState(
    val layers: List<Layer> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val filterType: LayerType? = null,
    val searchQuery: String = "",
    val isReorderMode: Boolean = false,
    val lastAddedLayer: Layer? = null,
    val showLayerInfoDialog: Boolean = false
)

/**
 * ViewModel para la pantalla de gestión de capas.
 * Gestiona el estado de las capas y las operaciones sobre ellas.
 */
class LayersViewModel(
    private val layerRepository: LayerRepository,
    private val rasterRepository: RasterRepository
) : ViewModel() {

    // Estado privado mutable
    private val _uiState = MutableStateFlow(LayersUiState())
    
    // Estado público inmutable
    val uiState: StateFlow<LayersUiState> = _uiState.asStateFlow()

    init {
        Timber.d("LayersViewModel inicializado")
        loadLayers()
    }

    /**
     * Carga todas las capas desde el repositorio.
     */
    fun loadLayers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val filterType = _uiState.value.filterType
                
                val layersFlow = if (filterType != null) {
                    layerRepository.getLayersByType(filterType)
                } else {
                    layerRepository.getAllLayers()
                }
                
                layersFlow.collect { layers ->
                    _uiState.value = _uiState.value.copy(
                        layers = layers,
                        isLoading = false,
                        errorMessage = null
                    )
                    Timber.d("Capas cargadas: ${layers.size}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar capas: ${e.message}"
                )
                Timber.e(e, "Error al cargar capas")
            }
        }
    }

    /**
     * Alterna la visibilidad de una capa.
     *
     * @param layer Capa a actualizar
     */
    fun toggleLayerVisibility(layer: Layer) {
        viewModelScope.launch {
            try {
                val updatedLayer = layer.copy(
                    isVisible = !layer.isVisible,
                    updatedAt = System.currentTimeMillis()
                )
                layerRepository.updateLayer(updatedLayer)
                Timber.d("Visibilidad de capa alternada: ${layer.name} -> ${updatedLayer.isVisible}")
            } catch (e: Exception) {
                showError("Error al actualizar visibilidad: ${e.message}")
                Timber.e(e, "Error al actualizar visibilidad de capa")
            }
        }
    }

    /**
     * Actualiza la opacidad de una capa.
     *
     * @param layer Capa a actualizar
     * @param opacity Nueva opacidad (0.0 a 1.0)
     */
    fun updateLayerOpacity(layer: Layer, opacity: Float) {
        viewModelScope.launch {
            try {
                val clampedOpacity = opacity.coerceIn(0.0f, 1.0f)
                val updatedLayer = layer.copy(
                    opacity = clampedOpacity,
                    updatedAt = System.currentTimeMillis()
                )
                layerRepository.updateLayer(updatedLayer)
                Timber.d("Opacidad de capa actualizada: ${layer.name} -> $clampedOpacity")
            } catch (e: Exception) {
                showError("Error al actualizar opacidad: ${e.message}")
                Timber.e(e, "Error al actualizar opacidad de capa")
            }
        }
    }

    /**
     * Añade una nueva capa y muestra el diálogo de información.
     *
     * @param layer Capa a añadir
     */
    fun addLayer(layer: Layer) {
        viewModelScope.launch {
            try {
                layerRepository.addLayer(layer)
                
                // Actualizar estado para mostrar diálogo
                _uiState.value = _uiState.value.copy(
                    lastAddedLayer = layer,
                    showLayerInfoDialog = true
                )
                
                Timber.d("Nueva capa añadida: ${layer.name}")
            } catch (e: Exception) {
                showError("Error al añadir capa: ${e.message}")
                Timber.e(e, "Error al añadir capa")
            }
        }
    }

    /**
     * Cierra el diálogo de información de capa.
     */
    fun dismissLayerInfoDialog() {
        _uiState.value = _uiState.value.copy(
            showLayerInfoDialog = false,
            lastAddedLayer = null
        )
    }

    /**
     * Elimina una capa.
     *
     * @param layer Capa a eliminar
     */
    fun deleteLayer(layer: Layer) {
        viewModelScope.launch {
            try {
                layerRepository.deleteLayer(layer)
                Timber.d("Capa eliminada: ${layer.name}")
            } catch (e: Exception) {
                showError("Error al eliminar capa: ${e.message}")
                Timber.e(e, "Error al eliminar capa")
            }
        }
    }

    /**
     * Filtra capas por tipo.
     *
     * @param type Tipo de capa a filtrar, o null para mostrar todas
     */
    fun filterByType(type: LayerType?) {
        _uiState.value = _uiState.value.copy(filterType = type)
        loadLayers()
        Timber.d("Filtro de capas aplicado: $type")
    }

    /**
     * Crea capas de ejemplo para pruebas.
     */
    fun createSampleLayers() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                layerRepository.createSampleLayers()
                _uiState.value = _uiState.value.copy(isLoading = false)
                Timber.d("Capas de ejemplo creadas")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                showError("Error al crear capas de ejemplo: ${e.message}")
                Timber.e(e, "Error al crear capas de ejemplo")
            }
        }
    }

    /**
     * Obtiene el número de capas visibles.
     */
    fun getVisibleLayersCount(): Int {
        return _uiState.value.layers.count { it.isVisible }
    }

    /**
     * Obtiene el número de capas por tipo.
     */
    fun getLayerCountByType(type: LayerType): Int {
        return _uiState.value.layers.count { it.type == type }
    }

    /**
     * Muestra un mensaje de error.
     */
    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
        Timber.e("Error: $message")
    }

    /**
     * Limpia el mensaje de error actual.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Actualiza la consulta de búsqueda y filtra las capas.
     *
     * @param query Texto de búsqueda
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        Timber.d("Consulta de búsqueda actualizada: $query")
    }

    /**
     * Obtiene las capas filtradas por tipo y búsqueda.
     */
    fun getFilteredLayers(): List<Layer> {
        val layers = _uiState.value.layers
        val query = _uiState.value.searchQuery.lowercase().trim()
        
        return if (query.isEmpty()) {
            layers
        } else {
            layers.filter { layer ->
                layer.name.lowercase().contains(query) ||
                layer.localPath?.lowercase()?.contains(query) == true
            }
        }
    }

    /**
     * Activa o desactiva el modo reordenamiento.
     */
    fun toggleReorderMode() {
        val newMode = !_uiState.value.isReorderMode
        _uiState.value = _uiState.value.copy(isReorderMode = newMode)
        Timber.d("Modo reordenamiento: $newMode")
    }

    /**
     * Reordena las capas según una nueva lista ordenada.
     *
     * @param reorderedLayers Lista de capas en el nuevo orden
     */
    fun reorderLayers(reorderedLayers: List<Layer>) {
        viewModelScope.launch {
            try {
                layerRepository.reorderLayers(reorderedLayers)
                Timber.d("Capas reordenadas exitosamente")
            } catch (e: Exception) {
                showError("Error al reordenar capas: ${e.message}")
                Timber.e(e, "Error al reordenar capas")
            }
        }
    }

    /**
     * Importa un archivo GeoTIFF.
     * 
     * @param file Archivo GeoTIFF a importar
     * @param layerName Nombre para la nueva capa
     */
    fun importGeoTIFF(file: File, layerName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                Timber.d("Iniciando importación de GeoTIFF: ${file.name}")
                
                // Usar RasterRepository para importar
                val result = rasterRepository.importGeoTIFF(file, layerName)
                
                if (result != null) {
                    val (layer, _) = result
                    addLayer(layer)
                    Timber.d("GeoTIFF importado exitosamente: ${layer.name}")
                } else {
                    showError("Error al importar GeoTIFF: archivo no válido o formato incorrecto")
                    Timber.w("Resultado nulo al importar GeoTIFF: ${file.name}")
                }
                
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                showError("Error al importar: ${e.message}")
                Timber.e(e, "Error al importar GeoTIFF: ${file.name}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("LayersViewModel limpiado")
    }
}
