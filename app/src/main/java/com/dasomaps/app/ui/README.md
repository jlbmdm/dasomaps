# UI Layer

Capa de presentación usando Jetpack Compose y arquitectura MVVM.

## Estructura

### `/map`
Pantalla principal del mapa:
- MapScreen (Composable principal)
- MapViewModel (estado y lógica)
- Componentes relacionados con visualización

### `/layers`
Gestión de capas:
- Lista de capas disponibles
- Activar/desactivar capas
- Configuración de visualización

### `/geometry`
Herramientas de captura de geometrías:
- Modo de dibujo (puntos, líneas, polígonos)
- Edición de geometrías
- Lista de geometrías guardadas

### `/components`
Componentes Compose reutilizables:
- Botones personalizados
- Diálogos
- Controles de mapa

### `/theme`
Tema de la aplicación:
- Colores
- Tipografía
- Material Design 3

## Principios

- **Unidirectional Data Flow**: Los datos fluyen en una dirección
- **State Hoisting**: El estado se eleva a composables padres
- **ViewModel como fuente de verdad**: ViewModels gestionan el estado de UI
- **Composición sobre herencia**: Reutilizar mediante composición

## Patrón MVVM

```
View (Composable) <--> ViewModel <--> Repository
```

- **View**: UI reactiva con Compose
- **ViewModel**: Estado y lógica de presentación
- **Repository**: Acceso a datos
