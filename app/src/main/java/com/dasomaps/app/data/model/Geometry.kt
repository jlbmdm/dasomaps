package com.dasomaps.app.data.model

import org.locationtech.jts.geom.Geometry

/**
 * Tipos de geometría soportados.
 */
enum class GeometryType {
    /** Punto (waypoint) */
    POINT,
    
    /** Línea (track, ruta) */
    LINE,
    
    /** Polígono (área) */
    POLYGON
}

/**
 * Modelo de datos para una geometría capturada.
 *
 * @property id Identificador único de la geometría
 * @property name Nombre descriptivo
 * @property type Tipo de geometría
 * @property geometry Geometría JTS
 * @property attributes Atributos adicionales (clave-valor)
 * @property color Color para visualización (formato hex: #RRGGBB)
 * @property createdAt Fecha de creación
 * @property updatedAt Fecha de última actualización
 * @property isSynced Si está sincronizada con el servidor
 */
data class CapturedGeometry(
    val id: String,
    val name: String,
    val type: GeometryType,
    val geometry: Geometry,
    val attributes: Map<String, String> = emptyMap(),
    val color: String = "#FF0000",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
) {
    /**
     * Obtiene las coordenadas del centroide de la geometría.
     */
    fun getCentroid(): Pair<Double, Double> {
        val centroid = geometry.centroid
        return Pair(centroid.y, centroid.x) // (lat, lon)
    }
    
    /**
     * Calcula el área de la geometría (solo para polígonos).
     * @return Área en metros cuadrados, o null si no es un polígono
     */
    fun getArea(): Double? {
        return if (type == GeometryType.POLYGON) {
            geometry.area
        } else {
            null
        }
    }
    
    /**
     * Calcula la longitud de la geometría (para líneas).
     * @return Longitud en metros, o null si no es una línea
     */
    fun getLength(): Double? {
        return if (type == GeometryType.LINE) {
            geometry.length
        } else {
            null
        }
    }
}

/**
 * Punto de coordenadas simple (lat, lon).
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
