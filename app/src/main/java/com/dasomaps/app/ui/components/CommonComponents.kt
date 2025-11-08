package com.dasomaps.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Botón de acción flotante personalizado para el mapa.
 *
 * @param icon Icono a mostrar
 * @param contentDescription Descripción para accesibilidad
 * @param onClick Acción al hacer clic
 * @param modifier Modificador opcional
 * @param containerColor Color del contenedor
 */
@Composable
fun MapActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

/**
 * Columna de botones de control del mapa.
 *
 * @param onZoomIn Acción al aumentar zoom
 * @param onZoomOut Acción al disminuir zoom
 * @param onMyLocation Acción al presionar mi ubicación
 * @param isMyLocationEnabled Si la ubicación está habilitada
 * @param modifier Modificador opcional
 */
@Composable
fun MapControlButtons(
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
        MapActionButton(
            icon = Icons.Default.MyLocation,
            contentDescription = "Mi ubicación",
            onClick = onMyLocation,
            containerColor = if (isMyLocationEnabled) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surface
        )

        // Botón Zoom In
        MapActionButton(
            icon = Icons.Default.Add,
            contentDescription = "Aumentar zoom",
            onClick = onZoomIn
        )

        // Botón Zoom Out
        MapActionButton(
            icon = Icons.Default.Remove,
            contentDescription = "Disminuir zoom",
            onClick = onZoomOut
        )
    }
}

/**
 * Indicador de carga centrado.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
    }
}

/**
 * Mensaje de error con acción de cierre.
 *
 * @param message Mensaje a mostrar
 * @param onDismiss Acción al cerrar el mensaje
 * @param modifier Modificador opcional
 */
@Composable
fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    ) {
        Text(message)
    }
}

/**
 * Tarjeta de información simple.
 *
 * @param title Título de la tarjeta
 * @param content Contenido de la tarjeta
 * @param modifier Modificador opcional
 */
@Composable
fun InfoCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
