package com.dasomaps.app.data.model

/**
 * Representa el valor consultado de un píxel en una capa ráster.
 * 
 * Este modelo se usa para mostrar información dasométrica cuando el usuario
 * toca un punto en el mapa sobre una capa GeoTIFF activa.
 * 
 * @property layerId ID de la capa ráster consultada
 * @property layerName Nombre descriptivo de la capa
 * @property latitude Latitud WGS84 del punto consultado
 * @property longitude Longitud WGS84 del punto consultado
 * @property values Valores de cada banda (índice 0 = banda 1)
 * @property bandNames Nombres descriptivos de las bandas (opcional)
 * @property unit Unidad de medida (ej: "m", "m³/ha", "árboles/ha")
 * @property noDataValue Valor que representa ausencia de datos
 * @property timestamp Momento de la consulta
 */
data class RasterValue(
    val layerId: String,
    val layerName: String,
    val latitude: Double,
    val longitude: Double,
    val values: List<Double>,
    val bandNames: List<String>? = null,
    val unit: String? = null,
    val noDataValue: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Obtiene el valor de la primera banda (uso común para ráster de una sola banda).
     */
    val primaryValue: Double
        get() = values.firstOrNull() ?: Double.NaN
    
    /**
     * Verifica si el valor consultado corresponde a NoData.
     */
    fun isNoData(): Boolean {
        return noDataValue != null && values.any { it == noDataValue }
    }
    
    /**
     * Verifica si hay datos válidos en todas las bandas.
     */
    fun hasValidData(): Boolean {
        return !isNoData() && values.all { !it.isNaN() && it.isFinite() }
    }
    
    /**
     * Obtiene el valor de una banda específica (índice basado en 1).
     * @param bandNumber Número de banda (1-based)
     * @return Valor de la banda o null si no existe
     */
    fun getBandValue(bandNumber: Int): Double? {
        val index = bandNumber - 1
        return values.getOrNull(index)
    }
    
    /**
     * Formatea el valor principal con su unidad.
     * Ej: "845.3 m" o "123.5 m³/ha"
     */
    fun formatPrimaryValue(): String {
        if (!hasValidData()) return "Sin datos"
        val formatted = String.format("%.2f", primaryValue)
        return if (unit != null) "$formatted $unit" else formatted
    }
    
    /**
     * Formatea todos los valores de las bandas.
     * @return Lista de strings con formato "Banda X: valor unidad"
     */
    fun formatAllBands(): List<String> {
        return values.mapIndexed { index, value ->
            val bandName = bandNames?.getOrNull(index) ?: "Banda ${index + 1}"
            val formatted = if (value.isNaN() || (noDataValue != null && value == noDataValue)) {
                "Sin datos"
            } else {
                val valueStr = String.format("%.2f", value)
                if (unit != null) "$valueStr $unit" else valueStr
            }
            "$bandName: $formatted"
        }
    }
}
