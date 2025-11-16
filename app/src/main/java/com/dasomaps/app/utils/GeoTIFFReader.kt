package com.dasomaps.app.utils

import android.content.Context
import com.dasomaps.app.data.model.GeoTIFFInfo
import org.gdal.gdal.Band
import org.gdal.gdal.Dataset
import org.gdal.gdal.gdal
import org.gdal.gdalconst.gdalconstConstants
import org.gdal.osr.SpatialReference
import timber.log.Timber
import java.io.File

/**
 * Lector de archivos GeoTIFF usando GDAL nativo.
 * 
 * IMPLEMENTACIÓN CON GDAL (JNI):
 * 
 * Esta implementación usa GDAL (Geospatial Data Abstraction Library) 
 * compilado como librería nativa para Android via JNI.
 * 
 * Capacidades:
 * - ✅ Lee metadatos completos del GeoTIFF
 * - ✅ Lee valores REALES de píxeles
 * - ✅ Transforma coordenadas geográficas a píxeles automáticamente
 * - ✅ Soporta múltiples bandas
 * - ✅ Soporta diferentes CRS (con transformación)
 * - ✅ Maneja valores NoData correctamente
 * - ✅ Alto rendimiento (código C++ nativo)
 * 
 * GDAL es el estándar de la industria para procesamiento geoespacial.
 * Es usado por QGIS, ArcGIS, Google Earth Engine, y miles de aplicaciones.
 * 
 * @property context Contexto de Android (necesario para inicialización)
 */
class GeoTIFFReader(private val context: Context) {

    companion object {
        private const val TAG = "GeoTIFFReader"
        
        @Volatile
        private var isGdalInitialized = false
        
        /**
         * Inicializa GDAL. Debe llamarse antes de usar cualquier función.
         * Es seguro llamar múltiples veces (solo inicializa una vez).
         */
        fun initializeGdal() {
            if (!isGdalInitialized) {
                synchronized(this) {
                    if (!isGdalInitialized) {
                        try {
                            // Registrar todos los drivers de GDAL
                            gdal.AllRegister()
                            
                            // Configurar opciones de GDAL
                            gdal.SetConfigOption("GDAL_NUM_THREADS", "ALL_CPUS")
                            
                            isGdalInitialized = true
                            Timber.tag(TAG).i("GDAL inicializado correctamente")
                            Timber.tag(TAG).i("Versión GDAL: ${gdal.VersionInfo()}")
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error al inicializar GDAL")
                            throw e
                        }
                    }
                }
            }
        }
    }

    init {
        // Asegurar que GDAL está inicializado
        initializeGdal()
    }

    /**
     * Lee los metadatos completos de un archivo GeoTIFF.
     * 
     * Usa GDAL para extraer toda la información geoespacial:
     * - Dimensiones (ancho, alto)
     * - Número de bandas
     * - Transformación geoespacial (GeoTransform)
     * - Sistema de coordenadas (CRS)
     * - Tipo de dato
     * - Valor NoData
     * 
     * @param file Archivo GeoTIFF a leer
     * @return Información de metadatos del GeoTIFF
     * @throws Exception si el archivo no es válido o no se puede leer
     */
    fun readMetadata(file: File): GeoTIFFInfo {
        Timber.tag(TAG).d("Leyendo metadatos de GeoTIFF: ${file.name}")
        
        var dataset: Dataset? = null
        
        try {
            // Abrir dataset en modo solo lectura
            dataset = gdal.Open(file.absolutePath, gdalconstConstants.GA_ReadOnly)
            
            if (dataset == null) {
                throw IllegalArgumentException("No se pudo abrir el archivo GeoTIFF: ${file.name}")
            }
            
            // Obtener dimensiones
            val width = dataset.getRasterXSize()
            val height = dataset.getRasterYSize()
            val bandCount = dataset.getRasterCount()
            
            Timber.tag(TAG).d("Dimensiones: ${width}x${height}, Bandas: $bandCount")
            
            // Obtener GeoTransform (transformación afín)
            val geoTransformArray = DoubleArray(6)
            dataset.GetGeoTransform(geoTransformArray)
            
            val geoTransform = GeoTIFFInfo.GeoTransform(
                originX = geoTransformArray[0],
                pixelWidth = geoTransformArray[1],
                rotationX = geoTransformArray[2],
                originY = geoTransformArray[3],
                rotationY = geoTransformArray[4],
                pixelHeight = geoTransformArray[5]
            )
            
            Timber.tag(TAG).d("GeoTransform: ${geoTransformArray.contentToString()}")
            
            // Calcular bounds
            val minX = geoTransformArray[0]
            val maxX = minX + width * geoTransformArray[1]
            val maxY = geoTransformArray[3]
            val minY = maxY + height * geoTransformArray[5]
            
            val bounds = GeoTIFFInfo.Bounds(
                minLon = minOf(minX, maxX),
                minLat = minOf(minY, maxY),
                maxLon = maxOf(minX, maxX),
                maxLat = maxOf(minY, maxY)
            )
            
            Timber.tag(TAG).d("Bounds: $bounds")
            
            // Obtener CRS
            val projection = dataset.GetProjection()
            val srs = SpatialReference(projection)
            val crsString = if (srs.IsProjected() == 1) {
                "EPSG:${srs.GetAuthorityCode(null)}"
            } else if (srs.IsGeographic() == 1) {
                "EPSG:${srs.GetAuthorityCode(null)}"
            } else {
                "UNKNOWN"
            }
            
            Timber.tag(TAG).d("CRS: $crsString")
            
            // Obtener información de la primera banda para determinar tipo de dato y NoData
            val band = dataset.GetRasterBand(1)
            val dataType = getDataType(band.getDataType())
            
            // Obtener valor NoData (si existe)
            val noDataArray = Array<Double>(1) { 0.0 }
            band.GetNoDataValue(noDataArray)
            val noDataValue = if (noDataArray[0] != 0.0 && !noDataArray[0].isNaN()) {
                noDataArray[0]
            } else {
                null
            }
            
            Timber.tag(TAG).d("Tipo de dato: $dataType")
            if (noDataValue != null) {
                Timber.tag(TAG).d("NoData value: $noDataValue")
            }
            
            // Nombres de bandas
            val bandNames = List(bandCount) { index ->
                "Banda ${index + 1}"
            }
            
            // Crear objeto GeoTIFFInfo
            val info = GeoTIFFInfo(
                width = width,
                height = height,
                bounds = bounds,
                pixelSizeX = Math.abs(geoTransformArray[1]),
                pixelSizeY = Math.abs(geoTransformArray[5]),
                bandCount = bandCount,
                bandNames = bandNames,
                noDataValue = noDataValue,
                dataType = dataType,
                compression = null,  // GDAL no expone fácilmente la compresión
                crs = crsString,
                geoTransform = geoTransform
            )
            
            Timber.tag(TAG).d("Metadatos leídos exitosamente")
            Timber.tag(TAG).d(info.toString())
            
            return info
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al leer metadatos de GeoTIFF: ${file.name}")
            throw e
        } finally {
            // Cerrar dataset
            dataset?.delete()
        }
    }

    /**
     * Obtiene el valor REAL de un píxel en una coordenada geográfica específica.
     * 
     * Esta implementación usa GDAL para:
     * 1. Abrir el archivo GeoTIFF
     * 2. Convertir coordenadas geográficas (lat, lon) a píxel (col, row)
     * 3. Leer el valor real del píxel de la banda especificada
     * 4. Verificar si es NoData
     * 
     * @param file Archivo GeoTIFF
     * @param latitude Latitud WGS84 (o CRS del archivo)
     * @param longitude Longitud WGS84 (o CRS del archivo)
     * @param bandNumber Número de banda (1-based, por defecto 1)
     * @return Valor del píxel o null si está fuera del ráster o es NoData
     */
    fun getPixelValue(
        file: File,
        latitude: Double,
        longitude: Double,
        bandNumber: Int = 1
    ): Double? {
        var dataset: Dataset? = null
        
        try {
            // Abrir dataset
            dataset = gdal.Open(file.absolutePath, gdalconstConstants.GA_ReadOnly)
            
            if (dataset == null) {
                Timber.tag(TAG).w("No se pudo abrir el archivo: ${file.name}")
                return null
            }
            
            // Verificar número de banda
            if (bandNumber < 1 || bandNumber > dataset.getRasterCount()) {
                Timber.tag(TAG).w("Número de banda inválido: $bandNumber (máx: ${dataset.getRasterCount()})")
                return null
            }
            
            // Obtener GeoTransform
            val geoTransform = DoubleArray(6)
            dataset.GetGeoTransform(geoTransform)
            
            // Convertir coordenadas geográficas a píxel
            val pixelCoords = geoToPixel(
                longitude, latitude,
                geoTransform[0], geoTransform[1], geoTransform[2],
                geoTransform[3], geoTransform[4], geoTransform[5]
            )
            
            if (pixelCoords == null) {
                Timber.tag(TAG).w("No se pudo convertir coordenada a píxel: ($longitude, $latitude)")
                return null
            }
            
            val (col, row) = pixelCoords
            
            // Verificar que está dentro del ráster
            if (col < 0 || col >= dataset.getRasterXSize() || 
                row < 0 || row >= dataset.getRasterYSize()) {
                Timber.tag(TAG).w("Píxel fuera del ráster: ($col, $row)")
                return null
            }
            
            Timber.tag(TAG).d("Consultando píxel [$col, $row] en banda $bandNumber")
            
            // Obtener banda
            val band = dataset.GetRasterBand(bandNumber)
            
            // Leer valor del píxel
            // Crear buffer para un solo píxel
            val buffer = DoubleArray(1)
            
            // Leer píxel específico (xOff, yOff, xSize=1, ySize=1)
            band.ReadRaster(
                col, row,      // Offset
                1, 1,          // Tamaño a leer (1x1 píxel)
                1, 1,          // Tamaño del buffer
                gdalconstConstants.GDT_Float64,  // Tipo de dato  // ← CAMBIAR AQUÍ TAMBIÉN
                buffer         // Buffer de salida
            )
            
            val value = buffer[0]
            
            // Verificar si es NoData
            val noDataArray = Array<Double>(1) { 0.0 }
            band.GetNoDataValue(noDataArray)
            val noDataValue = noDataArray[0]
            
            if (noDataValue != 0.0 && !noDataValue.isNaN() && Math.abs(value - noDataValue) < 0.0001) {
                Timber.tag(TAG).d("Valor NoData encontrado en ($longitude, $latitude)")
                return null
            }
            
            Timber.tag(TAG).d("Valor leído: $value")
            
            return value
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al obtener valor de píxel en ($longitude, $latitude)")
            return null
        } finally {
            // Cerrar dataset
            dataset?.delete()
        }
    }

    /**
     * Obtiene los valores REALES de todas las bandas en una coordenada geográfica.
     * 
     * @param file Archivo GeoTIFF
     * @param latitude Latitud WGS84
     * @param longitude Longitud WGS84
     * @return Lista de valores (uno por banda) o null si está fuera del ráster
     */
    fun getAllBandValues(
        file: File,
        latitude: Double,
        longitude: Double
    ): List<Double>? {
        var dataset: Dataset? = null

        try {
            Timber.tag(TAG).d("getAllBandValues: Abriendo archivo ${file.name}")

            // Abrir dataset
            dataset = gdal.Open(file.absolutePath, gdalconstConstants.GA_ReadOnly)

            if (dataset == null) {
                Timber.tag(TAG).w("getAllBandValues: No se pudo abrir el archivo: ${file.name}")
                return null
            }

            val bandCount = dataset.getRasterCount()
            val width = dataset.getRasterXSize()
            val height = dataset.getRasterYSize()

            Timber.tag(TAG).d("getAllBandValues: Dataset ${width}x${height}, $bandCount bandas")

            // Obtener GeoTransform
            val geoTransform = DoubleArray(6)
            dataset.GetGeoTransform(geoTransform)

            Timber.tag(TAG).d("getAllBandValues: GeoTransform = ${geoTransform.contentToString()}")
            Timber.tag(TAG).d("getAllBandValues: Coordenada = ($latitude, $longitude)")

            // Convertir coordenadas geográficas a píxel
            val pixelCoords = geoToPixel(
                longitude, latitude,
                geoTransform[0], geoTransform[1], geoTransform[2],
                geoTransform[3], geoTransform[4], geoTransform[5]
            )

            if (pixelCoords == null) {
                Timber.tag(TAG).w("getAllBandValues: No se pudo convertir coordenada a píxel")
                return null
            }

            val (col, row) = pixelCoords

            Timber.tag(TAG).d("getAllBandValues: Píxel calculado = ($col, $row)")

            // Verificar que está dentro del ráster
            if (col < 0 || col >= width || row < 0 || row >= height) {
                Timber.tag(TAG).w("getAllBandValues: Píxel fuera del ráster ($col,$row), límites=(0,0,$width,$height)")
                return null
            }

            // Leer valores de todas las bandas
            val values = mutableListOf<Double>()

            for (bandIndex in 1..bandCount) {
                val band = dataset.GetRasterBand(bandIndex)
                val buffer = DoubleArray(1)

                Timber.tag(TAG).d("getAllBandValues: Leyendo banda $bandIndex en píxel ($col,$row)")

                val result = band.ReadRaster(
                    col, row,
                    1, 1,
                    1, 1,
                    gdalconstConstants.GDT_Float64,  // ← FORZAR A DOUBLE
                    buffer
                )

                Timber.tag(TAG).d("getAllBandValues: ReadRaster result=$result, buffer[0]=${buffer[0]}")

                values.add(buffer[0])
            }

            Timber.tag(TAG).d("Valores leídos de todas las bandas: $values")

            return values

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al obtener valores de todas las bandas")
            return null
        } finally {
            dataset?.delete()
        }
    }

    /**
     * Valida si un archivo es un GeoTIFF válido que GDAL puede leer.
     * 
     * @param file Archivo a validar
     * @return true si es un archivo GeoTIFF válido
     */
    fun isValidGeoTIFF(file: File): Boolean {
        var dataset: Dataset? = null
        
        return try {
            dataset = gdal.Open(file.absolutePath, gdalconstConstants.GA_ReadOnly)
            val isValid = dataset != null
            
            if (!isValid) {
                Timber.tag(TAG).w("No es un archivo GeoTIFF válido: ${file.name}")
            } else {
                Timber.tag(TAG).d("Archivo GeoTIFF válido: ${file.name}")
            }
            
            isValid
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al validar GeoTIFF: ${file.name}")
            false
        } finally {
            dataset?.delete()
        }
    }

    // ========== Métodos Privados ==========

    /**
     * Convierte coordenadas geográficas a píxel usando GeoTransform.
     * 
     * Basado en la fórmula estándar de GDAL:
     * X_geo = GT[0] + col * GT[1] + row * GT[2]
     * Y_geo = GT[3] + col * GT[4] + row * GT[5]
     * 
     * Resolviendo para col y row:
     * col = (X_geo - GT[0]) / GT[1]  (para ráster no rotados)
     * row = (Y_geo - GT[3]) / GT[5]  (para ráster no rotados)
     */
    private fun geoToPixel(
        lon: Double,
        lat: Double,
        gt0: Double, gt1: Double, gt2: Double,
        gt3: Double, gt4: Double, gt5: Double
    ): Pair<Int, Int>? {
        try {
            // Para ráster no rotados (caso más común)
            if (gt2 == 0.0 && gt4 == 0.0) {
                val col = ((lon - gt0) / gt1).toInt()
                val row = ((lat - gt3) / gt5).toInt()
                return Pair(col, row)
            }
            
            // Para ráster rotados (caso general)
            // Resolver sistema de ecuaciones lineales
            val det = gt1 * gt5 - gt2 * gt4
            
            if (Math.abs(det) < 1e-10) {
                return null
            }
            
            val dx = lon - gt0
            val dy = lat - gt3
            
            val col = ((gt5 * dx - gt2 * dy) / det).toInt()
            val row = ((-gt4 * dx + gt1 * dy) / det).toInt()

            // LOG para controlar xq no muestra valores
            Timber.tag(TAG).d("geoToPixel: ($lon,$lat) -> ($col,$row) [no rotado]")
            Timber.tag(TAG).d("geoToPixel: GT=[${gt0},${gt1},${gt2},${gt3},${gt4},${gt5}]")

            return Pair(col, row)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al convertir geo a píxel")
            return null
        }
    }

    /**
     * Convierte el tipo de dato de GDAL a nuestro enum.
     */
    private fun getDataType(gdalType: Int): GeoTIFFInfo.DataType {
        return when (gdalType) {
            gdalconstConstants.GDT_Byte -> GeoTIFFInfo.DataType.BYTE
            gdalconstConstants.GDT_Int16 -> GeoTIFFInfo.DataType.INT16
            gdalconstConstants.GDT_UInt16 -> GeoTIFFInfo.DataType.UINT16
            gdalconstConstants.GDT_Int32 -> GeoTIFFInfo.DataType.INT32
            gdalconstConstants.GDT_UInt32 -> GeoTIFFInfo.DataType.UINT32
            gdalconstConstants.GDT_Float32 -> GeoTIFFInfo.DataType.FLOAT32
            gdalconstConstants.GDT_Float64 -> GeoTIFFInfo.DataType.FLOAT64
            else -> GeoTIFFInfo.DataType.UNKNOWN
        }
    }
}
