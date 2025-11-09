package com.dasomaps.app.data.model

/**
 * Tipos de capa soportados por la aplicación.
 */
enum class LayerType {
    /** Capa ráster (GeoTIFF, COG) */
    RASTER,
    
    /** Teselas de mapa (MBTiles) */
    MBTILES,
    
    /** Capa vectorial (geometrías) */
    VECTOR,
    
    /** Mapa base (OpenStreetMap) */
    BASE_MAP
}

/**
 * Estado de sincronización de una capa.
 */
enum class SyncStatus {
    /** No sincronizada (solo local) */
    LOCAL_ONLY,
    
    /** Sincronizada y actualizada */
    SYNCED,
    
    /** Pendiente de sincronización */
    PENDING_SYNC,
    
    /** Error en sincronización */
    SYNC_ERROR,
    
    /** Descargando desde servidor */
    DOWNLOADING
}

/**
 * Modelo de datos para una capa geoespacial.
 *
 * @property id Identificador único de la capa
 * @property name Nombre descriptivo de la capa
 * @property type Tipo de capa
 * @property isVisible Si la capa está actualmente visible en el mapa
 * @property opacity Opacidad de la capa (0.0 a 1.0)
 * @property zIndex Índice de orden de la capa (mayor = más arriba)
 * @property localPath Ruta local del archivo (si está en cache)
 * @property remoteUrl URL remota del servidor (si aplica)
 * @property syncStatus Estado de sincronización
 * @property bounds Límites geográficos [minLon, minLat, maxLon, maxLat]
 * @property zoomLevels Niveles de zoom disponibles
 * @property createdAt Fecha de creación
 * @property updatedAt Fecha de última actualización
 */
data class Layer(
    val id: String,
    val name: String,
    val type: LayerType,
    val isVisible: Boolean = true,
    val opacity: Float = 1.0f,
    val zIndex: Int = 0,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val bounds: List<Double>? = null,
    val zoomLevels: IntRange? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Verifica si la capa está disponible offline.
     */
    fun isAvailableOffline(): Boolean = localPath != null
    
    /**
     * Verifica si la capa necesita sincronización.
     */
    fun needsSync(): Boolean = syncStatus == SyncStatus.PENDING_SYNC || syncStatus == SyncStatus.SYNC_ERROR
}
