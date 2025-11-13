package com.dasomaps.app.data.repository

import android.content.Context
import com.dasomaps.app.data.model.GeoTIFFInfo
import com.dasomaps.app.data.model.Layer
import com.dasomaps.app.data.model.LayerType
import com.dasomaps.app.data.model.RasterValue
import com.dasomaps.app.utils.GeoTIFFManager
import com.dasomaps.app.utils.GeoTIFFReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio para gestionar operaciones con capas ráster (GeoTIFF).
 * 
 * Proporciona una capa de abstracción entre la UI y los lectores de GeoTIFF,
 * con caché de metadatos para mejorar el rendimiento.
 * 
 * Responsabilidades:
 * - Consultar valores de píxeles en coordenadas geográficas
 * - Gestionar caché de metadatos de GeoTIFF
 * - Coordinar consultas entre múltiples capas activas
 * - Manejar errores y logs
 * 
 * Uso:
 * ```
 * val repository = RasterRepository(context)
 * val values = repository.queryRasterValues(latitude, longitude, activeLayers)
 * ```
 */
class RasterRepository(private val context: Context) {

    private val reader = GeoTIFFReader(context)
    private val manager = GeoTIFFManager(context)
    
    // Caché de metadatos: layerId -> GeoTIFFInfo
    private val metadataCache = ConcurrentHashMap<String, GeoTIFFInfo>()
    
    // Caché de archivos: layerId -> File
    private val fileCache = ConcurrentHashMap<String, File>()

    /**
     * Resultado de una consulta de valores ráster.
     */
    sealed class QueryResult {
        /**
         * Consulta exitosa con valores.
         */
        data class Success(val values: List<RasterValue>) : QueryResult()
        
        /**
         * No se encontraron valores (coordenada fuera de todos los ráster).
         */
        object NoData : QueryResult()
        
        /**
         * Error durante la consulta.
         */
        data class Error(val message: String, val exception: Exception? = null) : QueryResult()
    }

    /**
     * Consulta valores de todas las capas ráster activas en una coordenada.
     * 
     * @param latitude Latitud WGS84
     * @param longitude Longitud WGS84
     * @param activeLayers Lista de capas activas y visibles
     * @return Resultado de la consulta con valores de todas las capas
     */
    suspend fun queryRasterValues(
        latitude: Double,
        longitude: Double,
        activeLayers: List<Layer>
    ): QueryResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Consultando valores ráster en ($latitude, $longitude)")
            
            // Filtrar solo capas RASTER
            val rasterLayers = activeLayers.filter { it.type == LayerType.RASTER }
            
            if (rasterLayers.isEmpty()) {
                Timber.d("No hay capas ráster activas")
                return@withContext QueryResult.NoData
            }
            
            Timber.d("Consultando ${rasterLayers.size} capas ráster")
            
            val values = mutableListOf<RasterValue>()
            
            // Consultar cada capa
            for (layer in rasterLayers) {
                try {
                    val value = queryLayerValue(layer, latitude, longitude)
                    if (value != null) {
                        values.add(value)
                        Timber.d("Valor encontrado en capa ${layer.name}: ${value.primaryValue}")
                    } else {
                        Timber.d("Sin datos en capa ${layer.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error al consultar capa ${layer.name}")
                    // Continuar con las siguientes capas
                }
            }
            
            if (values.isEmpty()) {
                Timber.d("No se encontraron valores en ninguna capa")
                QueryResult.NoData
            } else {
                Timber.d("Encontrados ${values.size} valores")
                QueryResult.Success(values)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error general al consultar valores ráster")
            QueryResult.Error("Error al consultar valores: ${e.message}", e)
        }
    }

    /**
     * Consulta el valor de una capa específica en una coordenada.
     * 
     * @param layer Capa ráster a consultar
     * @param latitude Latitud WGS84
     * @param longitude Longitud WGS84
     * @return Valor encontrado o null si no hay datos
     */
    suspend fun queryLayerValue(
        layer: Layer,
        latitude: Double,
        longitude: Double
    ): RasterValue? = withContext(Dispatchers.IO) {
        try {
            // Obtener archivo de la capa
            val file = getLayerFile(layer) ?: run {
                Timber.w("No se encontró archivo para capa ${layer.name}")
                return@withContext null
            }
            
            // Obtener metadatos (con caché)
            val info = getLayerMetadata(layer, file) ?: run {
                Timber.w("No se pudieron leer metadatos de ${layer.name}")
                return@withContext null
            }
            
            // Verificar que la coordenada está dentro del ráster
            if (!info.containsCoordinate(longitude, latitude)) {
                Timber.d("Coordenada fuera del ráster ${layer.name}")
                return@withContext null
            }
            
            // Consultar valores de todas las bandas
            val values = reader.getAllBandValues(file, latitude, longitude)
            
            if (values == null || values.all { it.isNaN() }) {
                Timber.d("Sin datos válidos en ${layer.name}")
                return@withContext null
            }
            
            // Crear RasterValue
            RasterValue(
                layerId = layer.id,
                layerName = layer.name,
                latitude = latitude,
                longitude = longitude,
                values = values,
                bandNames = layer.bandNames ?: info.bandNames,
                unit = layer.unit,
                noDataValue = layer.noDataValue ?: info.noDataValue
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error al consultar capa ${layer.name}")
            null
        }
    }

    /**
     * Obtiene los metadatos de una capa con caché.
     * 
     * @param layer Capa ráster
     * @param file Archivo GeoTIFF
     * @return Metadatos del GeoTIFF o null si hay error
     */
    suspend fun getLayerMetadata(layer: Layer, file: File): GeoTIFFInfo? = withContext(Dispatchers.IO) {
        try {
            // Verificar caché
            metadataCache[layer.id]?.let {
                Timber.d("Metadatos de ${layer.name} obtenidos de caché")
                return@withContext it
            }
            
            // Leer metadatos
            Timber.d("Leyendo metadatos de ${layer.name}")
            val info = reader.readMetadata(file)
            
            // Guardar en caché
            metadataCache[layer.id] = info
            
            info
            
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener metadatos de ${layer.name}")
            null
        }
    }

    /**
     * Obtiene el archivo de una capa ráster.
     * 
     * @param layer Capa ráster
     * @return Archivo GeoTIFF o null si no existe
     */
    private fun getLayerFile(layer: Layer): File? {
        // Verificar caché
        fileCache[layer.id]?.let { cachedFile ->
            if (cachedFile.exists()) {
                return cachedFile
            } else {
                fileCache.remove(layer.id)
            }
        }
        
        // Obtener del localPath
        val path = layer.localPath ?: return null
        val file = File(path)
        
        if (!file.exists()) {
            Timber.w("Archivo no existe: $path")
            return null
        }
        
        // Guardar en caché
        fileCache[layer.id] = file
        
        return file
    }

    /**
     * Valida un archivo GeoTIFF antes de importarlo.
     * 
     * @param file Archivo a validar
     * @return Información del archivo o null si no es válido
     */
    suspend fun validateGeoTIFF(file: File): GeoTIFFManager.GeoTIFFFileInfo? = withContext(Dispatchers.IO) {
        try {
            manager.validateGeoTIFF(file)
        } catch (e: Exception) {
            Timber.e(e, "Error al validar GeoTIFF")
            null
        }
    }

    /**
     * Importa un archivo GeoTIFF y crea una capa.
     * 
     * @param sourceFile Archivo GeoTIFF de origen
     * @param layerName Nombre para la capa
     * @return Par de (Layer, File) importados o null si hay error
     */
    suspend fun importGeoTIFF(
        sourceFile: File,
        layerName: String
    ): Pair<Layer, File>? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Importando GeoTIFF: ${sourceFile.name}")
            
            // Validar archivo
            val fileInfo = manager.validateGeoTIFF(sourceFile)
            if (fileInfo == null || !fileInfo.isValid) {
                Timber.w("Archivo no es un GeoTIFF válido: ${sourceFile.name}")
                return@withContext null
            }
            
            // Importar archivo
            val importedFile = manager.importGeoTIFF(sourceFile, layerName)
            if (importedFile == null) {
                Timber.w("Error al importar archivo: ${sourceFile.name}")
                return@withContext null
            }
            
            // Leer metadatos
            val metadata = fileInfo.metadata ?: reader.readMetadata(importedFile)
            
            // Crear Layer
            val layer = Layer(
                id = "raster_${System.currentTimeMillis()}",
                name = layerName,
                type = LayerType.RASTER,
                isVisible = true,
                opacity = 1.0f,
                zIndex = 100, // Por encima de MBTiles
                localPath = importedFile.absolutePath,
                bounds = metadata.bounds.toList(),
                bandCount = metadata.bandCount,
                bandNames = metadata.bandNames,
                noDataValue = metadata.noDataValue
            )
            
            // Guardar metadatos en caché
            metadataCache[layer.id] = metadata
            fileCache[layer.id] = importedFile
            
            Timber.d("GeoTIFF importado exitosamente: $layerName")
            
            Pair(layer, importedFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Error al importar GeoTIFF: ${sourceFile.name}")
            null
        }
    }

    /**
     * Busca archivos GeoTIFF disponibles en el dispositivo.
     * 
     * @return Lista de archivos encontrados
     */
    suspend fun findAvailableGeoTIFFs(): List<GeoTIFFManager.GeoTIFFFileInfo> = withContext(Dispatchers.IO) {
        try {
            manager.findGeoTIFFFiles()
        } catch (e: Exception) {
            Timber.e(e, "Error al buscar archivos GeoTIFF")
            emptyList()
        }
    }

    /**
     * Obtiene información de los GeoTIFF importados.
     * 
     * @return Lista de archivos importados con información
     */
    suspend fun getImportedGeoTIFFs(): List<GeoTIFFManager.GeoTIFFFileInfo> = withContext(Dispatchers.IO) {
        try {
            manager.getImportedGeoTIFFsInfo()
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener GeoTIFFs importados")
            emptyList()
        }
    }

    /**
     * Elimina una capa ráster y su archivo.
     * 
     * @param layer Capa a eliminar
     * @return true si se eliminó exitosamente
     */
    suspend fun deleteRasterLayer(layer: Layer): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getLayerFile(layer)
            
            if (file != null) {
                // Eliminar archivo
                val deleted = manager.deleteGeoTIFF(file)
                
                if (deleted) {
                    // Limpiar cachés
                    metadataCache.remove(layer.id)
                    fileCache.remove(layer.id)
                    
                    Timber.d("Capa ráster eliminada: ${layer.name}")
                    return@withContext true
                }
            }
            
            false
            
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar capa ráster: ${layer.name}")
            false
        }
    }

    /**
     * Verifica si una capa ráster está disponible (archivo existe).
     * 
     * @param layer Capa a verificar
     * @return true si el archivo existe
     */
    fun isLayerAvailable(layer: Layer): Boolean {
        val file = getLayerFile(layer)
        return file != null && file.exists()
    }

    /**
     * Limpia el caché de metadatos.
     * 
     * @param layerId ID de la capa a limpiar, o null para limpiarlo
     */
    fun clearMetadataCache(layerId: String? = null) {
        if (layerId != null) {
            metadataCache.remove(layerId)
            fileCache.remove(layerId)
            Timber.d("Caché limpiado para capa: $layerId")
        } else {
            metadataCache.clear()
            fileCache.clear()
            Timber.d("Caché de metadatos limpiado completamente")
        }
    }

    /**
     * Obtiene estadísticas del caché.
     * 
     * @return Información sobre el estado del caché
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            metadataCacheSize = metadataCache.size,
            fileCacheSize = fileCache.size,
            estimatedMemoryKB = metadataCache.size * 2 // ~2 KB por metadatos
        )
    }

    /**
     * Estadísticas del caché.
     */
    data class CacheStats(
        val metadataCacheSize: Int,
        val fileCacheSize: Int,
        val estimatedMemoryKB: Int
    ) {
        override fun toString(): String {
            return "Caché: $metadataCacheSize metadatos, $fileCacheSize archivos (~$estimatedMemoryKB KB)"
        }
    }

    /**
     * Pre-carga metadatos de una lista de capas.
     * Útil para mejorar el rendimiento de la primera consulta.
     * 
     * @param layers Capas a pre-cargar
     */
    suspend fun preloadMetadata(layers: List<Layer>) = withContext(Dispatchers.IO) {
        Timber.d("Pre-cargando metadatos de ${layers.size} capas")
        
        layers.filter { it.type == LayerType.RASTER }.forEach { layer ->
            try {
                val file = getLayerFile(layer)
                if (file != null) {
                    getLayerMetadata(layer, file)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error al pre-cargar metadatos de ${layer.name}")
            }
        }
        
        Timber.d("Pre-carga completada. ${getCacheStats()}")
    }

    /**
     * Obtiene información de almacenamiento de GeoTIFF.
     * 
     * @return String descriptivo del almacenamiento usado
     */
    fun getStorageInfo(): String {
        return manager.getStorageInfo()
    }

    /**
     * Verifica si hay espacio disponible para importar un archivo.
     * 
     * @param fileSize Tamaño en bytes
     * @return true si hay espacio suficiente
     */
    fun hasStorageSpace(fileSize: Long): Boolean {
        return manager.hasStorageSpace(fileSize)
    }
}
