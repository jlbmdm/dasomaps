package com.dasomaps.app.ui.layers

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dasomaps.app.utils.GeoTIFFManager
import timber.log.Timber
import java.io.File

/**
 * Diálogo para añadir archivos GeoTIFF a la aplicación.
 * 
 * Permite al usuario:
 * 1. Ver lista de archivos GeoTIFF disponibles en el dispositivo
 * 2. Seleccionar archivos usando el selector del sistema
 * 3. Ver preview de metadatos antes de añadir
 * 4. Importar el GeoTIFF seleccionado
 * 
 * @param onDismiss Callback cuando se cierra el diálogo
 * @param onGeoTIFFSelected Callback cuando se selecciona un GeoTIFF con (file, layerName)
 * @param modifier Modificador opcional
 */
@Composable
fun AddGeoTIFFDialog(
    onDismiss: () -> Unit,
    onGeoTIFFSelected: (File, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var availableFiles by remember { mutableStateOf<List<GeoTIFFManager.GeoTIFFFileInfo>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<GeoTIFFManager.GeoTIFFFileInfo?>(null) }
    var layerName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Launcher para seleccionar archivos del sistema
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    isLoading = true
                    
                    // Convertir URI a File temporal
                    val file = uriToTempFile(context, uri)
                    
                    if (file != null) {
                        // Validar con GeoTIFFManager
                        val manager = GeoTIFFManager(context)
                        val fileInfo = manager.validateGeoTIFF(file)
                        
                        withContext(Dispatchers.Main) {
                            if (fileInfo != null && fileInfo.isValid) {
                                selectedFile = fileInfo
                                layerName = fileInfo.name.substringBeforeLast(".")
                                Timber.d("✅ Archivo seleccionado: ${fileInfo.name}")
                            } else {
                                errorMessage = "El archivo no es un GeoTIFF válido"
                                Timber.w("❌ Archivo no válido: ${file.name}")
                            }
                            isLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "No se pudo acceder al archivo"
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Error al leer archivo: ${e.message}"
                        isLoading = false
                    }
                    Timber.e(e, "Error al seleccionar archivo")
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Añadir GeoTIFF",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Descripción
                Text(
                    text = "Selecciona un archivo GeoTIFF para añadir como capa ráster",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    // Loading
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (selectedFile != null) {
                    // Vista de archivo seleccionado
                    SelectedFileView(
                        fileInfo = selectedFile!!,
                        layerName = layerName,
                        onLayerNameChange = { layerName = it },
                        showMetadata = showMetadata,
                        onToggleMetadata = { showMetadata = !showMetadata },
                        onClearSelection = {
                            selectedFile = null
                            layerName = ""
                            showMetadata = false
                        }
                    )
                } else {
                    // Lista de archivos disponibles
                    AvailableFilesView(
                        files = availableFiles,
                        onFileSelected = { file ->
                            selectedFile = file
                            layerName = file.name.substringBeforeLast(".")
                        },
                        onSelectFromSystem = {
                            filePickerLauncher.launch("*/*")
                        },
                        errorMessage = errorMessage,
                        onClearError = { errorMessage = null }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            selectedFile?.let { file ->
                                if (layerName.isNotBlank()) {
                                    onGeoTIFFSelected(file.file, layerName.trim())
                                }
                            }
                        },
                        enabled = selectedFile != null && layerName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Añadir")
                    }
                }
            }
        }
    }
}

/**
 * Vista de archivo seleccionado con metadatos.
 */
@Composable
private fun SelectedFileView(
    fileInfo: GeoTIFFManager.GeoTIFFFileInfo,
    layerName: String,
    onLayerNameChange: (String) -> Unit,
    showMetadata: Boolean,
    onToggleMetadata: () -> Unit,
    onClearSelection: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card con info del archivo
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (fileInfo.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (fileInfo.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text(
                            text = fileInfo.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = fileInfo.displaySize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Clear, "Deseleccionar")
                }
            }
        }

        // Campo de nombre de capa
        OutlinedTextField(
            value = layerName,
            onValueChange = onLayerNameChange,
            label = { Text("Nombre de la capa") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Layers, contentDescription = null)
            }
        )

        // Botón para mostrar/ocultar metadatos
        if (fileInfo.metadata != null) {
            OutlinedButton(
                onClick = onToggleMetadata,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (showMetadata) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showMetadata) "Ocultar metadatos" else "Ver metadatos")
            }

            // Metadatos expandibles
            if (showMetadata) {
                MetadataView(fileInfo.metadata)
            }
        }
    }
}

/**
 * Vista de metadatos del GeoTIFF.
 */
@Composable
private fun MetadataView(metadata: com.dasomaps.app.data.model.GeoTIFFInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Metadatos",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            MetadataRow("Dimensiones", "${metadata.width} × ${metadata.height} px")
            MetadataRow("Bandas", metadata.bandCount.toString())
            MetadataRow("Tipo de dato", metadata.dataType.toString())
            MetadataRow("Tamaño estimado", String.format("%.2f MB", metadata.estimatedSizeMB()))
            
            if (metadata.noDataValue != null) {
                MetadataRow("NoData", metadata.noDataValue.toString())
            }
            
            metadata.compression?.let {
                MetadataRow("Compresión", it)
            }

            // Bounds
            Text(
                text = "Límites geográficos:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            MetadataRow("  Min Lon", String.format("%.6f°", metadata.bounds.minLon))
            MetadataRow("  Min Lat", String.format("%.6f°", metadata.bounds.minLat))
            MetadataRow("  Max Lon", String.format("%.6f°", metadata.bounds.maxLon))
            MetadataRow("  Max Lat", String.format("%.6f°", metadata.bounds.maxLat))
        }
    }
}

/**
 * Fila de metadatos.
 */
@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Convierte un URI del selector de archivos a un archivo temporal.
 * 
 * @param context Contexto de la aplicación
 * @param uri URI del archivo seleccionado
 * @return File temporal o null si hay error
 */
private fun uriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        
        if (inputStream != null) {
            // Crear archivo temporal
            val tempFile = File.createTempFile(
                "geotiff_temp_",
                ".tif",
                context.cacheDir
            )
            
            // Copiar contenido
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Timber.d("✅ Archivo temporal creado: ${tempFile.name} (${tempFile.length()} bytes)")
            tempFile
        } else {
            Timber.w("❌ No se pudo abrir InputStream para URI: $uri")
            null
        }
    } catch (e: Exception) {
        Timber.e(e, "❌ Error al convertir URI a File")
        null
    }
}

/**
 * Vista de archivos disponibles.
 */
@Composable
private fun AvailableFilesView(
    files: List<GeoTIFFManager.GeoTIFFFileInfo>,
    onFileSelected: (GeoTIFFManager.GeoTIFFFileInfo) -> Unit,
    onSelectFromSystem: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Botón para seleccionar desde el sistema
        Button(
            onClick = onSelectFromSystem,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Seleccionar archivo GeoTIFF")
        }

        // Mensaje de error si existe
        errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    IconButton(
                        onClick = onClearError,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (files.isNotEmpty()) {
            // Lista de archivos
            Text(
                text = "Archivos encontrados (${files.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    FileItem(
                        fileInfo = file,
                        onClick = { onFileSelected(file) }
                    )
                }
            }
        }

        // Nota informativa
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Los archivos GeoTIFF deben estar en WGS84 (EPSG:4326). " +
                           "Si tus archivos están en UTM u otro CRS, transfórmalos en QGIS primero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Item de archivo en la lista.
 */
@Composable
private fun FileItem(
    fileInfo: GeoTIFFManager.GeoTIFFFileInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (fileInfo.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (fileInfo.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = fileInfo.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = fileInfo.displaySize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Seleccionar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
