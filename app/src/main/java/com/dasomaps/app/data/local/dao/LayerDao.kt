package com.dasomaps.app.data.local.dao

import androidx.room.*
import com.dasomaps.app.data.local.entity.LayerEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones con capas en la base de datos.
 */
@Dao
interface LayerDao {
    
    /**
     * Obtiene todas las capas como Flow (reactivo).
     */
    @Query("SELECT * FROM layers ORDER BY createdAt DESC")
    fun getAllLayers(): Flow<List<LayerEntity>>
    
    /**
     * Obtiene una capa específica por ID.
     */
    @Query("SELECT * FROM layers WHERE id = :layerId")
    suspend fun getLayerById(layerId: String): LayerEntity?
    
    /**
     * Obtiene todas las capas visibles.
     */
    @Query("SELECT * FROM layers WHERE isVisible = 1 ORDER BY createdAt DESC")
    fun getVisibleLayers(): Flow<List<LayerEntity>>
    
    /**
     * Obtiene capas por tipo.
     */
    @Query("SELECT * FROM layers WHERE type = :type ORDER BY createdAt DESC")
    fun getLayersByType(type: String): Flow<List<LayerEntity>>
    
    /**
     * Inserta una nueva capa.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: LayerEntity)
    
    /**
     * Inserta múltiples capas.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayers(layers: List<LayerEntity>)
    
    /**
     * Actualiza una capa existente.
     */
    @Update
    suspend fun updateLayer(layer: LayerEntity)
    
    /**
     * Elimina una capa.
     */
    @Delete
    suspend fun deleteLayer(layer: LayerEntity)
    
    /**
     * Elimina una capa por ID.
     */
    @Query("DELETE FROM layers WHERE id = :layerId")
    suspend fun deleteLayerById(layerId: String)
    
    /**
     * Actualiza la visibilidad de una capa.
     */
    @Query("UPDATE layers SET isVisible = :isVisible, updatedAt = :timestamp WHERE id = :layerId")
    suspend fun updateLayerVisibility(layerId: String, isVisible: Boolean, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Actualiza la opacidad de una capa.
     */
    @Query("UPDATE layers SET opacity = :opacity, updatedAt = :timestamp WHERE id = :layerId")
    suspend fun updateLayerOpacity(layerId: String, opacity: Float, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Obtiene capas que necesitan sincronización.
     */
    @Query("SELECT * FROM layers WHERE syncStatus IN ('PENDING_SYNC', 'SYNC_ERROR')")
    suspend fun getLayersNeedingSync(): List<LayerEntity>
    
    /**
     * Actualiza el estado de sincronización de una capa.
     */
    @Query("UPDATE layers SET syncStatus = :status, updatedAt = :timestamp WHERE id = :layerId")
    suspend fun updateSyncStatus(layerId: String, status: String, timestamp: Long = System.currentTimeMillis())
}
