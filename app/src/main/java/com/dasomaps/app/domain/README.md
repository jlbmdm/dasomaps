# Domain Layer

Capa de dominio que contiene la lógica de negocio de la aplicación.

## Estructura

### `/usecase`
Casos de uso que encapsulan la lógica de negocio:
- Operaciones con capas
- Gestión de geometrías
- Consultas de valores de píxeles
- Sincronización de datos

## Principios

- **Clean Architecture**: Independiente de frameworks y UI
- **Testeable**: Fácil de probar de forma unitaria
- **Reutilizable**: Los casos de uso pueden ser compartidos entre diferentes partes de la UI

## Ejemplo de Caso de Uso

```kotlin
class GetLayerValueAtPointUseCase(
    private val rasterRepository: RasterRepository
) {
    suspend operator fun invoke(layerId: String, lat: Double, lon: Double): Result<Double> {
        // Lógica de negocio
    }
}
```
