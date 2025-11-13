package com.dasomaps.app.utils

import android.content.Context
import android.os.Environment
import com.dasomaps.app.data.model.GeoTIFFInfo
import timber.log.Timber
import java.io.File

/**
 * Gestor de archivos GeoTIFF para la aplicación.
 * 
 * Proporciona funcionalidades de alto nivel para:
 * - Buscar archivos GeoTIFF en el dispositivo
 * - Validar archivos GeoTIFF
 * - Importar archivos al directorio de la app
 * - Gestionar caché de metadatos
 * 
 * Uso:
 * ```
 * val manager = GeoTIFFManager(context)
 * val files = manager.findGeoTIFFFiles()
 * val isValid = manager.validateGeoTIFF(file)
 * val imported = manager.importGeoTIFF(sourceFile, layerName)
 * ```
 */
class GeoTIFFManager(private val context: Context) {

    private val reader = GeoTIFFReader(context)
    
    // Directorio donde se almacenan los GeoTIFF de la app
    private val geoTiffDirectory: File by lazy {
        File(context.filesDir, "geotiff").apply {
            if (!exists()) {
                mkdirs()
                Timber.d("Directorio GeoTIFF creado: $absolutePath")
            }
        }
    }

    companion object {
        // Extensiones válidas
        private val VALID_EXTENSIONS = setOf("tif", "tiff", "gtif", "geotiff")
        
        // Tamaño máximo de archivo (500 MB)
        private const val MAX_FILE_SIZE_MB = 500
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L
        
        // Directorios comunes donde buscar
        private val SEARCH_DIRECTORIES = listOf(
            "Download",
            "Downloads",
            "Documents",
            "DCIM",
            "Pictures"
        )
    }

    /**
     * Información sobre un archivo GeoTIFF encontrado.
     */
    data class GeoTIFFFileInfo(
        val file: File,
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val sizeMB: Double,
        val isValid: Boolean,
        val metadata: GeoTIFFInfo? = null
    ) {
        val displaySize: String
            get() = when {
                sizeMB < 1.0 -> String.format("%.1f KB", sizeBytes / 1024.0)
                sizeMB < 100.0 -> String.format("%.1f MB", sizeMB)
                else -> String.format("%.0f MB", sizeMB)
            }
    }

    /**
     * Busca archivos GeoTIFF en directorios comunes del dispositivo.
     * 
     * @return Lista de archivos encontrados con su información
     */
    fun findGeoTIFFFiles(): List<GeoTIFFFileInfo> {
        Timber.d("Buscando archivos GeoTIFF en el dispositivo...")
        
        val foundFiles = mutableListOf<GeoTIFFFileInfo>()
        
        // Buscar en almacenamiento externo
        val externalStorage = Environment.getExternalStorageDirectory()
        if (externalStorage.exists() && externalStorage.canRead()) {
            SEARCH_DIRECTORIES.forEach { dirName ->
                val dir = File(externalStorage, dirName)
                if (dir.exists() && dir.canRead()) {
                    findGeoTIFFInDirectory(dir, foundFiles)
                }
            }
        }
        
        // Buscar en directorio de la app
        if (geoTiffDirectory.exists()) {
            findGeoTIFFInDirectory(geoTiffDirectory, foundFiles)
        }
        
        Timber.d("Encontrados ${foundFiles.size} archivos GeoTIFF")
        return foundFiles.sortedByDescending { it.file.lastModified() }
    }

    /**
     * Busca archivos GeoTIFF en un directorio específico.
     */
    private fun findGeoTIFFInDirectory(directory: File, results: MutableList<GeoTIFFFileInfo>) {
        try {
            directory.listFiles()?.forEach { file ->
                try {
                    if (file.isFile && isGeoTIFFFile(file)) {
                        val sizeBytes = file.length()
                        
                        // Filtrar archivos muy grandes
                        if (sizeBytes <= MAX_FILE_SIZE_BYTES) {
                            val isValid = reader.isValidGeoTIFF(file)
                            
                            results.add(
                                GeoTIFFFileInfo(
                                    file = file,
                                    name = file.name,
                                    path = file.absolutePath,
                                    sizeBytes = sizeBytes,
                                    sizeMB = sizeBytes / (1024.0 * 1024.0),
                                    isValid = isValid
                                )
                            )
                            
                            Timber.d("Encontrado: ${file.name} (${sizeBytes / 1024} KB) - Válido: $isValid")
                        } else {
                            Timber.w("Archivo muy grande omitido: ${file.name} (${sizeBytes / (1024 * 1024)} MB)")
                        }
                    } else if (file.isDirectory && file.canRead()) {
                        // Buscar recursivamente (hasta 2 niveles de profundidad)
                        findGeoTIFFInDirectory(file, results)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error al procesar archivo: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error al leer directorio: ${directory.absolutePath}")
        }
    }

    /**
     * Verifica si un archivo tiene extensión de GeoTIFF.
     */
    private fun isGeoTIFFFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in VALID_EXTENSIONS
    }

    /**
     * Valida un archivo GeoTIFF y obtiene sus metadatos.
     * 
     * @param file Archivo a validar
     * @return Información del archivo con metadatos o null si no es válido
     */
    fun validateGeoTIFF(file: File): GeoTIFFFileInfo? {
        return try {
            if (!file.exists() || !file.canRead()) {
                Timber.w("Archivo no existe o no se puede leer: ${file.name}")
                return null
            }
            
            val isValid = reader.isValidGeoTIFF(file)
            
            if (!isValid) {
                return GeoTIFFFileInfo(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    sizeMB = file.length() / (1024.0 * 1024.0),
                    isValid = false
                )
            }
            
            // Leer metadatos
            val metadata = reader.readMetadata(file)
            
            GeoTIFFFileInfo(
                file = file,
                name = file.name,
                path = file.absolutePath,
                sizeBytes = file.length(),
                sizeMB = file.length() / (1024.0 * 1024.0),
                isValid = true,
                metadata = metadata
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error al validar GeoTIFF: ${file.name}")
            null
        }
    }

    /**
     * Importa un archivo GeoTIFF al directorio de la aplicación.
     * 
     * @param sourceFile Archivo GeoTIFF de origen
     * @param layerName Nombre para la capa (usado para el nombre del archivo destino)
     * @return Archivo importado o null si hay error
     */
    fun importGeoTIFF(sourceFile: File, layerName: String): File? {
        return try {
            Timber.d("Importando GeoTIFF: ${sourceFile.name} como $layerName")
            
            // Validar archivo origen
            if (!reader.isValidGeoTIFF(sourceFile)) {
                Timber.w("Archivo no es un GeoTIFF válido: ${sourceFile.name}")
                return null
            }
            
            // Crear nombre de archivo destino
            val sanitizedName = layerName
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(50)
            
            val destFile = File(geoTiffDirectory, "${sanitizedName}.tif")
            
            // Si ya existe, añadir timestamp
            val finalDestFile = if (destFile.exists()) {
                val timestamp = System.currentTimeMillis()
                File(geoTiffDirectory, "${sanitizedName}_${timestamp}.tif")
            } else {
                destFile
            }
            
            // Copiar archivo
            sourceFile.copyTo(finalDestFile, overwrite = false)
            
            Timber.d("GeoTIFF importado: ${finalDestFile.name}")
            return finalDestFile
            
        } catch (e: Exception) {
            Timber.e(e, "Error al importar GeoTIFF: ${sourceFile.name}")
            null
        }
    }

    /**
     * Elimina un archivo GeoTIFF del directorio de la aplicación.
     * 
     * @param file Archivo a eliminar
     * @return true si se eliminó exitosamente
     */
    fun deleteGeoTIFF(file: File): Boolean {
        return try {
            if (file.parentFile?.absolutePath == geoTiffDirectory.absolutePath) {
                val deleted = file.delete()
                if (deleted) {
                    Timber.d("GeoTIFF eliminado: ${file.name}")
                }
                deleted
            } else {
                Timber.w("Solo se pueden eliminar archivos del directorio de la app")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar GeoTIFF: ${file.name}")
            false
        }
    }

    /**
     * Obtiene todos los archivos GeoTIFF importados en la app.
     * 
     * @return Lista de archivos en el directorio de la app
     */
    fun getImportedGeoTIFFs(): List<File> {
        return try {
            geoTiffDirectory.listFiles { file ->
                file.isFile && isGeoTIFFFile(file)
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error al listar GeoTIFFs importados")
            emptyList()
        }
    }

    /**
     * Obtiene información detallada de todos los GeoTIFF importados.
     */
    fun getImportedGeoTIFFsInfo(): List<GeoTIFFFileInfo> {
        return getImportedGeoTIFFs().mapNotNull { file ->
            validateGeoTIFF(file)
        }
    }

    /**
     * Limpia archivos GeoTIFF temporales o inválidos.
     * 
     * @return Número de archivos eliminados
     */
    fun cleanupInvalidFiles(): Int {
        var count = 0
        
        try {
            getImportedGeoTIFFs().forEach { file ->
                if (!reader.isValidGeoTIFF(file)) {
                    if (file.delete()) {
                        count++
                        Timber.d("Archivo inválido eliminado: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al limpiar archivos inválidos")
        }
        
        return count
    }

    /**
     * Obtiene el tamaño total ocupado por los GeoTIFF importados.
     * 
     * @return Tamaño en MB
     */
    fun getTotalStorageUsedMB(): Double {
        return try {
            val totalBytes = getImportedGeoTIFFs().sumOf { it.length() }
            totalBytes / (1024.0 * 1024.0)
        } catch (e: Exception) {
            Timber.e(e, "Error al calcular almacenamiento usado")
            0.0
        }
    }

    /**
     * Verifica si hay espacio disponible para importar un archivo.
     * 
     * @param fileSize Tamaño del archivo en bytes
     * @return true si hay espacio disponible
     */
    fun hasStorageSpace(fileSize: Long): Boolean {
        return try {
            val usableSpace = geoTiffDirectory.usableSpace
            usableSpace > fileSize * 1.1  // 10% de margen
        } catch (e: Exception) {
            Timber.e(e, "Error al verificar espacio disponible")
            false
        }
    }

    /**
     * Obtiene una descripción legible del almacenamiento usado.
     */
    fun getStorageInfo(): String {
        val usedMB = getTotalStorageUsedMB()
        val fileCount = getImportedGeoTIFFs().size
        
        return buildString {
            append("$fileCount archivo${if (fileCount != 1) "s" else ""}")
            if (usedMB > 0) {
                append(" (${String.format("%.1f", usedMB)} MB)")
            }
        }
    }
}
