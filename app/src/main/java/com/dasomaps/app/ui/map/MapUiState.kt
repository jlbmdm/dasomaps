package com.dasomaps.app.ui.map

import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.RasterValue
import org.osmdroid.util.GeoPoint

/**
 * Enum para los diferentes tipos de mapa base disponibles.
 */
enum class BaseMapType {
    STREET,       // Callejero (OpenStreetMap)
    TOPO,         // Topográfico (OpenTopoMap)
    SATELLITE,    // Satélite (ESRI World Imagery)
    IGN_BASE,     // IGN Base (Callejero de España)
    IGN_RASTER,   // IGN Raster (Topográfico de España)
    IGN_PNOA,     // IGN PNOA (Ortofotos de máxima actualidad) // Descomentar tb en MapScreen.kt
//    IGN_MDT,      // IGN MDT (Modelo Digital de Terrenos) // Descomentar tb en MapScreen.kt
//    IGN_RELIEVE,  // IGN Relieve (sombreado) // Descomentar tb en MapScreen.kt
//    IGN_LIDAR,    // IGN LiDAR (Modelo Digital de Superficies) // Descomentar tb en MapScreen.kt
//    ITACYL_ORTO,  // Ortofoto de ITACyL (WMS) // Descomentar tb en MapScreen.kt
    IDECYL_ORTO,  // Ortofoto de IDECyL // Descomentar tb en MapScreen.kt
    IDECYL_TOPO   // Topográfico de IDECyL // Descomentar tb en MapScreen.kt
}

/**
 * Data class que representa el estado de la interfaz de usuario para la pantalla del mapa.
 */
data class MapUiState(
    val zoom: Double = 15.0,
    val center: GeoPoint = GeoPoint(41.6523, -4.7245), // Valladolid
    val isMyLocationEnabled: Boolean = false,
    val isFollowingLocation: Boolean = false,
    val visibleLayers: List<Layer> = emptyList(),
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isRasterQueryMode: Boolean = false,
    val isQueryingRaster: Boolean = false,
    val rasterValues: List<RasterValue> = emptyList(),
    val showRasterInfoPanel: Boolean = false,
    // val baseMapType: BaseMapType = BaseMapType.STREET // Por defecto, el callejero de OSM -> TileSourceFactory.MAPNIK
    // val baseMapType: BaseMapType = BaseMapType.IGN_RASTER // Por defecto, MTN25k raster
    val baseMapType: BaseMapType = BaseMapType.TOPO // Por defecto, el Topográfico (Mundial) -> TileSourceFactory.OpenTopo
)
