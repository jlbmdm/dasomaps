package com.dasomaps.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extensiones útiles para tipos comunes en Kotlin.
 */

// ========== Extensiones para Double (Coordenadas) ==========

/**
 * Formatea un valor Double como coordenada con 6 decimales.
 * Ejemplo: 41.652345 -> "41.652345"
 */
fun Double.toCoordinateString(): String = "%.6f".format(Locale.US, this)

/**
 * Formatea latitud con indicador N/S.
 * Ejemplo: 41.652345 -> "41.652345° N"
 */
fun Double.toLatitudeString(): String {
    val direction = if (this >= 0) "N" else "S"
    return "${kotlin.math.abs(this).toCoordinateString()}° $direction"
}

/**
 * Formatea longitud con indicador E/W.
 * Ejemplo: -4.724523 -> "4.724523° W"
 */
fun Double.toLongitudeString(): String {
    val direction = if (this >= 0) "E" else "W"
    return "${kotlin.math.abs(this).toCoordinateString()}° $direction"
}

/**
 * Convierte grados a radianes.
 */
fun Double.toRadians(): Double = Math.toRadians(this)

/**
 * Convierte radianes a grados.
 */
fun Double.toDegrees(): Double = Math.toDegrees(this)


// ========== Extensiones para Long (Timestamps) ==========

/**
 * Formatea un timestamp (Long) como fecha y hora legible.
 * Formato: "dd/MM/yyyy HH:mm:ss"
 */
fun Long.toDateTimeString(): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(this))
}

/**
 * Formatea un timestamp (Long) solo como fecha.
 * Formato: "dd/MM/yyyy"
 */
fun Long.toDateString(): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formatter.format(Date(this))
}

/**
 * Formatea un timestamp (Long) solo como hora.
 * Formato: "HH:mm:ss"
 */
fun Long.toTimeString(): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(this))
}


// ========== Extensiones para Float (Opacidad) ==========

/**
 * Asegura que el valor Float esté en el rango [0.0, 1.0].
 */
fun Float.clampOpacity(): Float = this.coerceIn(0.0f, 1.0f)

/**
 * Convierte opacidad (0.0-1.0) a porcentaje.
 * Ejemplo: 0.75f -> "75%"
 */
fun Float.toPercentageString(): String = "${(this * 100).toInt()}%"


// ========== Extensiones para String ==========

/**
 * Verifica si el String es una coordenada válida.
 */
fun String.isValidCoordinate(): Boolean {
    return try {
        val value = this.toDouble()
        value in -180.0..180.0
    } catch (e: NumberFormatException) {
        false
    }
}

/**
 * Trunca un String a una longitud máxima añadiendo "..." si es necesario.
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) {
        "${this.substring(0, maxLength - 3)}..."
    } else {
        this
    }
}


// ========== Cálculos Geoespaciales ==========

/**
 * Calcula la distancia en metros entre dos puntos usando la fórmula de Haversine.
 *
 * @param lat1 Latitud del primer punto
 * @param lon1 Longitud del primer punto
 * @param lat2 Latitud del segundo punto
 * @param lon2 Longitud del segundo punto
 * @return Distancia en metros
 */
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // Radio de la Tierra en metros

    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()

    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1.toRadians()) * kotlin.math.cos(lat2.toRadians()) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

    return earthRadius * c
}

/**
 * Formatea una distancia en metros a un String legible.
 * Ejemplo: 1500.0 -> "1.5 km"
 */
fun Double.toDistanceString(): String {
    return when {
        this < 1000 -> "%.0f m".format(Locale.US, this)
        else -> "%.2f km".format(Locale.US, this / 1000.0)
    }
}

/**
 * Formatea un área en metros cuadrados a un String legible.
 * Ejemplo: 15000.0 -> "1.5 ha"
 */
fun Double.toAreaString(): String {
    return when {
        this < 10000 -> "%.0f m²".format(Locale.US, this)
        this < 1000000 -> "%.2f ha".format(Locale.US, this / 10000.0)
        else -> "%.2f km²".format(Locale.US, this / 1000000.0)
    }
}
