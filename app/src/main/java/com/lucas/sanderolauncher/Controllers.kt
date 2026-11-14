package com.lucas.sanderolauncher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

fun locationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// ---------------------------------------------------------------
//  Música (Spotify)
// ---------------------------------------------------------------
class MusicController(private val context: Context) {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    var positionMs by mutableLongStateOf(0L)
    var hasAccess by mutableStateOf(false)
    var hasSession by mutableStateOf(false)

    private val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, MediaListenerService::class.java)
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { readMetadata(metadata) }
        override fun onPlaybackStateChanged(state: PlaybackState?) { readState(state) }
        override fun onSessionDestroyed() { hasSession = false }
    }
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    fun start() {
        stop()
        try {
            val sessions = msm.getActiveSessions(component)
            hasAccess = true
            msm.addOnActiveSessionsChangedListener(listener, component)
            pick(sessions)
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(listener) } catch (_: Exception) {}
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick(list: List<MediaController>?) {
        val chosen = list?.firstOrNull {
            it.packageName == "com.spotify.music" && it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull { it.packageName == "com.spotify.music" }
            ?: list?.firstOrNull()

        controller?.unregisterCallback(callback)
        controller = chosen
        hasSession = chosen != null
        if (chosen != null) {
            chosen.registerCallback(callback)
            readMetadata(chosen.metadata)
            readState(chosen.playbackState)
        } else {
            title = ""; artist = ""; art = null; playing = false
            durationMs = 0L; positionMs = 0L
        }
    }

    private fun readMetadata(m: MediaMetadata?) {
        title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val bmp: Bitmap? = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        art = bmp?.asImageBitmap()
    }

    private fun readState(s: PlaybackState?) {
        playing = s?.state == PlaybackState.STATE_PLAYING
        positionMs = s?.position ?: 0L
    }

    fun refreshPosition() {
        val s = controller?.playbackState ?: return
        var pos = s.position
        if (s.state == PlaybackState.STATE_PLAYING) {
            pos += ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
        }
        positionMs = if (durationMs > 0) pos.coerceIn(0L, durationMs) else pos
    }

    fun playPause() {
        val c = controller?.transportControls ?: return
        if (playing) c.pause() else c.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}


// ---------------------------------------------------------------
//  Ubicación y velocidad (GPS del autobox)
// ---------------------------------------------------------------
class LocationTracker(private val context: Context) : LocationListener {
    var lat by mutableDoubleStateOf(0.0)
    var lon by mutableDoubleStateOf(0.0)
    var speedKmh by mutableIntStateOf(0)
    var hasFix by mutableStateOf(false)

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        if (!locationGranted(context)) return
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(provider)) lm.requestLocationUpdates(provider, 1000L, 0f, this)
            }
            val last = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (last != null) apply(last, updateSpeed = false)
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        try { lm.removeUpdates(this) } catch (_: Exception) {}
    }

    private fun apply(l: Location, updateSpeed: Boolean) {
        lat = l.latitude
        lon = l.longitude
        hasFix = true
        if (updateSpeed && l.provider == LocationManager.GPS_PROVIDER) {
            val kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
            speedKmh = if (kmh < 3f) 0 else kmh.roundToInt()
        }
    }

    override fun onLocationChanged(location: Location) { apply(location, updateSpeed = true) }
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

// ---------------------------------------------------------------
//  Estado de Wi-Fi y Bluetooth
// ---------------------------------------------------------------
class SystemStatus(context: Context) {
    private val appContext = context.applicationContext
    var wifiOn by mutableStateOf(false)
    var wifiName by mutableStateOf("")
    var btOn by mutableStateOf(false)

    @Suppress("DEPRECATION")
    fun refresh() {
        try {
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiOn = wm.isWifiEnabled
            val info = wm.connectionInfo
            wifiName = if (wifiOn && info != null && info.networkId != -1) {
                val s = info.ssid?.trim('"') ?: ""
                if (s.isEmpty() || s == "<unknown ssid>") "Conectado" else s
            } else ""
        } catch (_: Exception) {}
        try {
            val bm = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            btOn = bm.adapter?.isEnabled == true
        } catch (_: Exception) {}
    }
}

// ---------------------------------------------------------------
//  Clima (Open-Meteo, sin API key) y nombre del barrio
// ---------------------------------------------------------------
data class WeatherInfo(val temp: Int, val desc: String, val icon: String)

private fun describe(code: Int): Pair<String, String> = when (code) {
    0 -> "Despejado" to "☀️"
    1, 2 -> "Parcialmente nublado" to "⛅"
    3 -> "Nublado" to "☁️"
    45, 48 -> "Niebla" to "🌫️"
    in 51..57 -> "Llovizna" to "🌦️"
    in 61..67 -> "Lluvia" to "🌧️"
    in 71..77 -> "Nieve" to "❄️"
    in 80..82 -> "Chubascos" to "🌧️"
    in 95..99 -> "Tormenta" to "⛈️"
    else -> "Sin datos" to "🌡️"
}

suspend fun fetchWeather(lat: Double, lon: Double): WeatherInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val cur = JSONObject(body).getJSONObject("current")
        val (desc, icon) = describe(cur.getInt("weather_code"))
        WeatherInfo(Math.round(cur.getDouble("temperature_2m")).toInt(), desc, icon)
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchPlace(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
    try {
        if (!Geocoder.isPresent()) return@withContext null
        @Suppress("DEPRECATION")
        val list = Geocoder(context, Locale("es")).getFromLocation(lat, lon, 1)
        val a = list?.firstOrNull()
        a?.subLocality ?: a?.locality
    } catch (e: Exception) {
        null
    }
}
