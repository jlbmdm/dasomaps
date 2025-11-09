package com.dasomaps.app.utils

import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Utilidades para conversión de coordenadas.
 * 
 * Soporta:
 * - WGS84 (lat/lon) <-> UTM huso 30 (EPSG:25830)
 * - Formateo de coordenadas
 * - Cálculo de escala de mapa
 */
object CoordinateUtils {

    /**
     * Datos de coordenadas con múltiples formatos.
     */
    data class CoordinateInfo(
        val latitude: Double,
        val longitude: Double,
        val utmEasting: Double,
        val utmNorthing: Double,
        val utmZone: Int = 30,
        val latitudeStr: String,
        val longitudeStr: String,
        val utmStr: String,
        val zoomLevel: Int = 15,
        val scaleValue: String = "1:50000",  // Escala como texto (ej: "1:5000")
        val scaleFormattedWithZoom: String = "1:50.000 | zoom: 15"  // Con miles y zoom
    )

    /**
     * Convierte un GeoPoint a información completa de coordenadas.
     *
     * @param geoPoint Punto geográfico en WGS84
     * @param zoomLevel Nivel de zoom del mapa (para calcular escala)
     * @return CoordinateInfo con todos los formatos
     */
    fun getCoordinateInfo(geoPoint: GeoPoint, zoomLevel: Int = 15): CoordinateInfo {
        val lat = geoPoint.latitude
        val lon = geoPoint.longitude
        
        // Convertir a UTM huso 30
        val utm = wgs84ToUtm(lat, lon, utmZone = 30)
        
        // Calcular escala del mapa
        val scaleValue = calculateMapScale(lat, zoomLevel)
        
        // Formato de escala con miles y zoom
        val scaleFormatted = formatScaleWithThousandsAndZoom(scaleValue, zoomLevel)
        
        return CoordinateInfo(
            latitude = lat,
            longitude = lon,
            utmEasting = utm.first,
            utmNorthing = utm.second,
            utmZone = 30,
            latitudeStr = formatLatitude(lat),
            longitudeStr = formatLongitude(lon),
            utmStr = formatUtm(utm.first, utm.second, 30),
            zoomLevel = zoomLevel,
            scaleValue = scaleValue,
            scaleFormattedWithZoom = scaleFormatted
        )
    }

    /**
     * Calcula la escala del mapa basada en el nivel de zoom.
     * 
     * En osmdroid (Web Mercator):
     * - Zoom 0: mundo entero (1 tile)
     * - Zoom 18: ~1.4m por píxel
     * - Zoom 20: ~0.35m por píxel
     * 
     * La fórmula es: metersPerPixel = 40075017 / (256 * 2^zoom)
     * 
     * @param latitude Latitud (para ajustar por coseno)
     * @param zoomLevel Nivel de zoom (0-28)
     * @return String con escala en formato "1:XXXXX"
     */
    fun calculateMapScale(latitude: Double, zoomLevel: Int): String {
        // Circunferencia de la Tierra en metros (Web Mercator)
        val earthCircumference = 40075017.0
        
        // Metros por píxel en el ecuador a este zoom
        val pixelSize = earthCircumference / (256.0 * (1 shl zoomLevel))  // 1 shl zoomLevel = 2^zoomLevel
        
        // Ajustar por latitud (los píxeles son más pequeños lejos del ecuador)
        val cosLat = cos(Math.toRadians(latitude))
        val pixelSizeAdjusted = pixelSize / cosLat
        
        // Asumir resolución de pantalla estándar (~100 DPI)
        val screenDpi = 96.0  // Píxeles por pulgada estándar
        val inchesPerMeter = 39.3701
        val pixelsPerInch = screenDpi
        val pixelsPerMeter = pixelsPerInch * inchesPerMeter
        
        // Metros por píxel en pantalla (asumiendo densidad estándar)
        val metersPerScreenPixel = pixelSizeAdjusted / pixelsPerMeter * 100.0
        
        // Escala de mapa (considerando que 1 unidad en el mapa = X metros en terreno)
        // Para simplificar, usamos una aproximación común
        val approxScale = (pixelSizeAdjusted * 39.3701 * 100 / 12).toInt()
        
        // Redondear a valores "bonitos" (potencias de 10 y múltiplos comunes)
        val scaleRounded = roundToNiceScale(approxScale)
        
        return "1:$scaleRounded"
    }

    /**
     * Formatea la escala con separador de miles y nivel de zoom de Google Maps.
     *
     * @param scaleValue Escala en número (ej: "1:5000")
     * @param zoomLevel Nivel de zoom (0-28)
     * @return String formateado (ej: "1:5.000 | zoom: 15")
     */
    fun formatScaleWithThousandsAndZoom(scaleValue: String, zoomLevel: Int): String {
        // Extraer el número de la escala (ej: "1:5000" -> "5000")
        val scaleNumber = scaleValue.split(":").getOrNull(1)?.toLongOrNull() ?: 0L
        
        // Formatear con separador de miles (punto)
        val formatted = when {
            scaleNumber >= 1_000_000 -> {
                val millions = scaleNumber / 1_000_000
                val remainder = (scaleNumber % 1_000_000) / 1_000
                if (remainder > 0) "$millions.${String.format("%03d", remainder)}" else "$millions.000"
            }
            scaleNumber >= 1_000 -> {
                val thousands = scaleNumber / 1_000
                val remainder = scaleNumber % 1_000
                if (remainder > 0) "$thousands.${String.format("%03d", remainder)}" else "$thousands.000"
            }
            else -> scaleNumber.toString()
        }
        
        return "1:$formatted | zoom: $zoomLevel"
    }

    /**
     * Redondea un valor de escala a números "bonitos" (500, 1000, 2000, 5000, 10000, etc)
     */
    private fun roundToNiceScale(scale: Int): Int {
        if (scale <= 0) return 1000
        
        // Encontrar el orden de magnitud
        val magnitude = 10.0.pow(floor(log10(scale.toDouble())))
        val normalized = scale / magnitude
        
        // Redondear a 1, 2, 5, 10
        val rounded = when {
            normalized <= 1.5 -> 1.0
            normalized <= 3.0 -> 2.0
            normalized <= 7.0 -> 5.0
            else -> 10.0
        }
        
        return (rounded * magnitude).toInt()
    }

    /**
     * Convierte coordenadas WGS84 a UTM.
     *
     * @param latitude Latitud en grados decimales
     * @param longitude Longitud en grados decimales
     * @param utmZone Huso UTM (por defecto 30 para España)
     * @return Par (Easting, Northing) en metros
     */
    fun wgs84ToUtm(latitude: Double, longitude: Double, utmZone: Int = 30): Pair<Double, Double> {
        // Constantes del elipsoide WGS84
        val a = 6378137.0  // Semi-eje mayor (metros)
        val f = 1.0 / 298.257223563  // Aplanamiento
        val b = a * (1 - f)  // Semi-eje menor
        val e = sqrt(1 - (b * b) / (a * a))  // Excentricidad
        val e2 = e * e / (1 - e * e)  // Segunda excentricidad
        
        // Parámetros de la proyección UTM
        val k0 = 0.9996  // Factor de escala
        val E0 = 500000.0  // False Easting
        val N0 = 0.0  // False Northing (hemisferio norte)
        
        // Convertir a radianes
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)
        
        // Calcular longitud central del huso
        val lonOriginRad = Math.toRadians((utmZone - 1) * 6.0 - 180.0 + 3.0)
        val lonDiff = lonRad - lonOriginRad
        
        // Cálculos intermedios
        val N = a / sqrt(1 - e * e * sin(latRad) * sin(latRad))
        val T = tan(latRad) * tan(latRad)
        val C = e2 * cos(latRad) * cos(latRad)
        val A = cos(latRad) * lonDiff
        
        // M - Longitud del arco meridiano
        val M = a * ((1 - e * e / 4 - 3 * e * e * e * e / 64 - 5 * e * e * e * e * e * e / 256) * latRad -
                (3 * e * e / 8 + 3 * e * e * e * e / 32 + 45 * e * e * e * e * e * e / 1024) * sin(2 * latRad) +
                (15 * e * e * e * e / 256 + 45 * e * e * e * e * e * e / 1024) * sin(4 * latRad) -
                (35 * e * e * e * e * e * e / 3072) * sin(6 * latRad))
        
        // Calcular coordenadas UTM
        val easting = E0 + k0 * N * (A + (1 - T + C) * A * A * A / 6 +
                (5 - 18 * T + T * T + 72 * C - 58 * e2) * A * A * A * A * A / 120)
        
        val northing = N0 + k0 * (M + N * tan(latRad) * (A * A / 2 +
                (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24 +
                (61 - 58 * T + T * T + 600 * C - 330 * e2) * A * A * A * A * A * A / 720))
        
        return Pair(easting, northing)
    }

    /**
     * Formatea latitud en grados, minutos, segundos.
     *
     * @param latitude Latitud en grados decimales
     * @return String formateado (ej: "41° 39' 8.28\" N")
     */
    fun formatLatitude(latitude: Double): String {
        val absLat = abs(latitude)
        val degrees = absLat.toInt()
        val minutes = ((absLat - degrees) * 60).toInt()
        val seconds = ((absLat - degrees - minutes / 60.0) * 3600)
        val direction = if (latitude >= 0) "N" else "S"
        
        return String.format("%d° %d' %.2f\" %s", degrees, minutes, seconds, direction)
    }

    /**
     * Formatea longitud en grados, minutos, segundos.
     *
     * @param longitude Longitud en grados decimales
     * @return String formateado (ej: "4° 43' 28.20\" W")
     */
    fun formatLongitude(longitude: Double): String {
        val absLon = abs(longitude)
        val degrees = absLon.toInt()
        val minutes = ((absLon - degrees) * 60).toInt()
        val seconds = ((absLon - degrees - minutes / 60.0) * 3600)
        val direction = if (longitude >= 0) "E" else "W"
        
        return String.format("%d° %d' %.2f\" %s", degrees, minutes, seconds, direction)
    }

    /**
     * Formatea coordenadas UTM.
     *
     * @param easting Coordenada Este en metros
     * @param northing Coordenada Norte en metros
     * @param zone Huso UTM
     * @return String formateado (ej: "30N 400123.45 E, 4612345.67 N")
     */
    fun formatUtm(easting: Double, northing: Double, zone: Int): String {
        return String.format("%dN %.2f E, %.2f N", zone, easting, northing)
    }

    /**
     * Formatea latitud en grados decimales.
     *
     * @param latitude Latitud
     * @return String formateado (ej: "41.6523°")
     */
    fun formatLatitudeDecimal(latitude: Double): String {
        return String.format("%.6f°", latitude)
    }

    /**
     * Formatea longitud en grados decimales.
     *
     * @param longitude Longitud
     * @return String formateado (ej: "-4.7245°")
     */
    fun formatLongitudeDecimal(longitude: Double): String {
        return String.format("%.6f°", longitude)
    }

    /**
     * Verifica si las coordenadas están en el rango de España.
     *
     * @param latitude Latitud en grados decimales
     * @param longitude Longitud en grados decimales
     * @return true si está en el rango 40-45° N, -7 a +3° E
     */
    fun isInSpainRange(latitude: Double, longitude: Double): Boolean {
        return latitude in 40.0..45.0 && longitude in -7.0..3.0
    }

    /**
     * Verifica si un bounds está en el rango de España.
     *
     * @param bounds Lista de [minLon, minLat, maxLon, maxLat]
     * @return true si el centro del bounds está en España
     */
    fun isInSpainRange(bounds: List<Double>): Boolean {
        if (bounds.size < 4) return false
        
        val centerLat = (bounds[1] + bounds[3]) / 2.0
        val centerLon = (bounds[0] + bounds[2]) / 2.0
        
        return isInSpainRange(centerLat, centerLon)
    }
}
