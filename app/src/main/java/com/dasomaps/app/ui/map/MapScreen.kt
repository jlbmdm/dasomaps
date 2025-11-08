package com.dasomaps.app.ui.map

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dasomaps.app.data.local.DasoMapsDatabase
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.model.LocationState
import com.dasomaps.app.data.repository.LayerRepository
import com.dasomaps.app.utils.Constants
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import timber.log.Timber
import java.io.File

/**
 * Pantalla principal del mapa.
 * 
 * Muestra un mapa interactivo con osmdroid y controles para:
 * - Zoom in/out
 * - Centrar en ubicación del usuario
 * - Visualización de capas MBTiles con opacidad
 * - Barra de escala en la parte inferior
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val database = remember { DasoMapsDatabase.getInstance(context) }
    val repository = remember { LayerRepository(database.layerDao()) }
    val viewModel = remember { MapViewModel(repository) }
    
    val uiState by viewModel.uiState.collectAsState()

    // Estado para mantener referencia al MapView
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var scaleBarOverlay by remember { mutableStateOf<ScaleBarOverlay?>(null) }
    
    // Mapa de overlays de MBTiles (layerId -> TilesOverlay)
    val mbtilesOverlays = remember { mutableMapOf<String, TilesOverlay>() }

    // Gestión de permisos de ubicación
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Efecto para actualizar el centro del mapa cuando cambia en el ViewModel
    LaunchedEffect(uiState.center) {
        mapView?.controller?.setCenter(uiState.center)
    }

    // Efecto para actualizar el zoom cuando cambia en el ViewModel
    LaunchedEffect(uiState.zoom) {
        mapView?.controller?.setZoom(uiState.zoom)
    }

    // Efecto para habilitar/deshabilitar la ubicación del usuario
    LaunchedEffect(uiState.isMyLocationEnabled) {
        myLocationOverlay?.let { overlay ->
            if (uiState.isMyLocationEnabled) {
                overlay.enableMyLocation()
                overlay.enableFollowLocation()
                Timber.d("Ubicación del usuario habilitada")
            } else {
                overlay.disableMyLocation()
                overlay.disableFollowLocation()
                Timber.d("Ubicación del usuario deshabilitada")
            }
        }
    }

    // Efecto para actualizar overlays de capas MBTiles
    LaunchedEffect(uiState.visibleLayers) {
        mapView?.let { map ->
            // Obtener capas MBTiles del estado
            val mbtilesLayers = uiState.visibleLayers.filter { it.type == LayerType.MBTILES }
            
            // Obtener IDs actuales y nuevas
            val currentLayerIds = mbtilesOverlays.keys.toSet()
            val newLayerIds = mbtilesLayers.map { it.id }.toSet()
            
            // Eliminar overlays de capas que ya no están visibles
            val layersToRemove = currentLayerIds - newLayerIds
            layersToRemove.forEach { layerId ->
                mbtilesOverlays[layerId]?.let { overlay ->
                    map.overlays.remove(overlay)
                    mbtilesOverlays.remove(layerId)
                    Timber.d("Overlay removido: $layerId")
                }
            }
            
            // Añadir o actualizar overlays para capas visibles
            mbtilesLayers.forEach { layer ->
                if (layer.isVisible && layer.localPath != null) {
                    val file = File(layer.localPath)
                    
                    if (file.exists()) {
                        // Si el overlay ya existe, solo actualizar opacidad
                        val existingOverlay = mbtilesOverlays[layer.id]
                        if (existingOverlay != null) {
                            // Actualizar opacidad
                            existingOverlay.setColorFilter(
                                android.graphics.PorterDuffColorFilter(
                                    android.graphics.Color.argb((layer.opacity * 255).toInt(), 255, 255, 255),
                                    android.graphics.PorterDuff.Mode.DST_IN
                                )
                            )
                            map.invalidate()
                            Timber.d("Opacidad actualizada para: ${layer.name} -> ${layer.opacity}")
                        } else {
                            // Crear nuevo overlay
                            try {
                                // Abrir el archivo MBTiles usando ArchiveFileFactory
                                val archiveFile = ArchiveFileFactory.getArchiveFile(file)
                                if (archiveFile != null) {
                                    // Crear el módulo provider para el archivo
                                    val moduleProvider = MapTileFileArchiveProvider(
                                        SimpleRegisterReceiver(context),
                                        TileSourceFactory.DEFAULT_TILE_SOURCE,
                                        arrayOf(archiveFile)
                                    )
                                    
                                    // Envolver el módulo en un MapTileProviderArray
                                    // Este es el tipo correcto que TilesOverlay espera
                                    val tileProvider = MapTileProviderArray(
                                        TileSourceFactory.DEFAULT_TILE_SOURCE,
                                        SimpleRegisterReceiver(context),
                                        arrayOf<MapTileModuleProviderBase>(moduleProvider)
                                    )
                                    
                                    // Crear el overlay con el provider
                                    val overlay = TilesOverlay(tileProvider, context)
                                    
                                    // Aplicar opacidad inicial
                                    overlay.setColorFilter(
                                        android.graphics.PorterDuffColorFilter(
                                            android.graphics.Color.argb((layer.opacity * 255).toInt(), 255, 255, 255),
                                            android.graphics.PorterDuff.Mode.DST_IN
                                        )
                                    )
                                    
                                    // Añadir al mapa (antes del overlay de ubicación)
                                    val locationOverlayIndex = map.overlays.indexOfFirst { it is MyLocationNewOverlay }
                                    if (locationOverlayIndex >= 0) {
                                        map.overlays.add(locationOverlayIndex, overlay)
                                    } else {
                                        map.overlays.add(overlay)
                                    }
                                    
                                    mbtilesOverlays[layer.id] = overlay
                                    map.invalidate()
                                    
                                    Timber.d("Overlay MBTiles creado: ${layer.name} desde ${file.name}")
                                } else {
                                    Timber.w("No se pudo abrir archivo MBTiles: ${file.name}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error al crear overlay MBTiles: ${layer.name}")
                            }
                        }
                    } else {
                        Timber.w("Archivo MBTiles no existe: ${layer.localPath}")
                    }
                } else if (!layer.isVisible) {
                    // Remover overlay si la capa está oculta
                    mbtilesOverlays[layer.id]?.let { overlay ->
                        map.overlays.remove(overlay)
                        mbtilesOverlays.remove(layer.id)
                        map.invalidate()
                        Timber.d("Overlay ocultado: ${layer.name}")
                    }
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            MapFloatingButtons(
                onZoomIn = { viewModel.zoomIn() },
                onZoomOut = { viewModel.zoomOut() },
                onMyLocation = {
                    if (locationPermissions.allPermissionsGranted) {
                        viewModel.setMyLocationEnabled(true)
                        viewModel.centerOnMyLocation()
                    } else {
                        locationPermissions.launchMultiplePermissionRequest()
                    }
                },
                isMyLocationEnabled = uiState.isMyLocationEnabled
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mapa osmdroid
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        // Configuración básica del mapa
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        
                        // Configurar límites de zoom para permitir máximo zoom
                        minZoomLevel = Constants.Map.MIN_ZOOM
                        maxZoomLevel = Constants.Map.MAX_ZOOM
                        
                        // Configurar el controlador del mapa
                        controller.setZoom(uiState.zoom)
                        controller.setCenter(uiState.center)
                        
                        // Overlay de ubicación del usuario
                        val locationOverlay = MyLocationNewOverlay(
                            GpsMyLocationProvider(ctx),
                            this
                        )
                        locationOverlay.enableMyLocation()
                        overlays.add(locationOverlay)
                        myLocationOverlay = locationOverlay
                        
                        // Overlay de barra de escala (parte inferior)
                        val scaleBar = ScaleBarOverlay(this)
                        scaleBar.setCentred(true)
                        // Posicionar en la parte inferior, centrado
                        scaleBar.setScaleBarOffset(
                            (ctx.resources.displayMetrics.widthPixels / 2), 
                            10  // 10 píxeles desde abajo
                        )
                        overlays.add(scaleBar)
                        scaleBarOverlay = scaleBar
                        
                        mapView = this
                        
                        Timber.d("MapView creado y configurado con zoom máximo: $maxZoomLevel")
                    }
                },
                update = { view ->
                    // Actualizar el mapa si es necesario
                    view.invalidate()
                }
            )

            // Indicador de capas activas (esquina superior izquierda)
            val mbtilesLayersCount = uiState.visibleLayers.count { it.type == LayerType.MBTILES && it.isVisible }
            if (mbtilesLayersCount > 0) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$mbtilesLayersCount capa${if (mbtilesLayersCount > 1) "s" else ""} MBTiles activa${if (mbtilesLayersCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Mensaje de error si existe
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Indicador de carga
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }

            // Diálogo de permisos si son necesarios
            if (locationPermissions.shouldShowRationale) {
                LocationPermissionRationale(
                    onRequestPermission = {
                        locationPermissions.launchMultiplePermissionRequest()
                    },
                    onDismiss = {
                        // El usuario canceló la solicitud de permisos
                    }
                )
            }
        }
    }

    // Limpiar recursos cuando se destruye la vista
    DisposableEffect(Unit) {
        onDispose {
            // Limpiar overlays de MBTiles
            mbtilesOverlays.values.forEach { overlay ->
                overlay.onDetach(mapView)
            }
            mbtilesOverlays.clear()
            
            mapView?.onDetach()
            Timber.d("MapView y overlays liberados")
        }
    }
}

/**
 * Botones flotantes para controlar el mapa.
 */
@Composable
fun MapFloatingButtons(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    isMyLocationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botón de Mi Ubicación
        FloatingActionButton(
            onClick = onMyLocation,
            containerColor = if (isMyLocationEnabled) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surface
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Mi ubicación"
            )
        }

        // Botón Zoom In
        FloatingActionButton(
            onClick = onZoomIn,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Aumentar zoom"
            )
        }

        // Botón Zoom Out
        FloatingActionButton(
            onClick = onZoomOut,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Disminuir zoom"
            )
        }
    }
}

/**
 * Diálogo explicativo para solicitar permisos de ubicación.
 */
@Composable
fun LocationPermissionRationale(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Permisos de ubicación necesarios")
        },
        text = {
            Text(
                "DasoMaps necesita acceso a tu ubicación para mostrarte tu posición " +
                "en el mapa y permitirte capturar geometrías con datos de ubicación precisos."
            )
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Conceder permisos")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
