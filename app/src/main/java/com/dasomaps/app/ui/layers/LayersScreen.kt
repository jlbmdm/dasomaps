package com.dasomaps.app.ui.layers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dasomaps.app.data.local.DasoMapsDatabase
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.repository.LayerRepository
import timber.log.Timber
import androidx.compose.ui.platform.LocalContext

/**
 * Pantalla de gestión de capas.
 * 
 * Permite visualizar y gestionar todas las capas disponibles:
 * - Ver lista de capas
 * - Activar/desactivar capas
 * - Ajustar opacidad
 * - Filtrar por tipo
 * - Añadir nuevas capas MBTiles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersScreen() {
    val context = LocalContext.current
    val database = remember { DasoMapsDatabase.getInstance(context) }
    val repository = remember { LayerRepository(database.layerDao()) }
    val viewModel = remember { LayersViewModel(repository) }
    
    val uiState by viewModel.uiState.collectAsState()
    var showAddMBTilesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capas") },
                actions = {
                    // Botón para crear capas de ejemplo
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
            FloatingActionButton(
                onClick = { showAddMBTilesDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir capa MBTiles"
                )
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
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
                    Column {
                        // Filtros de tipo
                        LayerTypeFilters(
                            selectedType = uiState.filterType,
                            onTypeSelected = { viewModel.filterByType(it) },
                            layerCounts = LayerType.values().associateWith { type ->
                                uiState.layers.count { it.type == type }
                            }
                        )

                        // Lista de capas
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = uiState.layers,
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
}

/**
 * Vista cuando no hay capas disponibles.
 */
@Composable
fun EmptyLayersView(
    onCreateSampleLayers: () -> Unit,
    onAddMBTiles: () -> Unit,
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
            text = "Añade tu primer archivo MBTiles o crea capas de ejemplo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onAddMBTiles) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir MBTiles")
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
        LayerType.values().forEach { type ->
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
 * Item individual de capa.
 */
@Composable
fun LayerItem(
    layer: Layer,
    onVisibilityToggle: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Fila superior: nombre, tipo y controles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
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
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón de opacidad
                    IconButton(
                        onClick = { showOpacitySlider = !showOpacitySlider }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = "Ajustar opacidad"
                        )
                    }

                    // Toggle de visibilidad
                    Switch(
                        checked = layer.isVisible,
                        onCheckedChange = { onVisibilityToggle() }
                    )

                    // Botón de eliminar
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar capa",
                            tint = MaterialTheme.colorScheme.error
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
