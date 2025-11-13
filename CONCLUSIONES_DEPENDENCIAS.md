# Conclusiones y Lecciones sobre la Actualización de Dependencias

Este documento resume los problemas encontrados durante la actualización de dependencias del proyecto y las soluciones aplicadas. El objetivo es servir como guía para evitar incurrir en los mismos errores en el futuro.

## Resumen del Proceso

El proceso comenzó con la intención de eliminar los *warnings* del `check` de CI/CD, que sugerían actualizar las versiones de varias librerías. La actualización masiva inicial desencadenó una serie de errores de compilación y de ejecución en cadena que tuvimos que resolver paso a paso.

---

## Incidencias Resueltas

### 1. Incompatibilidad de `compileSdk`

- **Error:** `Dependency 'androidx.core:core:1.17.0' requires libraries and applications that depend on it to compile against version 36 or later of the Android APIs.`
- **Causa:** Las nuevas versiones de las librerías de AndroidX (`core`, `activity`, etc.) requerían que el proyecto se compilara con el SDK de Android 36. Nuestro proyecto estaba configurado para usar la versión 35.
- **Solución:** Se modificó el fichero `app/build.gradle.kts` para actualizar `compileSdk` y `targetSdk` de `35` a `36`.

### 2. Incompatibilidad de Versiones de Kotlin

- **Error:** `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.2.0, expected version is 2.0.0.`
- **Causa:** Se actualizaron algunas librerías (`okhttp`, `retrofit`) a versiones muy recientes que habían sido compiladas con Kotlin `2.2.0`. Sin embargo, el proyecto seguía usando Kotlin `2.0.0`, lo que generó un conflicto de incompatibilidad binaria.
- **Solución:** Se revirtieron las versiones de las librerías en conflicto (`okhttp`, `retrofit`, `coroutines`, etc.) en el fichero `gradle/libs.versions.toml` a sus versiones originales, que eran compatibles con Kotlin `2.0.0`.

### 3. Crash en Runtime por cambios en la API de Compose

- **Error:** `java.lang.IllegalArgumentException: clickable only supports IndicationNodeFactory...`.
- **Causa:** Una de las actualizaciones de Jetpack Compose introdujo un cambio en la API interna del modificador `.clickable`. La forma simple en que se estaba usando en `MapScreen.kt` quedó obsoleta y provocaba un `crash` en tiempo de ejecución al no ser compatible con el nuevo sistema de `IndicationNodeFactory`.
- **Solución:** Se actualizó la llamada al modificador `.clickable` para usar su sobrecarga explícita, proveyendo `interactionSource` y `indication` (el efecto *ripple*) manualmente. Esto satisface los nuevos requisitos de la API.

---

## Buenas Prácticas y Recomendaciones para el Futuro

Para evitar que estos problemas se repitan, se recomienda seguir estas pautas:

1.  **Realizar Actualizaciones Incrementales**:
    - No actualices todas las dependencias a la vez. Ve por grupos pequeños y lógicos (ej: todas las librerías de Compose, luego las de red, etc.).
    - Compila y ejecuta la aplicación después de cada pequeño grupo de actualizaciones para aislar rápidamente cualquier problema.

2.  **Revisar los Requisitos de las Librerías**:
    - Antes de actualizar, especialmente en saltos de versión mayores, consulta las "release notes" (notas de la versión) de la librería. Suelen advertir sobre nuevos requisitos, como la necesidad de una `compileSdk` más alta o una versión específica de Kotlin.

3.  **Atención a la Versión de Kotlin**:
    - La compatibilidad de la versión de Kotlin es fundamental. Un error de "incompatible version of Kotlin" es una señal clara de conflicto. La solución más segura es revertir la actualización de esa librería hasta que estés listo para actualizar la versión de Kotlin de todo el proyecto.

4.  **Confiar en Logcat para Errores de Ejecución**:
    - Si la aplicación compila pero se cierra al iniciar, **Logcat** es tu mejor herramienta. Filtra por el nombre del paquete de tu app y busca mensajes con severidad `Error` (E) o `Fatal` (F). Como vimos, el mensaje de error a menudo describe la causa exacta y puede sugerir la solución.

5.  **Priorizar Versiones Estables**:
    - Evita usar versiones `alpha`, `beta` o `rc` (Release Candidate) a menos que sea estrictamente necesario para obtener una funcionalidad específica. Las versiones estables son siempre la apuesta más segura.
