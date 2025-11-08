package com.dasomaps.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room para almacenar geometrías capturadas.
 */
@Entity(tableName = "geometries")
data class GeometryEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,
    
    val type: String, // GeometryType como String (POINT, LINE, POLYGON)
    
    val wkt: String, // Well-Known Text representation de la geometría
    
    val attributesJson: String? = null, // JSON con atributos adicionales
    
    val color: String = "#FF0000",
    
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis(),
    
    val isSynced: Boolean = false
)
