package com.dasomaps.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.utils.CoordinateUtils
import com.dasomaps.app.utils.MBTilesManager
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File

/**
 * Diálogo que muestra información sobre una capa recién añadida.
 * 
 * - Se cierra automáticamente después de 30 segundos
 * - Muestra nombre, tipo, bounds, zoom, etc.
 * - Permite al usuario cerrar manualmente
 */
@Composable
fun LayerInfoDialog(
    layer: Layer,
    onDismiss: () -> Unit,
    onZoomToLayer: ((Double) -> Unit)? = null
) {
    var timeRemaining by remember { mutableStateOf(30) }
    
    // Auto-dismiss después de 30 segundos
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
        onDismiss()
    }
    
    // Leer metadatos si es MBTiles
    val metadata = remember(layer.localPath) {
        if (layer.localPath != null) {
            val file = File(layer.localPath)
            if (file.exists()) {
                MBTilesManager.readMBTilesMetadata(file)
            } else {
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }
    
    // Determinar si debe hacer zoom automático
    val shouldAutoZoom = remember(layer.bounds) {
        layer.bounds?.let { bounds ->
            CoordinateUtils.isInSpainRange(bounds)
        } ?: false
    }
    
    // Auto-zoom si está en rango España
    LaunchedEffect(shouldAutoZoom) {
        if (shouldAutoZoom && onZoomToLayer != null) {
            delay(1000)  // Esperar 1 segundo antes de hacer zoom
            onZoomToLayer(10.0)  // Zoom nivel 10 como solicitaste
            Timber.d("Auto-zoom a capa en rango España con zoom 10")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("Capa Añadida")
                    Text(
                        text = "Se cerrará en ${timeRemaining}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Nombre de la capa
                InfoRow(
                    label = "Nombre",
                    value = layer.name,
                    icon = Icons.Default.Label
                )
                
                // Tipo de capa
                InfoRow(
                    label = "Tipo",
                    value = layer.type.name,
                    icon = Icons.Default.Category
                )
                
                // Formato (si es MBTiles)
                metadata["format"]?.let { format ->
                    InfoRow(
                        label = "Formato",
                        value = format.uppercase(),
                        icon = Icons.Default.Image
                    )
                }
                
                // Rango de zoom
                val zoomRange = if (metadata.isNotEmpty()) {
                    "${metadata["minzoom"] ?: "?"} - ${metadata["maxzoom"] ?: "?"}"
                } else if (layer.zoomLevels != null) {
                    "${layer.zoomLevels.first} - ${layer.zoomLevels.last}"
                } else {
                    "Desconocido"
                }
                InfoRow(
                    label = "Rango de Zoom",
                    value = zoomRange,
                    icon = Icons.Default.ZoomIn
                )
                
                // Bounds (si disponibles)
                layer.bounds?.let { bounds ->
                    val boundsStr = """
                        Lat: %.4f° a %.4f°
                        Lon: %.4f° a %.4f°
                    """.trimIndent().format(
                        bounds[1], bounds[3],  // min/max lat
                        bounds[0], bounds[2]   // min/max lon
                    )
                    
                    InfoRow(
                        label = "Coordenadas",
                        value = boundsStr,
                        icon = Icons.Default.Map
                    )
                    
                    // Indicar si está en rango España
                    if (shouldAutoZoom) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Capa en rango de España - Zoom automático aplicado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                // Tamaño del archivo
                layer.localPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val sizeStr = MBTilesManager.getMBTilesInfo(file)?.get("size") ?: "Desconocido"
                        InfoRow(
                            label = "Tamaño",
                            value = sizeStr,
                            icon = Icons.Default.Storage
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}

/**
 * Fila de información con icono, etiqueta y valor.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
