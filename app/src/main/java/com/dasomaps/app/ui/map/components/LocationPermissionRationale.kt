package com.dasomaps.app.ui.map.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Diálogo que muestra una justificación al usuario sobre por qué se necesita
 * el permiso de ubicación.
 *
 * @param onRequestPermission Lambda para volver a solicitar el permiso.
 * @param onDismiss Lambda para descartar el diálogo.
 */
@Composable
fun LocationPermissionRationale(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permiso de Ubicación Necesario") },
        text = {
            Text(
                "DasoMaps necesita acceso a tu ubicación para poder mostrar tu posición en el mapa " +
                "y centrar la vista en ella. Por favor, concede el permiso para usar esta funcionalidad."
            )
        },
        confirmButton = {
            TextButton(onClick = onRequestPermission) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
