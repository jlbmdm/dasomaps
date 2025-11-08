# Utils

Utilidades y helpers comunes para toda la aplicación.

## Contenido

### Extensiones
- Extensiones de Kotlin para tipos comunes
- Helpers de conversión de coordenadas
- Formateo de datos

### Constantes
- Constantes de la aplicación
- Valores por defecto
- Configuraciones globales

### Helpers
- Gestión de permisos
- Conversiones de unidades
- Utilidades geoespaciales

### Network
- Gestión de conectividad
- Detección de red disponible

## Ejemplos

```kotlin
// Extensión para formatear coordenadas
fun Double.toCoordinateString(): String = "%.6f".format(this)

// Helper de permisos
object PermissionHelper {
    fun hasLocationPermission(context: Context): Boolean
}

// Utilidad de conectividad
object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean
}
```
