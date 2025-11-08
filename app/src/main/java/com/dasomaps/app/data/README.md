# Data Layer

Esta capa contiene toda la lógica de acceso y gestión de datos de la aplicación.

## Estructura

### `/model`
Clases de datos (data classes) que representan las entidades del dominio:
- Capas ráster
- Geometrías (puntos, líneas, polígonos)
- Configuración de la aplicación

### `/local`
Gestión de datos locales:
- Base de datos Room
- DAOs (Data Access Objects)
- Entidades de base de datos
- Cache de archivos

### `/remote`
Gestión de datos remotos:
- Cliente PostGIS
- APIs remotas
- Servicios de red

### `/repository`
Implementación del patrón Repository:
- Abstracción entre fuentes de datos
- Lógica de sincronización online/offline
- Cache inteligente

## Principios

- **Single Source of Truth**: Los repositorios son la única fuente de verdad
- **Separation of Concerns**: Cada capa tiene responsabilidades claras
- **Offline First**: Priorizar funcionamiento sin conexión
