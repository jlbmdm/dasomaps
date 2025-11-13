package com.dasomaps.app.data.model

/**
 * Información de metadatos de un archivo GeoTIFF.
 * 
 * Contiene toda la información geoespacial necesaria para posicionar
 * el ráster en el mapa y realizar consultas de coordenadas a píxeles.
 * 
 * @property width Ancho en píxeles
 * @property height Alto en píxeles
 * @property bounds Límites geográficos [minLon, minLat, maxLon, maxLat] en WGS84
 * @property pixelSizeX Tamaño del píxel en el eje X (en grados para WGS84)
 * @property pixelSizeY Tamaño del píxel en el eje Y (en grados para WGS84)
 * @property bandCount Número de bandas en el ráster
 * @property bandNames Nombres de las bandas (si están definidos)
 * @property noDataValue Valor que representa ausencia de datos
 * @property dataType Tipo de dato (Byte, Int16, Float32, etc.)
 * @property compression Tipo de compresión (si existe)
 * @property crs Sistema de coordenadas (debería ser EPSG:4326 / WGS84)
 * @property geoTransform Parámetros de la transformación afín [x0, dx, 0, y0, 0, dy]
 */
data class GeoTIFFInfo(
    val width: Int,
    val height: Int,
    val bounds: Bounds,
    val pixelSizeX: Double,
    val pixelSizeY: Double,
    val bandCount: Int,
    val bandNames: List<String>? = null,
    val noDataValue: Double? = null,
    val dataType: DataType,
    val compression: String? = null,
    val crs: String = "EPSG:4326",
    val geoTransform: GeoTransform
) {
    /**
     * Límites geográficos del ráster.
     */
    data class Bounds(
        val minLon: Double,
        val minLat: Double,
        val maxLon: Double,
        val maxLat: Double
    ) {
        /**
         * Verifica si un punto está dentro de los límites.
         */
        fun contains(lon: Double, lat: Double): Boolean {
            return lon >= minLon && lon <= maxLon && lat >= minLat && lat <= maxLat
        }
        
        /**
         * Convierte a lista [minLon, minLat, maxLon, maxLat].
         */
        fun toList(): List<Double> = listOf(minLon, minLat, maxLon, maxLat)
    }
    
    /**
     * Transformación afín para convertir coordenadas píxel ↔ geográficas.
     * 
     * Basado en el estándar GeoTIFF:
     * X_geo = geoTransform[0] + col * geoTransform[1] + row * geoTransform[2]
     * Y_geo = geoTransform[3] + col * geoTransform[4] + row * geoTransform[5]
     * 
     * Para ráster no rotados (caso común):
     * - geoTransform[2] = 0
     * - geoTransform[4] = 0
     */
    data class GeoTransform(
        val originX: Double,      // X de la esquina superior izquierda
        val pixelWidth: Double,   // Tamaño del píxel en X
        val rotationX: Double,    // Rotación en X (normalmente 0)
        val originY: Double,      // Y de la esquina superior izquierda
        val rotationY: Double,    // Rotación en Y (normalmente 0)
        val pixelHeight: Double   // Tamaño del píxel en Y (normalmente negativo)
    ) {
        /**
         * Convierte coordenadas geográficas (lon, lat) a píxel (col, row).
         * 
         * @return Pair<col, row> o null si está fuera del ráster
         */
        fun geoToPixel(lon: Double, lat: Double): Pair<Int, Int>? {
            // Para ráster no rotados (caso común)
            if (rotationX == 0.0 && rotationY == 0.0) {
                val col = ((lon - originX) / pixelWidth).toInt()
                val row = ((lat - originY) / pixelHeight).toInt()
                return Pair(col, row)
            }
            
            // Para ráster rotados (caso general)
            // Resolver sistema de ecuaciones lineales
            val det = pixelWidth * pixelHeight - rotationX * rotationY
            if (det == 0.0) return null
            
            val dx = lon - originX
            val dy = lat - originY
            
            val col = ((pixelHeight * dx - rotationY * dy) / det).toInt()
            val row = ((-rotationX * dx + pixelWidth * dy) / det).toInt()
            
            return Pair(col, row)
        }
        
        /**
         * Convierte coordenadas de píxel (col, row) a geográficas (lon, lat).
         */
        fun pixelToGeo(col: Int, row: Int): Pair<Double, Double> {
            val lon = originX + col * pixelWidth + row * rotationX
            val lat = originY + col * rotationY + row * pixelHeight
            return Pair(lon, lat)
        }
        
        /**
         * Convierte a array [x0, dx, rx, y0, ry, dy].
         */
        fun toArray(): DoubleArray = doubleArrayOf(
            originX, pixelWidth, rotationX,
            originY, rotationY, pixelHeight
        )
    }
    
    /**
     * Tipos de datos soportados en GeoTIFF.
     */
    enum class DataType {
        BYTE,      // 8-bit unsigned integer
        INT16,     // 16-bit signed integer
        UINT16,    // 16-bit unsigned integer
        INT32,     // 32-bit signed integer
        UINT32,    // 32-bit unsigned integer
        FLOAT32,   // 32-bit float
        FLOAT64,   // 64-bit float (double)
        UNKNOWN;
        
        companion object {
            fun fromString(type: String): DataType {
                return when (type.uppercase()) {
                    "BYTE", "UINT8" -> BYTE
                    "INT16", "SHORT" -> INT16
                    "UINT16", "USHORT" -> UINT16
                    "INT32", "INT" -> INT32
                    "UINT32", "UINT" -> UINT32
                    "FLOAT32", "FLOAT" -> FLOAT32
                    "FLOAT64", "DOUBLE" -> FLOAT64
                    else -> UNKNOWN
                }
            }
        }
    }
    
    /**
     * Verifica si una coordenada está dentro del ráster.
     */
    fun containsCoordinate(lon: Double, lat: Double): Boolean {
        return bounds.contains(lon, lat)
    }
    
    /**
     * Convierte coordenadas geográficas a índice de píxel.
     * Verifica que esté dentro de los límites del ráster.
     * 
     * @return Pair<col, row> o null si está fuera
     */
    fun getPixelCoordinates(lon: Double, lat: Double): Pair<Int, Int>? {
        if (!containsCoordinate(lon, lat)) return null
        
        val pixel = geoTransform.geoToPixel(lon, lat) ?: return null
        val (col, row) = pixel
        
        // Verificar que esté dentro del ráster
        if (col < 0 || col >= width || row < 0 || row >= height) {
            return null
        }
        
        return Pair(col, row)
    }
    
    /**
     * Calcula el tamaño del archivo aproximado en MB.
     */
    fun estimatedSizeMB(): Double {
        val bytesPerPixel = when (dataType) {
            DataType.BYTE -> 1
            DataType.INT16, DataType.UINT16 -> 2
            DataType.INT32, DataType.UINT32, DataType.FLOAT32 -> 4
            DataType.FLOAT64 -> 8
            DataType.UNKNOWN -> 4 // Asumimos Float32
        }
        val totalBytes = width.toLong() * height.toLong() * bandCount * bytesPerPixel
        return totalBytes / (1024.0 * 1024.0)
    }
    
    /**
     * Información resumida en formato legible.
     */
    override fun toString(): String {
        return buildString {
            appendLine("GeoTIFF Info:")
            appendLine("  Dimensiones: ${width}x${height} píxeles")
            appendLine("  Bandas: $bandCount")
            appendLine("  Tipo: $dataType")
            appendLine("  Tamaño píxel: ${String.format("%.6f", pixelSizeX)}° x ${String.format("%.6f", Math.abs(pixelSizeY))}°")
            appendLine("  Bounds: [${String.format("%.6f", bounds.minLon)}, ${String.format("%.6f", bounds.minLat)}, " +
                       "${String.format("%.6f", bounds.maxLon)}, ${String.format("%.6f", bounds.maxLat)}]")
            appendLine("  NoData: ${noDataValue ?: "N/A"}")
            appendLine("  CRS: $crs")
            appendLine("  Tamaño estimado: ${String.format("%.2f", estimatedSizeMB())} MB")
            compression?.let { appendLine("  Compresión: $it") }
        }
    }
}
