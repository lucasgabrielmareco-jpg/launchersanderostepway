package com.lucas.sanderolauncher

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío: solo existe para que Android permita a la app
 * leer las sesiones de medios activas (Spotify) una vez que el usuario
 * concede "Acceso a notificaciones".
 */
class MediaListenerService : NotificationListenerService()
