package com.dasomaps.app.data.model

/**
 * Enum para representar los diferentes estados de los permisos de ubicación.
 */
enum class LocationState {
    /**
     * Estado inicial, aún no se ha solicitado el permiso.
     */
    Initial,

    /**
     * El usuario ha concedido el permiso de ubicación.
     */
    PermissionGranted,

    /**
     * El usuario ha denegado el permiso de ubicación.
     */
    PermissionDenied,

    /**
     * Es necesario mostrar una justificación al usuario antes de volver a solicitar el permiso.
     */
    ShowRationale
}
