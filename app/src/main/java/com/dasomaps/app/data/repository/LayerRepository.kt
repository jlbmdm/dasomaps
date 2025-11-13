package com.dasomaps.app.data.repository

import com.dasomaps.app.data.local.dao.LayerDao
import com.dasomaps.app.data.local.entity.LayerEntity
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.model.SyncStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Repositorio para gestionar capas.
 * Proporciona una abstracción entre la capa de datos y la capa de dominio.
 */
class LayerRepository(
    private val layerDao: LayerDao,
    private val gson: Gson = Gson()
) {

    /**
     * Obtiene todas las capas como Flow reactivo.
     */
    fun getAllLayers(): Flow<List<Layer>> {
        return layerDao.getAllLayers().map { entities ->
            entities.map { entityToModel(it) }
        }
    }

    /**
     * Obtiene solo las capas visibles.
     */
    fun getVisibleLayers(): Flow<List<Layer>> {
        return layerDao.getVisibleLayers().map { entities ->
            entities.map { entityToModel(it) }
        }
    }

    /**
     * Obtiene capas por tipo.
     */
    fun getLayersByType(type: LayerType): Flow<List<Layer>> {
        return layerDao.getLayersByType(type.name).map { entities ->
            entities.map { entityToModel(it) }
        }
    }

    /**
     * Obtiene una capa específica por ID.
     */
    suspend fun getLayerById(layerId: String): Layer? {
        return layerDao.getLayerById(layerId)?.let { entityToModel(it) }
    }

    /**
     * Añade una nueva capa.
     */
    suspend fun addLayer(layer: Layer) {
        try {
            layerDao.insertLayer(modelToEntity(layer))
            Timber.d("Capa añadida: ${layer.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error al añadir capa: ${layer.name}")
            throw e
        }
    }

    /**
     * Actualiza una capa existente.
     */
    suspend fun updateLayer(layer: Layer) {
        try {
            layerDao.updateLayer(modelToEntity(layer))
            Timber.d("Capa actualizada: ${layer.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar capa: ${layer.name}")
            throw e
        }
    }

    /**
     * Elimina una capa.
     */
    suspend fun deleteLayer(layer: Layer) {
        try {
            layerDao.deleteLayerById(layer.id)
            Timber.d("Capa eliminada: ${layer.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar capa: ${layer.name}")
            throw e
        }
    }

    /**
     * Actualiza la visibilidad de una capa.
     */
    suspend fun updateLayerVisibility(layerId: String, isVisible: Boolean) {
        try {
            layerDao.updateLayerVisibility(layerId, isVisible)
            Timber.d("Visibilidad de capa actualizada: $layerId -> $isVisible")
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar visibilidad de capa: $layerId")
            throw e
        }
    }

    /**
     * Actualiza la opacidad de una capa.
     */
    suspend fun updateLayerOpacity(layerId: String, opacity: Float) {
        try {
            layerDao.updateLayerOpacity(layerId, opacity)
            Timber.d("Opacidad de capa actualizada: $layerId -> $opacity")
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar opacidad de capa: $layerId")
            throw e
        }
    }

    /**
     * Obtiene capas que necesitan sincronización.
     */
    suspend fun getLayersNeedingSync(): List<Layer> {
        return layerDao.getLayersNeedingSync().map { entityToModel(it) }
    }

    /**
     * Actualiza el estado de sincronización de una capa.
     */
    suspend fun updateSyncStatus(layerId: String, status: SyncStatus) {
        try {
            layerDao.updateSyncStatus(layerId, status.name)
            Timber.d("Estado de sincronización actualizado: $layerId -> $status")
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar estado de sincronización: $layerId")
            throw e
        }
    }

    /**
     * Reordena las capas actualizando sus zIndex.
     * 
     * @param orderedLayers Lista de capas en el orden deseado (índice 0 = más abajo, último = más arriba)
     */
    suspend fun reorderLayers(orderedLayers: List<Layer>) {
        try {
            orderedLayers.forEachIndexed { index, layer ->
                val updatedLayer = layer.copy(
                    zIndex = index,
                    updatedAt = System.currentTimeMillis()
                )
                layerDao.updateLayer(modelToEntity(updatedLayer))
            }
            Timber.d("Capas reordenadas: ${orderedLayers.size} capas")
        } catch (e: Exception) {
            Timber.e(e, "Error al reordenar capas")
            throw e
        }
    }

    /**
     * Obtiene todas las capas ordenadas por zIndex (ascendente).
     */
    fun getLayersOrderedByZIndex(): Flow<List<Layer>> {
        return getAllLayers().map { layers ->
            layers.sortedBy { it.zIndex }
        }
    }

    /**
     * Crea capas de ejemplo para testing.
     */
    suspend fun createSampleLayers() {
        val sampleLayers = listOf(
            Layer(
                id = "layer_osm",
                name = "OpenStreetMap",
                type = LayerType.BASE_MAP,
                isVisible = true,
                opacity = 1.0f,
                syncStatus = SyncStatus.SYNCED
            ),
            Layer(
                id = "layer_satellite",
                name = "Vista Satélite",
                type = LayerType.BASE_MAP,
                isVisible = false,
                opacity = 1.0f,
                syncStatus = SyncStatus.LOCAL_ONLY
            ),
            Layer(
                id = "layer_topo",
                name = "Mapa Topográfico",
                type = LayerType.BASE_MAP,
                isVisible = false,
                opacity = 0.8f,
                syncStatus = SyncStatus.LOCAL_ONLY
            ),
            Layer(
                id = "layer_my_places",
                name = "Mis Lugares",
                type = LayerType.VECTOR,
                isVisible = true,
                opacity = 1.0f,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        )

        sampleLayers.forEach { layer ->
            addLayer(layer)
        }
        Timber.d("Capas de ejemplo creadas: ${sampleLayers.size}")
    }

    // ========== Conversiones Entity <-> Model ==========

    /**
     * Convierte una entidad de base de datos a modelo de dominio.
     */
    private fun entityToModel(entity: LayerEntity): Layer {
        val bounds = entity.boundsJson?.let { json ->
            try {
                val type = object : TypeToken<List<Double>>() {}.type
                gson.fromJson<List<Double>>(json, type)
            } catch (e: Exception) {
                Timber.e(e, "Error al parsear bounds JSON")
                null
            }
        }

        val zoomLevels = if (entity.minZoom != null && entity.maxZoom != null) {
            entity.minZoom..entity.maxZoom
        } else {
            null
        }
        
        val bandNames = entity.bandNamesJson?.let { json ->
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, type)
            } catch (e: Exception) {
                Timber.e(e, "Error al parsear bandNames JSON")
                null
            }
        }

        return Layer(
            id = entity.id,
            name = entity.name,
            type = LayerType.valueOf(entity.type),
            isVisible = entity.isVisible,
            opacity = entity.opacity,
            zIndex = entity.zIndex,
            localPath = entity.localPath,
            remoteUrl = entity.remoteUrl,
            syncStatus = SyncStatus.valueOf(entity.syncStatus),
            bounds = bounds,
            zoomLevels = zoomLevels,
            bandCount = entity.bandCount,
            bandNames = bandNames,
            unit = entity.unit,
            noDataValue = entity.noDataValue,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Convierte un modelo de dominio a entidad de base de datos.
     */
    private fun modelToEntity(layer: Layer): LayerEntity {
        val boundsJson = layer.bounds?.let { bounds ->
            try {
                gson.toJson(bounds)
            } catch (e: Exception) {
                Timber.e(e, "Error al serializar bounds a JSON")
                null
            }
        }
        
        val bandNamesJson = layer.bandNames?.let { bandNames ->
            try {
                gson.toJson(bandNames)
            } catch (e: Exception) {
                Timber.e(e, "Error al serializar bandNames a JSON")
                null
            }
        }

        return LayerEntity(
            id = layer.id,
            name = layer.name,
            type = layer.type.name,
            isVisible = layer.isVisible,
            opacity = layer.opacity,
            zIndex = layer.zIndex,
            localPath = layer.localPath,
            remoteUrl = layer.remoteUrl,
            syncStatus = layer.syncStatus.name,
            boundsJson = boundsJson,
            minZoom = layer.zoomLevels?.first,
            maxZoom = layer.zoomLevels?.last,
            bandCount = layer.bandCount,
            bandNamesJson = bandNamesJson,
            unit = layer.unit,
            noDataValue = layer.noDataValue,
            createdAt = layer.createdAt,
            updatedAt = layer.updatedAt
        )
    }
}
