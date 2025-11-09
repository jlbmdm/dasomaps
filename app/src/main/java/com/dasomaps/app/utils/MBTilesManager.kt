package com.dasomaps.app.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import timber.log.Timber
import java.io.File

/**
 * Gestor para archivos MBTiles.
 * Proporciona funcionalidades para validar, cargar y gestionar archivos MBTiles.
 */
object MBTilesManager {

    /**
     * Valida si un archivo es un MBTiles válido.
     *
     * @param file Archivo a validar
     * @param deepValidation Si es true, realiza validación profunda de la estructura SQLite
     * @return true si es válido, false en caso contrario
     */
    fun isValidMBTiles(file: File, deepValidation: Boolean = true): Boolean {
        if (!file.exists() || !file.isFile) {
            Timber.w("Archivo no existe o no es un archivo: ${file.absolutePath}")
            return false
        }

        if (!file.name.endsWith(".mbtiles", ignoreCase = true)) {
            Timber.w("Archivo no tiene extensión .mbtiles: ${file.name}")
            return false
        }

        // Intentar abrir el archivo con ArchiveFileFactory
        return try {
            val archiveFile = ArchiveFileFactory.getArchiveFile(file)
            val isValid = archiveFile != null
            archiveFile?.close()
            
            if (isValid && deepValidation) {
                // Validación profunda: intentar leer la estructura SQLite usando API de Android
                return validateSQLiteStructure(file)
            }
            
            Timber.d("Archivo MBTiles validado: ${file.name} - válido: $isValid")
            isValid
        } catch (e: Exception) {
            Timber.e(e, "Error al validar MBTiles: ${file.name}")
            false
        }
    }
    
    /**
     * Valida la estructura SQLite de un archivo MBTiles usando la API nativa de Android.
     * Verifica que las tablas necesarias existan y no estén corruptas.
     *
     * @param file Archivo MBTiles a validar
     * @return true si la estructura es válida, false en caso contrario
     */
    private fun validateSQLiteStructure(file: File): Boolean {
        var database: SQLiteDatabase? = null
        
        return try {
            // Abrir base de datos en modo solo lectura
            database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            // Verificar que existe la tabla 'tiles'
            val cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='tiles'",
                null
            )
            
            val tableExists = cursor.use { it.moveToFirst() }
            
            if (!tableExists) {
                Timber.w("Tabla 'tiles' no encontrada en ${file.name}")
                return false
            }
            
            // Intentar leer un tile para verificar que no esté corrupta
            val testCursor = database.rawQuery(
                "SELECT tile_data FROM tiles LIMIT 1",
                null
            )
            
            testCursor.use {
                // Si llega aquí sin excepción, la tabla no está corrupta
                Timber.d("Estructura SQLite válida para ${file.name}")
                true
            }
        } catch (e: SQLiteException) {
            Timber.w(e, "Base de datos MBTiles corrupta: ${file.name}")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error al validar estructura SQLite: ${file.name}")
            false
        } finally {
            database?.close()
        }
    }

    /**
     * Lee los metadatos de un archivo MBTiles.
     *
     * @param file Archivo MBTiles
     * @return Map con los metadatos (name, format, bounds, minzoom, maxzoom, etc.)
     */
    fun readMBTilesMetadata(file: File): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        var database: SQLiteDatabase? = null
        
        try {
            database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            // Leer tabla de metadatos
            val cursor = database.rawQuery("SELECT name, value FROM metadata", null)
            
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)
                    val value = it.getString(1)
                    metadata[name] = value
                }
            }
            
            // Si no hay minzoom/maxzoom en metadatos, calcular desde tiles
            if (!metadata.containsKey("minzoom") || !metadata.containsKey("maxzoom")) {
                val zoomCursor = database.rawQuery(
                    "SELECT MIN(zoom_level) as min_zoom, MAX(zoom_level) as max_zoom FROM tiles",
                    null
                )
                
                zoomCursor.use {
                    if (it.moveToFirst()) {
                        metadata["minzoom"] = it.getString(0) ?: "0"
                        metadata["maxzoom"] = it.getString(1) ?: "18"
                    }
                }
            }
            
            Timber.d("Metadatos MBTiles: $metadata")
        } catch (e: Exception) {
            Timber.e(e, "Error al leer metadatos de MBTiles: ${file.name}")
        } finally {
            database?.close()
        }
        
        return metadata
    }

    /**
     * Crea un TileSource personalizado basado en los metadatos del MBTiles.
     *
     * @param file Archivo MBTiles
     * @param metadata Metadatos del MBTiles
     * @return TileSource configurado para el MBTiles
     */
    fun createTileSourceFromMBTiles(file: File, metadata: Map<String, String>): ITileSource {
        val name = metadata["name"] ?: file.nameWithoutExtension
        val format = metadata["format"] ?: "png"
        val minZoom = metadata["minzoom"]?.toIntOrNull() ?: 0
        val maxZoom = metadata["maxzoom"]?.toIntOrNull() ?: 18
        
        // Determinar extensión de archivo basado en formato
        val extension = when (format.lowercase()) {
            "jpg", "jpeg" -> ".jpg"
            "png" -> ".png"
            "pbf" -> ".pbf"
            "webp" -> ".webp"
            else -> ".png"
        }
        
        Timber.d("Creando TileSource: name=$name, format=$format, minZoom=$minZoom, maxZoom=$maxZoom, extension=$extension")
        
        return XYTileSource(
            name,                    // Nombre del tile source
            minZoom,                // Zoom mínimo
            maxZoom,                // Zoom máximo
            256,                    // Tamaño del tile (casi siempre 256)
            extension,              // Extensión del archivo
            arrayOf<String>()       // Base URLs (vacío para MBTiles local)
        )
    }

    /**
     * Abre un archivo MBTiles.
     *
     * @param file Archivo MBTiles a abrir
     * @return IArchiveFile o null si hay error
     */
    fun openMBTiles(file: File): IArchiveFile? {
        return try {
            val archiveFile = ArchiveFileFactory.getArchiveFile(file)
            Timber.d("Archivo MBTiles abierto: ${file.name}")
            archiveFile
        } catch (e: Exception) {
            Timber.e(e, "Error al abrir MBTiles: ${file.name}")
            null
        }
    }

    /**
     * Obtiene información básica de un archivo MBTiles.
     *
     * @param file Archivo MBTiles
     * @return Map con información (name, size, path, isCorrupted) o null si hay error
     */
    fun getMBTilesInfo(file: File): Map<String, String>? {
        val isValid = isValidMBTiles(file, deepValidation = false)
        if (!isValid) {
            return null
        }

        return try {
            val isCorrupted = !validateSQLiteStructure(file)
            
            mapOf(
                "name" to file.nameWithoutExtension,
                "size" to formatFileSize(file.length()),
                "path" to file.absolutePath,
                "extension" to file.extension,
                "isCorrupted" to isCorrupted.toString()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener info de MBTiles: ${file.name}")
            null
        }
    }

    /**
     * Busca archivos MBTiles en un directorio.
     *
     * @param directory Directorio donde buscar
     * @return Lista de archivos MBTiles encontrados
     */
    fun findMBTilesFiles(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) {
            Timber.w("Directorio no existe o no es directorio: ${directory.absolutePath}")
            return emptyList()
        }

        return try {
            directory.listFiles { file ->
                file.isFile && file.name.endsWith(".mbtiles", ignoreCase = true)
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error al buscar archivos MBTiles en: ${directory.absolutePath}")
            emptyList()
        }
    }

    /**
     * Copia un archivo MBTiles al directorio de la app.
     *
     * @param context Contexto de la aplicación
     * @param sourceFile Archivo origen
     * @param targetFileName Nombre del archivo destino (opcional)
     * @return Archivo copiado o null si hay error
     */
    fun copyMBTilesToAppDirectory(
        context: Context,
        sourceFile: File,
        targetFileName: String? = null
    ): File? {
        if (!isValidMBTiles(sourceFile)) {
            Timber.w("Archivo origen no es válido: ${sourceFile.absolutePath}")
            return null
        }

        return try {
            val appDirectory = File(context.filesDir, Constants.Directories.FILES_DIR_NAME)
            if (!appDirectory.exists()) {
                appDirectory.mkdirs()
            }

            val fileName = targetFileName ?: sourceFile.name
            val targetFile = File(appDirectory, fileName)

            sourceFile.copyTo(targetFile, overwrite = true)
            Timber.d("Archivo MBTiles copiado: ${targetFile.absolutePath}")
            
            targetFile
        } catch (e: Exception) {
            Timber.e(e, "Error al copiar archivo MBTiles")
            null
        }
    }

    /**
     * Elimina un archivo MBTiles.
     *
     * @param file Archivo a eliminar
     * @return true si se eliminó correctamente, false en caso contrario
     */
    fun deleteMBTiles(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                Timber.d("Archivo MBTiles eliminado: ${file.absolutePath}")
            } else {
                Timber.w("No se pudo eliminar archivo MBTiles: ${file.absolutePath}")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar archivo MBTiles: ${file.name}")
            false
        }
    }

    /**
     * Formatea el tamaño de un archivo a formato legible.
     *
     * @param bytes Tamaño en bytes
     * @return String formateado (ej: "15.5 MB")
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Obtiene directorios comunes donde buscar archivos MBTiles.
     *
     * @param context Contexto de la aplicación
     * @return Lista de directorios
     */
    fun getCommonMBTilesDirectories(context: Context): List<File> {
        val directories = mutableListOf<File>()

        // Directorio de archivos de la app
        val appFilesDir = File(context.filesDir, Constants.Directories.FILES_DIR_NAME)
        if (appFilesDir.exists() || appFilesDir.mkdirs()) {
            directories.add(appFilesDir)
        }

        // Directorio externo de la app (si está disponible)
        context.getExternalFilesDir(null)?.let { externalDir ->
            val mbtilesDir = File(externalDir, "mbtiles")
            if (mbtilesDir.exists() || mbtilesDir.mkdirs()) {
                directories.add(mbtilesDir)
            }
        }

        // Directorio de descargas (si existe)
        val downloadsDir = File("/storage/emulated/0/Download")
        if (downloadsDir.exists()) {
            directories.add(downloadsDir)
        }

        Timber.d("Directorios comunes para MBTiles: ${directories.size}")
        return directories
    }
}
