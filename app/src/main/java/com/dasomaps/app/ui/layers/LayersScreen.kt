package com.dasomaps.app.ui.layers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dasomaps.app.data.local.DasoMapsDatabase
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.repository.LayerRepository
import com.dasomaps.app.data.repository.RasterRepository
import com.dasomaps.app.ui.components.LayerInfoDialog
import com.dasomaps.app.utils.MBTilesManager
import timber.log.Timber
import java.io.File

/**
 * Pantalla de gestión de capas con mejoras.
 * 
 * Funcionalidades:
 * - Ver lista de capas
 * - Activar/desactivar capas
 * - Ajustar opacidad
 * - Filtrar por tipo
 * - Buscar capas por nombre
 * - Reordenar capas (drag & drop)
 * - Ver thumbnails de preview
 * - Añadir nuevas capas MBTiles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersScreen() {
    val context = LocalContext.current
    val database = remember { DasoMapsDatabase.getInstance(context) }
    val repository = remember { LayerRepository(database.layerDao()) }
    val rasterRepository = remember { RasterRepository(context) }
    val viewModel = remember { LayersViewModel(repository, rasterRepository) }
    
    val uiState by viewModel.uiState.collectAsState()
    var showAddMBTilesDialog by remember { mutableStateOf(false) }
    var showAddGeoTIFFDialog by remember { mutableStateOf(false) }
    var showAddLayerMenu by remember { mutableStateOf(false) }
    
    // Lista filtrada de capas (búsqueda + tipo)
    val filteredLayers = remember(uiState.layers, uiState.searchQuery) {
        viewModel.getFilteredLayers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capas") },
                actions = {
                    // Botón de modo reordenamiento
                    if (filteredLayers.isNotEmpty()) {
                        IconButton(onClick = { viewModel.toggleReorderMode() }) {
                            Icon(
                                imageVector = if (uiState.isReorderMode) Icons.Default.Check else Icons.Default.Reorder,
                                contentDescription = if (uiState.isReorderMode) "Guardar orden" else "Reordenar",
                                tint = if (uiState.isReorderMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    
                    // Botón para crear capas de ejemplo (solo si está vacío)
                    if (uiState.layers.isEmpty() && !uiState.isLoading) {
                        IconButton(onClick = { viewModel.createSampleLayers() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Crear capas de ejemplo"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Menú desplegable de opciones
                if (showAddLayerMenu) {
                    // Opción GeoTIFF
                    SmallFloatingActionButton(
                        onClick = {
                            showAddGeoTIFFDialog = true
                            showAddLayerMenu = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Image, "GeoTIFF")
                            Text("GeoTIFF")
                        }
                    }
                    
                    // Opción MBTiles
                    SmallFloatingActionButton(
                        onClick = {
                            showAddMBTilesDialog = true
                            showAddLayerMenu = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Map, "MBTiles")
                            Text("MBTiles")
                        }
                    }
                }
                
                // FAB principal
                FloatingActionButton(
                    onClick = { showAddLayerMenu = !showAddLayerMenu }
                ) {
                    Icon(
                        imageVector = if (showAddLayerMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Añadir capa"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                uiState.layers.isEmpty() -> {
                    EmptyLayersView(
                        onCreateSampleLayers = { viewModel.createSampleLayers() },
                        onAddMBTiles = { showAddMBTilesDialog = true },
                        onAddGeoTIFF = { showAddGeoTIFFDialog = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
                    Column {
                        // Barra de búsqueda
                        SearchBar(
                            query = uiState.searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        // Mensaje de modo reordenamiento
                        if (uiState.isReorderMode) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Mantén presionada una capa para arrastrarla y reordenar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Filtros de tipo
                        LayerTypeFilters(
                            selectedType = uiState.filterType,
                            onTypeSelected = { viewModel.filterByType(it) },
                            layerCounts = LayerType.entries.associateWith { type ->
                                uiState.layers.count { it.type == type }
                            }
                        )

                        // Lista de capas
                        if (uiState.isReorderMode) {
                            // Modo reordenamiento: lista con drag & drop
                            ReorderableLayersList(
                                layers = filteredLayers,
                                onReorder = { reorderedLayers ->
                                    viewModel.reorderLayers(reorderedLayers)
                                },
                                onVisibilityToggle = { layer -> viewModel.toggleLayerVisibility(layer) },
                                onOpacityChange = { layer, opacity -> viewModel.updateLayerOpacity(layer, opacity) },
                                onDelete = { layer -> viewModel.deleteLayer(layer) }
                            )
                        } else {
                            // Modo normal: lista estándar
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = filteredLayers.sortedBy { it.zIndex },
                                    key = { it.id }
                                ) { layer ->
                                    LayerItem(
                                        layer = layer,
                                        onVisibilityToggle = { viewModel.toggleLayerVisibility(layer) },
                                        onOpacityChange = { opacity -> 
                                            viewModel.updateLayerOpacity(layer, opacity) 
                                        },
                                        onDelete = { viewModel.deleteLayer(layer) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mensaje de error
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
        }
    }

    // Diálogo para añadir MBTiles
    if (showAddMBTilesDialog) {
        AddMBTilesDialog(
            onDismiss = { showAddMBTilesDialog = false },
            onLayerAdded = { layer ->
                viewModel.addLayer(layer)
                Timber.d("Capa MBTiles añadida: ${layer.name}")
            }
        )
    }

    // Diálogo para añadir GeoTIFF
    if (showAddGeoTIFFDialog) {
        AddGeoTIFFDialog(
            onDismiss = { showAddGeoTIFFDialog = false },
            onGeoTIFFSelected = { file, layerName ->
                viewModel.importGeoTIFF(file, layerName)
                showAddGeoTIFFDialog = false
            }
        )
    }

    /*
    // Diálogo de información de capa
    if (uiState.showLayerInfoDialog && uiState.lastAddedLayer != null) {
        LayerInfoDialog(
            layer = uiState.lastAddedLayer,
    */


    // Diálogo de información de capa
    val layerForInfo = uiState.lastAddedLayer
    if (uiState.showLayerInfoDialog && layerForInfo != null) {
        LayerInfoDialog(
            layer = layerForInfo,
                onDismiss = { viewModel.dismissLayerInfoDialog() },
            onZoomToLayer = { zoom ->
                // El zoom automático se maneja dentro del diálogo
                // Por ahora solo registrar en log
                // Timber.d("Zoom solicitado a nivel: $zoom para capa: ${uiState.lastAddedLayer.name}")
                Timber.d("... para capa: ${layerForInfo.name}")
            }
        )
    }
}

/**
 * Barra de búsqueda de capas.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Buscar capas...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Limpiar"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

/**
 * Lista de capas con reordenamiento (drag & drop).
 */
@Composable
fun ReorderableLayersList(
    layers: List<Layer>,
    onReorder: (List<Layer>) -> Unit,
    onVisibilityToggle: (Layer) -> Unit,
    onOpacityChange: (Layer, Float) -> Unit,
    onDelete: (Layer) -> Unit
) {
    var reorderedLayers by remember(layers) { mutableStateOf(layers.sortedBy { it.zIndex }) }
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = reorderedLayers,
            key = { _, layer -> layer.id }
        ) { index, layer ->
            val isDragging = draggingItemIndex == index
            
            LayerItem(
                layer = layer,
                onVisibilityToggle = { onVisibilityToggle(layer) },
                onOpacityChange = { opacity -> onOpacityChange(layer, opacity) },
                onDelete = { onDelete(layer) },
                isDraggable = true,
                onDragStart = { draggingItemIndex = index },
                onDragEnd = {
                    draggingItemIndex = null
                    onReorder(reorderedLayers)
                },
                onDrag = { targetIndex ->
                    if (targetIndex != index && targetIndex in reorderedLayers.indices) {
                        val mutableList = reorderedLayers.toMutableList()
                        val item = mutableList.removeAt(index)
                        mutableList.add(targetIndex, item)
                        reorderedLayers = mutableList
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isDragging) {
                            Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

/**
 * Vista cuando no hay capas disponibles.
 */
@Composable
fun EmptyLayersView(
    onCreateSampleLayers: () -> Unit,
    onAddMBTiles: () -> Unit,
    onAddGeoTIFF: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "No hay capas disponibles",
            style = MaterialTheme.typography.titleLarge
        )
        
        Text(
            text = "Añade tu primer archivo de capa o crea capas de ejemplo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onAddGeoTIFF) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir GeoTIFF")
                }
                
                OutlinedButton(onClick = onAddMBTiles) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir MBTiles")
                }
            }
            
            Button(onClick = onCreateSampleLayers) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Capas de ejemplo")
            }
        }
    }
}

/**
 * Filtros de tipo de capa (versión compacta con scroll horizontal).
 */
@Composable
fun LayerTypeFilters(
    selectedType: LayerType?,
    onTypeSelected: (LayerType?) -> Unit,
    layerCounts: Map<LayerType, Int>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filtro "Todas"
        item {
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { 
                    Text(
                        text = "Todas (${layerCounts.values.sum()})",
                        style = MaterialTheme.typography.labelMedium
                    ) 
                },
                leadingIcon = {
                    if (selectedType == null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }

        // Filtros por tipo
        LayerType.entries.forEach { type ->
            val count = layerCounts[type] ?: 0
            if (count > 0) {
                item {
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        label = { 
                            Text(
                                text = "${getLayerTypeLabel(type)} ($count)",
                                style = MaterialTheme.typography.labelMedium
                            ) 
                        },
                        leadingIcon = {
                            if (selectedType == type) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Item individual de capa con thumbnail.
 */
@Composable
fun LayerItem(
    layer: Layer,
    onVisibilityToggle: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isDraggable: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: (Int) -> Unit = {}
) {
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val cardModifier = if (isDraggable) {
        modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDragEnd = { onDragEnd() },
                onDrag = { change, _ ->
                    change.consume()
                    // Calcular índice destino basado en posición
                    val targetIndex = (change.position.y / 100).toInt()
                    onDrag(targetIndex)
                }
            )
        }
    } else {
        modifier
    }

    Card(
        modifier = cardModifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            LayerThumbnail(
                layer = layer,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            
            // Contenido principal
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Fila superior: nombre y controles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDraggable) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Arrastrar",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = getLayerTypeLabel(layer.type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (layer.isAvailableOffline()) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Disponible offline",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Mostrar zIndex en modo reordenamiento
                            if (isDraggable) {
                                Text(
                                    text = "z:${layer.zIndex}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de opacidad
                        IconButton(
                            onClick = { showOpacitySlider = !showOpacitySlider },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = "Ajustar opacidad",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Toggle de visibilidad
                        Switch(
                            checked = layer.isVisible,
                            onCheckedChange = { onVisibilityToggle() },
                            modifier = Modifier.height(36.dp)
                        )

                        // Botón de eliminar
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar capa",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Slider de opacidad (expandible)
                if (showOpacitySlider) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Opacidad:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Slider(
                            value = layer.opacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "${(layer.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar capa") },
            text = { Text("¿Estás seguro de que quieres eliminar la capa '${layer.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Thumbnail de preview de una capa.
 */
@Composable
fun LayerThumbnail(
    layer: Layer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail = remember(layer.id) {
        // Intentar generar thumbnail para capas MBTiles
        if (layer.type == LayerType.MBTILES && layer.localPath != null) {
            try {
                val file = File(layer.localPath)
                if (file.exists()) {
                    // Por ahora, mostrar un ícono genérico
                    // TODO: Implementar extracción real de tile como thumbnail
                    null
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al generar thumbnail")
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .background(
                color = when (layer.type) {
                    LayerType.MBTILES -> MaterialTheme.colorScheme.primaryContainer
                    LayerType.RASTER -> MaterialTheme.colorScheme.secondaryContainer
                    LayerType.VECTOR -> MaterialTheme.colorScheme.tertiaryContainer
                    LayerType.BASE_MAP -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Preview de ${layer.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = when (layer.type) {
                    LayerType.MBTILES -> Icons.Default.Map
                    LayerType.RASTER -> Icons.Default.Image
                    LayerType.VECTOR -> Icons.Default.Timeline
                    LayerType.BASE_MAP -> Icons.Default.Public
                },
                contentDescription = getLayerTypeLabel(layer.type),
                modifier = Modifier.size(32.dp),
                tint = when (layer.type) {
                    LayerType.MBTILES -> MaterialTheme.colorScheme.onPrimaryContainer
                    LayerType.RASTER -> MaterialTheme.colorScheme.onSecondaryContainer
                    LayerType.VECTOR -> MaterialTheme.colorScheme.onTertiaryContainer
                    LayerType.BASE_MAP -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Obtiene la etiqueta en español para un tipo de capa.
 */
private fun getLayerTypeLabel(type: LayerType): String {
    return when (type) {
        LayerType.RASTER -> "Ráster"
        LayerType.MBTILES -> "MBTiles"
        LayerType.VECTOR -> "Vectorial"
        LayerType.BASE_MAP -> "Mapa Base"
    }
}
