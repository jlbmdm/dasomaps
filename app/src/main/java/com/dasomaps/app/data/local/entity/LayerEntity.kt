package com.dasomaps.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.model.SyncStatus

/**
 * Entidad Room para almacenar información de capas.
 */
@Entity(tableName = "layers")
data class LayerEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,
    
    val type: String, // LayerType como String
    
    val isVisible: Boolean = true,
    
    val opacity: Float = 1.0f,
    
    val zIndex: Int = 0,
    
    val localPath: String? = null,
    
    val remoteUrl: String? = null,
    
    val syncStatus: String = SyncStatus.LOCAL_ONLY.name,
    
    val boundsJson: String? = null, // JSON array: [minLon, minLat, maxLon, maxLat]
    
    val minZoom: Int? = null,
    
    val maxZoom: Int? = null,
    
    // Campos específicos para capas RASTER (GeoTIFF)
    val bandCount: Int? = null,
    
    val bandNamesJson: String? = null, // JSON array: ["Banda1", "Banda2", ...]
    
    val unit: String? = null, // Unidad de medida (ej: "m", "m³/ha")
    
    val noDataValue: Double? = null, // Valor que representa ausencia de datos
    
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
)
