package com.dasomaps.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dasomaps.app.utils.CoordinateUtils
import org.osmdroid.util.GeoPoint

/**
 * Componente que muestra las coordenadas actuales del centro del mapa.
 * 
 * Diseño horizontal con dos cajas:
 * - Izquierda: Coordenadas geográficas (WGS84)
 * - Derecha: Coordenadas UTM con escala en número (con miles y zoom de Google)
 * 
 * Fondo transparente para no ocultar el mapa en exceso.
 */
@Composable
fun CoordinateDisplay(
    center: GeoPoint,
    zoomLevel: Int = 15,
    modifier: Modifier = Modifier
) {
    val coordInfo = remember(center, zoomLevel) {
        CoordinateUtils.getCoordinateInfo(center, zoomLevel)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Caja izquierda: Coordenadas Geográficas (WGS84)
        Card(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "WGS84",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = coordInfo.latitudeStr,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = coordInfo.longitudeStr,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Caja derecha: Coordenadas UTM con escala formateada (con miles y zoom)
        Card(
            modifier = Modifier
                .weight(1.5f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "UTM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = coordInfo.utmStr,
                    style = MaterialTheme.typography.bodySmall
                )
                // Tercera línea: Escala con miles y zoom
                Text(
                    text = coordInfo.scaleFormattedWithZoom,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}
