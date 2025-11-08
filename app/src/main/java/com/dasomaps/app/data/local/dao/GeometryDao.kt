package com.dasomaps.app.data.local.dao

import androidx.room.*
import com.dasomaps.app.data.local.entity.GeometryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones con geometrías en la base de datos.
 */
@Dao
interface GeometryDao {
    
    /**
     * Obtiene todas las geometrías como Flow (reactivo).
     */
    @Query("SELECT * FROM geometries ORDER BY createdAt DESC")
    fun getAllGeometries(): Flow<List<GeometryEntity>>
    
    /**
     * Obtiene una geometría específica por ID.
     */
    @Query("SELECT * FROM geometries WHERE id = :geometryId")
    suspend fun getGeometryById(geometryId: String): GeometryEntity?
    
    /**
     * Obtiene geometrías por tipo.
     */
    @Query("SELECT * FROM geometries WHERE type = :type ORDER BY createdAt DESC")
    fun getGeometriesByType(type: String): Flow<List<GeometryEntity>>
    
    /**
     * Obtiene geometrías no sincronizadas.
     */
    @Query("SELECT * FROM geometries WHERE isSynced = 0")
    suspend fun getUnsyncedGeometries(): List<GeometryEntity>
    
    /**
     * Inserta una nueva geometría.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeometry(geometry: GeometryEntity)
    
    /**
     * Inserta múltiples geometrías.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeometries(geometries: List<GeometryEntity>)
    
    /**
     * Actualiza una geometría existente.
     */
    @Update
    suspend fun updateGeometry(geometry: GeometryEntity)
    
    /**
     * Elimina una geometría.
     */
    @Delete
    suspend fun deleteGeometry(geometry: GeometryEntity)
    
    /**
     * Elimina una geometría por ID.
     */
    @Query("DELETE FROM geometries WHERE id = :geometryId")
    suspend fun deleteGeometryById(geometryId: String)
    
    /**
     * Marca una geometría como sincronizada.
     */
    @Query("UPDATE geometries SET isSynced = 1, updatedAt = :timestamp WHERE id = :geometryId")
    suspend fun markAsSynced(geometryId: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Actualiza el nombre de una geometría.
     */
    @Query("UPDATE geometries SET name = :name, updatedAt = :timestamp WHERE id = :geometryId")
    suspend fun updateGeometryName(geometryId: String, name: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Cuenta el número total de geometrías.
     */
    @Query("SELECT COUNT(*) FROM geometries")
    suspend fun getGeometryCount(): Int
    
    /**
     * Elimina todas las geometrías.
     */
    @Query("DELETE FROM geometries")
    suspend fun deleteAllGeometries()
}
