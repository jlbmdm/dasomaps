package com.dasomaps.app.ui.map

import android.Manifest
import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dasomaps.app.data.local.DasoMapsDatabase
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.repository.LayerRepository
import com.dasomaps.app.data.repository.RasterRepository
import com.dasomaps.app.ui.components.CoordinateDisplay
import com.dasomaps.app.utils.Constants
import com.dasomaps.app.utils.CoordinateUtils
import com.dasomaps.app.utils.MBTilesUpscalingProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber
import java.io.File

// =================================================================================
// DEFINICIÓN DE MAPAS BASE PERSONALIZADOS
// =================================================================================

/**
 * Clase auxiliar para Tile Sources que usan el formato de URL Z/Y/X en lugar del estándar Z/X/Y.
 */
class YXTileSource(
    name: String, minZoom: Int, maxZoom: Int, tileSize: Int, imageFilenameEnding: String,
    baseUrls: Array<String>, copyright: String
) : XYTileSource(name, minZoom, maxZoom, tileSize, imageFilenameEnding, baseUrls, copyright) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + mImageFilenameEnding
    }
}

/**
 * Fuente de teselas para servicios WMTS estándar (KVP, no RESTful).
 * Permite construir URL del tipo:
 * ?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=...&STYLE=...&TILEMATRIXSET=...&TILEMATRIX=...&TILEROW=...&TILECOL=...&FORMAT=...
 *
 * Si [style] es null, se omite completamente el parámetro STYLE.
 */
class WMTSTileSource(
    name: String,
    baseUrl: String,
    private val layer: String,
    private val tileMatrixSet: String,
    private val format: String,
    private val style: String? = null
) : OnlineTileSourceBase(name, 0, 20, 256, ".jpeg", arrayOf(baseUrl), "") {

    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)

        return buildString {
            append(baseUrl)
            if (!baseUrl.contains("?")) append("?") else append("&")
            append("SERVICE=WMTS")
            append("&REQUEST=GetTile")
            append("&VERSION=1.0.0")
            append("&LAYER=$layer")
            style?.let { append("&STYLE=$it") }
            append("&TILEMATRIXSET=$tileMatrixSet")
            append("&TILEMATRIX=$tileMatrixSet:$z") // 👈 cambio clave
            append("&TILEROW=$y")
            append("&TILECOL=$x")
            append("&FORMAT=$format")
        }
    }
}

//Esto ya no lo uso:
/*
// IGN WMTS - Mapa Rasterizado (MTN)
class IGNMTNTileSource : OnlineTileSourceBase(
    "IGN_MTNRaster",
    0, 20, 256, ".jpeg",
    arrayOf("https://www.ign.es/wmts/mapa-raster/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTile.getZoom(pMapTileIndex)
        val x = MapTile.getX(pMapTileIndex)
        val y = MapTile.getY(pMapTileIndex)

        // Construcción URL según especificación WMTS (KVP)
        // Ejemplo válido:
        // https://www.ign.es/wmts/mapa-raster?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0
        // &LAYER=MTN&STYLE=default&TILEMATRIXSET=GoogleMapsCompatible
        // &TILEMATRIX=14&TILEROW=7977&TILECOL=6105&FORMAT=image/jpeg
        return "https://www.ign.es/wmts/mapa-raster?" +
                "SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                "&LAYER=MTN&STYLE=default" +
                "&TILEMATRIXSET=GoogleMapsCompatible" +
                "&TILEMATRIX=$zoom&TILEROW=$y&TILECOL=$x" +
                "&FORMAT=image/jpeg"
    }
}
*/

/**
 * Clase auxiliar para servicios WMS. Construye la URL con el BBOX requerido.
 */
class WMSTileSource(name: String, baseUrl: String, version: String, layers: String, format: String) :
    OnlineTileSourceBase(name, 0, 20, 256, ".png", arrayOf(baseUrl), "") {
    private val mVersion = version
    private val mLayers = layers
    private val mFormat = format

    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)

        val tileSystem = MapView.getTileSystem()
        
        // Convertir coordenadas de tesela a lat/lon
        val lonWest = tileSystem.getLongitudeFromTileX(x, zoom)
        val lonEast = tileSystem.getLongitudeFromTileX(x + 1, zoom)
        val latNorth = tileSystem.getLatitudeFromTileY(y, zoom)
        val latSouth = tileSystem.getLatitudeFromTileY(y + 1, zoom)

        val url = StringBuilder(baseUrl)
        url.append("?SERVICE=WMS")
        url.append("&VERSION=$mVersion")
        url.append("&REQUEST=GetMap")
        url.append("&LAYERS=$mLayers")
        url.append("&FORMAT=$mFormat")
        url.append("&BBOX=$lonWest,$latSouth,$lonEast,$latNorth")
        url.append("&WIDTH=${tileSizePixels}")
        url.append("&HEIGHT=${tileSizePixels}")
        url.append("&SRS=EPSG:3857")
        url.append("&STYLES=")
        url.append("&TRANSPARENT=TRUE")

        return url.toString()
    }
}

// --- Mapas Base ----

// Formato Z/Y/X
private val esriSatelliteTileSource = YXTileSource(
    "EsriWorldImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri, et al."
)

// wmts del IGN: Formato wmts
// Para wmts o wms no debe usarse el Formato Z/X/Y (Estándar) xq no es RESTful tipo /Z/X/Y.jpeg
// Ejemplo RESTfun Z/X/Y: https://www.ign.es/wmts/ign-base/ign-base/default/EPSG:3857/14/7978/6105.jpeg
/*
// Formato Z/X/Y (Estándar)
private val ignBaseTileSource = XYTileSource(
    "IGNBase", 0, 20, 256, ".jpeg",
    arrayOf("https://www.ign.es/wmts/ign-base/ign-base/default/EPSG:3857/"),
    "© IGN"
)
*/

/*
Lo correcto es usar los con parámetros GetTile, que se pide así:
https://www.ign.es/wmts/ign-base?
    SERVICE=WMTS&
    REQUEST=GetTile&
    VERSION=1.0.0&
    LAYER=ign-base&
    TILEMATRIXSET=GoogleMapsCompatible&
    TILEMATRIX=14&
    TILEROW=6105&
    TILECOL=7978&
    FORMAT=image/jpeg
*/
private val ignBaseTodoTileSource = WMTSTileSource(
    name = "IGNBaseTodo",
    baseUrl = "https://www.ign.es/wmts/ign-base",
    layer = "IGNBaseTodo",
    tileMatrixSet = "GoogleMapsCompatible",
    format = "image/jpeg",
    style = "default"
)

// wmts del IGN: Formato wmts
// Versión incorrecta:
/*
// Formato Z/X/Y (Estándar) ->> No funciona
private val ignRasterTileSource = XYTileSource(
    "IGNRaster", 0, 20, 256, ".jpeg",
    arrayOf("https://www.ign.es/wmts/mapa-raster/mapa-raster/default/EPSG:3857/"),
    "© IGN"
)
*/
// Versión correcta:
// IGN - Mapas ráster del IGN (MTN)
private val ignMtnTileSource = WMTSTileSource(
    name = "IGNMTN",
    baseUrl = "https://www.ign.es/wmts/mapa-raster",
    layer = "MTN",
    tileMatrixSet = "GoogleMapsCompatible",
    format = "image/jpeg",
    style = "default"
)

// WMTS - Imágenes de satélite Sentinel y ortofotos PNOA (OI.orthoimageCoverage)
private val ignPnoaTileSource = WMTSTileSource(
    name = "IGN_PNOA",
    baseUrl = "https://www.ign.es/wmts/pnoa-ma",
    layer = "OI.OrthoimageCoverage",
    tileMatrixSet = "GoogleMapsCompatible",
    format = "image/jpeg",
    style = "default"
)

private val ignMdtTileSource = WMTSTileSource(
    "IGN_MDT",
    "https://servicios.idee.es/wmts/mdt",
    "EL.ElevationGridCoverage",
    "GoogleMapsCompatible",  //  Verificado con https://www.ign.es/wmts/mapa-raster?request=GetCapabilities&service=WMTS que usa GoogleMapsCompatible
    "image/jpeg",
    style = "default"
)

private val ignRelieveTileSource = WMTSTileSource(
    "Relieve",
    "https://servicios.idee.es/wmts/mdt",
    "Relieve",
    "GoogleMapsCompatible",  //  Verificado con https://www.ign.es/wmts/mapa-raster?request=GetCapabilities&service=WMTS que usa GoogleMapsCompatible
    "image/jpeg",
    style = "default"
)
// No disponible:
/*
private val ignLidarTileSource = WMTSTileSource(
    "IGNLidar",
    "https://www.ign.es/wmts/lidar/LIDAR",
    "MapaLidar-IGN",
    "GoogleMapsCompatible",  //  Verificado con https://www.ign.es/wmts/mapa-raster?request=GetCapabilities&service=WMTS que usa GoogleMapsCompatible
    "image/jpeg"
)
*/

private val itacylOrtoTileSource = WMTSTileSource(
    "Ortofoto-ITACYL",
    "https://orto.wms.itacyl.es/WMS",
    "Ortofoto-ITACYL",
    "GoogleMapsCompatible",  //  Verificado con https://www.ign.es/wmts/mapa-raster?request=GetCapabilities&service=WMTS que usa GoogleMapsCompatible
    "image/jpeg",
    style = "default"
)

// WMTS - Ortoimagen 2020-2021 Castilla y León (IDEcyl)
private val idecylOrto2020TileSource = WMTSTileSource(
    name = "IDECyL_Orto2020",
    baseUrl = "https://idecyl.jcyl.es/geoserver/oi/gwc/service/wmts",
    layer = "oi_2020_cyl",
    tileMatrixSet = "EPSG:900913",
    format = "image/jpeg",
    style = null   // 👈 no incluir STYLE
)

private val idecylTopoTileSource = WMTSTileSource(
    name = "MapaCyL",
    baseUrl = "https://idecyl.jcyl.es/geoserver/mapacyl/gwc/service/wmts",
    layer = "MapaCyL",
    tileMatrixSet = "EPSG:900913",
    format = "image/jpeg",
    style = null   // 👈 no incluir STYLE
)


/**
 * Pantalla principal del mapa.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val database = remember { DasoMapsDatabase.getInstance(context) }
    val layerRepository = remember { LayerRepository(database.layerDao()) }
    val rasterRepository = remember { RasterRepository(context) }
    val viewModel = remember { MapViewModel(context, layerRepository, rasterRepository) }

    val uiState by viewModel.uiState.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var showBaseMapDialog by remember { mutableStateOf(false) }

    val mbtilesOverlays = remember { mutableMapOf<String, TilesOverlay>() }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    var showZoomButtons by remember { mutableStateOf(false) }

    LaunchedEffect(showZoomButtons) {
        if (showZoomButtons) {
            delay(5000)  // 5 segundos
            showZoomButtons = false
        }
    }

//    // Efectos para actualizar la cámara del mapa
//    LaunchedEffect(uiState.center) {
//        mapView?.controller?.setCenter(uiState.center)
//    }
//    LaunchedEffect(uiState.zoom) {
//        mapView?.controller?.setZoom(uiState.zoom)
//    }

    // Efecto para CAMBIAR el mapa base
    LaunchedEffect(uiState.baseMapType) {
        val newTileSource = when (uiState.baseMapType) {
            BaseMapType.STREET -> TileSourceFactory.MAPNIK
            BaseMapType.TOPO -> TileSourceFactory.OpenTopo
            BaseMapType.SATELLITE -> esriSatelliteTileSource
            BaseMapType.IGN_BASE -> ignBaseTodoTileSource
            BaseMapType.IGN_RASTER -> ignMtnTileSource
            BaseMapType.IGN_PNOA -> ignPnoaTileSource // Descomentar tb en MapUiState.kt
//            BaseMapType.IGN_MDT -> ignMdtTileSource // Descomentar tb en MapUiState.kt
//            BaseMapType.IGN_RELIEVE -> ignRelieveTileSource // Descomentar tb en MapUiState.kt
//            BaseMapType.IGN_LIDAR -> ignLidarTileSource // Descomentar tb en MapUiState.kt
//            BaseMapType.ITACYL_ORTO -> itacylOrtoTileSource // Descomentar tb en MapUiState.kt
            BaseMapType.IDECYL_ORTO -> idecylOrto2020TileSource // Descomentar tb en MapUiState.kt
            BaseMapType.IDECYL_TOPO -> idecylTopoTileSource // Descomentar tb en MapUiState.kt
        }
        mapView?.setTileSource(newTileSource)
        Timber.d("Cambiando mapa base a: ${newTileSource.name()}")
    }

    // Efecto para seguimiento de ubicación
    LaunchedEffect(uiState.isFollowingLocation) {
        if (uiState.isFollowingLocation) {
            while (uiState.isFollowingLocation) {
                myLocationOverlay?.myLocation?.let { myLocation ->
                    mapView?.controller?.animateTo(GeoPoint(myLocation.latitude, myLocation.longitude))
                }
                delay(1000) // Actualizar cada segundo
            }
        }
    }

    // Efecto para habilitar/deshabilitar la capa de ubicación
    LaunchedEffect(uiState.isMyLocationEnabled) {
        myLocationOverlay?.let { overlay ->
            if (uiState.isMyLocationEnabled) {
                overlay.enableMyLocation()
                overlay.enableFollowLocation()
            } else {
                overlay.disableMyLocation()
                overlay.disableFollowLocation()
            }
        }
    }

    // Efecto para gestionar overlays de capas MBTiles
    LaunchedEffect(uiState.visibleLayers) {
        mapView?.let { map ->
            val mbtilesLayers = uiState.visibleLayers.filter { it.type == LayerType.MBTILES }
            val currentLayerIds = mbtilesOverlays.keys.toSet()
            val newLayerIds = mbtilesLayers.map { it.id }.toSet()

            // Eliminar overlays que ya no son visibles
            (currentLayerIds - newLayerIds).forEach { layerId ->
                mbtilesOverlays.remove(layerId)?.let { map.overlays.remove(it) }
            }

            // Añadir o actualizar overlays
            mbtilesLayers.forEach { layer ->
                if (layer.isVisible && layer.localPath != null) {
                    val file = File(layer.localPath)
                    if (!file.exists()) return@forEach

                    val existingOverlay = mbtilesOverlays[layer.id]
                    if (existingOverlay != null) {
                        // Actualizar opacidad
                        existingOverlay.setColorFilter(
                            android.graphics.PorterDuffColorFilter(
                                android.graphics.Color.argb((layer.opacity * 255).toInt(), 255, 255, 255),
                                android.graphics.PorterDuff.Mode.DST_IN
                            )
                        )
                    } else {
                        // Crear nuevo overlay
                        try {
                            val metadata = com.dasomaps.app.utils.MBTilesManager.readMBTilesMetadata(file)
                            val tileSource = com.dasomaps.app.utils.MBTilesManager.createTileSourceFromMBTiles(file, metadata)
                            val archiveFile = ArchiveFileFactory.getArchiveFile(file) ?: return@forEach

                            val moduleProvider = MapTileFileArchiveProvider(SimpleRegisterReceiver(context), tileSource, arrayOf(archiveFile))
                            val maxZoomInFile = metadata["maxzoom"]?.toString()?.toIntOrNull() ?: 18
                            val upscalingProvider = MBTilesUpscalingProvider(context, moduleProvider, pMaxZoomWithData = maxZoomInFile)
                            val tileProvider = MapTileProviderArray(tileSource, SimpleRegisterReceiver(context), arrayOf(upscalingProvider))

                            val overlay = TilesOverlay(tileProvider, context).apply {
                                loadingBackgroundColor = AndroidColor.TRANSPARENT
                                setColorFilter(
                                    android.graphics.PorterDuffColorFilter(
                                        android.graphics.Color.argb((layer.opacity * 255).toInt(), 255, 255, 255),
                                        android.graphics.PorterDuff.Mode.DST_IN
                                    )
                                )
                            }

                            val locationOverlayIndex = map.overlays.indexOfFirst { it is MyLocationNewOverlay }
                            if (locationOverlayIndex >= 0) {
                                map.overlays.add(locationOverlayIndex, overlay)
                            } else {
                                map.overlays.add(overlay)
                            }
                            mbtilesOverlays[layer.id] = overlay
                        } catch (e: Exception) {
                            Timber.e(e, "Error al crear overlay MBTiles: ${layer.name}")
                        }
                    }
                }
            }
            map.invalidate()
        }
    }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        minZoomLevel = Constants.Map.MIN_ZOOM
                        maxZoomLevel = 28.0
//                        controller.setZoom(uiState.zoom)
//                        controller.setCenter(uiState.center)

                        // Capa de ubicación
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).also {
                            it.enableMyLocation()
                            overlays.add(it)
                            myLocationOverlay = it
                        }

                        // Barra de escala
                        ScaleBarOverlay(this).also {
                            it.setCentred(true)
                            it.setScaleBarOffset(ctx.resources.displayMetrics.widthPixels / 2, 10)
                            overlays.add(it)
                        }
                        
                        mapView = this

                        // Detector de toques para consulta ráster
                        this.setOnTouchListener { _, event ->
                            if (event.action == android.view.MotionEvent.ACTION_DOWN && uiState.isRasterQueryMode) {
                                val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                                viewModel.queryRasterValues(geoPoint.latitude, geoPoint.longitude)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                true
                            } else false
                        }
                    }
                },
                update = { view ->
                    // Actualizar el centro y el zoom desde el estado
                    view.controller.setZoom(uiState.zoom)
                    view.controller.setCenter(uiState.center)

                    // Listener para actualizar el estado cuando el mapa se mueve
                    view.addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?) = true.also { viewModel.updateMapCenter(view.mapCenter as GeoPoint) }
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?) = true.also { event?.let { viewModel.updateMapZoom(it.zoomLevel) } }
                    })
                    view.invalidate()
                }
            )

            // Botón para cambiar el mapa base
            FloatingActionButton(
                onClick = { showBaseMapDialog = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Map, "Cambiar mapa base")
            }

            // Botón de modo consulta ráster
            if (uiState.visibleLayers.any { it.type == LayerType.RASTER && it.isVisible }) {
                FloatingActionButton(
                    onClick = { viewModel.toggleRasterQueryMode() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
                    containerColor = if (uiState.isRasterQueryMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (uiState.isRasterQueryMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Default.TouchApp, "Consulta ráster")
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                // Controles de Zoom y Escala
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(),
                            onClick = { showZoomButtons = true }
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnimatedVisibility(showZoomButtons, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                MapControlButton("−") { viewModel.zoomOut(); showZoomButtons = true }
                            }
                            AnimatedVisibility(showZoomButtons, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                MapControlButton("+") { viewModel.zoomIn(); showZoomButtons = true }
                            }
                        }
                        Text(
                            text = CoordinateUtils.formatScaleWithThousandsAndZoom(
                                CoordinateUtils.calculateMapScale(uiState.center.latitude, uiState.zoom.toInt()),
                                uiState.zoom.toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }

                // Display de coordenadas
                CoordinateDisplay(
                    center = uiState.center,
                    zoomLevel = uiState.zoom.toInt(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Botón Mi Ubicación
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 30.dp, bottom = 26.dp)
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (locationPermissions.allPermissionsGranted) {
                                    if (!uiState.isMyLocationEnabled) viewModel.setMyLocationEnabled(true)
                                    myLocationOverlay?.myLocation?.let { mapView?.controller?.animateTo(it) }
                                } else {
                                    locationPermissions.launchMultiplePermissionRequest()
                                }
                            },
                            onLongPress = {
                                if (locationPermissions.allPermissionsGranted) {
                                    viewModel.toggleFollowingLocation()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    locationPermissions.launchMultiplePermissionRequest()
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(24.dp),
                color = if (uiState.isFollowingLocation) MaterialTheme.colorScheme.primary else Color.White,
                shadowElevation = 4.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            uiState.isFollowingLocation -> Icons.Default.MyLocation
                            uiState.isMyLocationEnabled -> Icons.Default.GpsFixed
                            else -> Icons.Default.GpsNotFixed
                        },
                        contentDescription = "Mi ubicación",
                        tint = if (uiState.isFollowingLocation) Color.White else Color.Black
                    )
                }
            }

            // Otros componentes de UI (paneles, diálogos, etc.)
            if (showBaseMapDialog) {
                BaseMapSelectionDialog(
                    currentBaseMap = uiState.baseMapType,
                    onBaseMapSelected = { viewModel.setBaseMapType(it) },
                    onDismiss = { showBaseMapDialog = false }
                )
            }

            if (locationPermissions.shouldShowRationale) {
                LocationPermissionRationale(
                    onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() },
                    onDismiss = {}
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView?.onDetach() }
    }
}

@Composable
private fun MapControlButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BaseMapSelectionDialog(
    currentBaseMap: BaseMapType,
    onBaseMapSelected: (BaseMapType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Mapa Base") },
        text = {
            LazyColumn {
                items(BaseMapType.entries) { baseMapType ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(),
                                onClick = { onBaseMapSelected(baseMapType); onDismiss() }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentBaseMap == baseMapType,
                            onClick = { onBaseMapSelected(baseMapType); onDismiss() }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = getBaseMapName(baseMapType), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun getBaseMapName(baseMapType: BaseMapType): String {
    return when (baseMapType) {
        BaseMapType.STREET -> "Callejero (OSM)"
        BaseMapType.TOPO -> "Topográfico (Mundial)"
        BaseMapType.SATELLITE -> "Satélite (ESRI)"
        BaseMapType.IGN_BASE -> "Mapa base (IGN)"
        BaseMapType.IGN_RASTER -> "MTN25K raster (IGN)"
        BaseMapType.IGN_PNOA -> "Ortofotos (IGN)"
//        BaseMapType.IGN_MDT -> "MDT (IGN)"
//        BaseMapType.IGN_RELIEVE -> "Sombreado de relieve (IGN)"
//        BaseMapType.IGN_LIDAR -> "LiDAR (IGN)"
//        BaseMapType.ITACYL_ORTO -> "Ortofotos (ITACyL)"
        BaseMapType.IDECYL_ORTO -> "Ortofotos (IDECyL)"
        BaseMapType.IDECYL_TOPO -> "Topográfico (IDECyL)"
    }
}

@Composable
fun LocationPermissionRationale(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permisos de ubicación necesarios") },
        text = { Text("DasoMaps necesita acceso a tu ubicación para mostrarte tu posición en el mapa y permitirte capturar geometrías con datos de ubicación precisos.") },
        confirmButton = { Button(onClick = onRequestPermission) { Text("Conceder permisos") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
