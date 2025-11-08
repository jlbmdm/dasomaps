package com.dasomaps.app.ui.layers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.model.SyncStatus
import com.dasomaps.app.utils.MBTilesManager
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Diálogo para añadir una nueva capa MBTiles.
 * 
 * Permite al usuario:
 * - Seleccionar un archivo MBTiles del dispositivo (que se importará a la app)
 * - Seleccionar un archivo MBTiles ya disponible en directorios conocidos
 * - Personalizar el nombre de la capa antes de añadirla
 */
@Composable
fun AddMBTilesDialog(
    onDismiss: () -> Unit,
    onLayerAdded: (Layer) -> Unit
) {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var availableFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var layerName by remember { mutableStateOf("") }

    // Lanzador para seleccionar archivo del sistema
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                // Copiar el archivo seleccionado al directorio de la app
                // Esto es necesario porque la URI puede no ser accesible permanentemente
                val inputStream = context.contentResolver.openInputStream(uri)
                // Generar nombre temporal único para evitar conflictos
                val tempFileName = "imported_${System.currentTimeMillis()}.mbtiles"
                val targetFile = File(context.filesDir, tempFileName)
                
                inputStream?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (MBTilesManager.isValidMBTiles(targetFile)) {
                    selectedFile = targetFile
                    // Sugerir un nombre amigable para la capa
                    layerName = "Capa importada ${System.currentTimeMillis() % 100000}"
                    errorMessage = null
                    Timber.d("Archivo MBTiles importado: ${targetFile.absolutePath}")
                } else {
                    targetFile.delete()
                    errorMessage = "El archivo seleccionado no es un MBTiles válido"
                    Timber.w("Archivo importado no es MBTiles válido")
                }
            } catch (e: Exception) {
                errorMessage = "Error al importar archivo: ${e.message}"
                Timber.e(e, "Error al importar archivo MBTiles")
            }
        }
    }

    // Cargar archivos disponibles
    LaunchedEffect(Unit) {
        try {
            val directories = MBTilesManager.getCommonMBTilesDirectories(context)
            val files = directories.flatMap { MBTilesManager.findMBTilesFiles(it) }
            availableFiles = files
            Timber.d("Archivos MBTiles encontrados: ${files.size}")
        } catch (e: Exception) {
            errorMessage = "Error al buscar archivos: ${e.message}"
            Timber.e(e, "Error al buscar archivos MBTiles")
        } finally {
            isLoading = false
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
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Text("Añadir capa MBTiles")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mensaje de error si existe
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Botón para seleccionar archivo del sistema
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importar archivo del dispositivo")
                }

                HorizontalDivider()

                // Archivo seleccionado
                selectedFile?.let { file ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Archivo seleccionado:",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                    text = MBTilesManager.getMBTilesInfo(file)?.get("size") ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Campo de texto para editar el nombre de la capa
                        OutlinedTextField(
                            value = layerName,
                            onValueChange = { layerName = it },
                            label = { Text("Nombre de la capa") },
                            placeholder = { Text("Introduce un nombre descriptivo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null
                                )
                            },
                            supportingText = {
                                Text(
                                    text = "Este nombre aparecerá en la lista de capas",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                } ?: run {
                    // Lista de archivos disponibles
                    Text(
                        text = "Archivos MBTiles disponibles:",
                        style = MaterialTheme.typography.labelMedium
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (availableFiles.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No se encontraron archivos MBTiles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Usa el botón de arriba para importar un archivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableFiles) { file ->
                                MBTilesFileItem(
                                    file = file,
                                    onClick = {
                                        selectedFile = file
                                        // Usar el nombre del archivo sin extensión como nombre de capa por defecto
                                        layerName = file.nameWithoutExtension
                                        errorMessage = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedFile?.let { file ->
                        // Usar el nombre personalizado o el nombre del archivo si está vacío
                        val finalName = layerName.trim().ifEmpty { file.nameWithoutExtension }
                        
                        val layer = Layer(
                            id = UUID.randomUUID().toString(),
                            name = finalName,
                            type = LayerType.MBTILES,
                            isVisible = true,
                            opacity = 1.0f,
                            localPath = file.absolutePath,
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                        onLayerAdded(layer)
                        onDismiss()
                    }
                },
                enabled = selectedFile != null
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Item de archivo MBTiles en la lista.
 */
@Composable
private fun MBTilesFileItem(
    file: File,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = MBTilesManager.getMBTilesInfo(file)?.get("size") ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
