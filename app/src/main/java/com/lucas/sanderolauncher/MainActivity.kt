package com.lucas.sanderolauncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppItem(val label: String, val packageName: String, val icon: ImageBitmap)

private enum class Screen { HOME, MAP, APPS }

private const val CAR_NAME = "RENAULT SANDERO STEPWAY"
private val SHORTCUT_NAMES = listOf("Casa", "Trabajo", "Favorito", "Gym")

class MainActivity : ComponentActivity() {
    private lateinit var music: MusicController
    private lateinit var tracker: LocationTracker
    private lateinit var status: SystemStatus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        music = MusicController(applicationContext)
        tracker = LocationTracker(applicationContext)
        status = SystemStatus(applicationContext)
        setContent { LauncherScreen(music, tracker, status) }
    }

    override fun onResume() {
        super.onResume()
        music.start()
        tracker.start()
    }

    override fun onPause() {
        super.onPause()
        music.stop()
        tracker.stop()
    }
}

// ---------------------------------------------------------------
//  Utilidades
// ---------------------------------------------------------------
private fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map {
            AppItem(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun launch(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

// Busca una app por paquete y, si no está, por nombre
private fun findApp(apps: List<AppItem>, packages: List<String>, labels: List<String>): AppItem? =
    apps.firstOrNull { it.packageName in packages }
        ?: apps.firstOrNull { a -> labels.any { a.label.contains(it, ignoreCase = true) } }

private fun openSettingsScreen(context: Context, vararg actions: String) {
    for (action in actions) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

private fun openDialer(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private fun navigateTo(context: Context, dest: String) {
    val uri = Uri.parse("google.navigation:q=" + Uri.encode(dest))
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

private fun subtitleFor(v: String?): String = when {
    v.isNullOrEmpty() -> "Toca para configurar"
    isCoords(v) -> "Ubicación guardada"
    else -> v
}

@Composable
private fun AppIcon(app: AppItem, size: Dp) {
    Image(app.icon, app.label, Modifier.size(size).clip(RoundedCornerShape(10.dp)))
}

// ---------------------------------------------------------------
//  Pantalla principal
// ---------------------------------------------------------------
@Composable
fun LauncherScreen(music: MusicController, tracker: LocationTracker, status: SystemStatus) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    val shortcutPrefs = remember { context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE) }

    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    val p = if (dark) DarkPalette else LightPalette

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editing by remember { mutableStateOf<String?>(null) }
    val shortcuts = remember {
        mutableStateMapOf<String, String>().apply {
            SHORTCUT_NAMES.forEach { n -> shortcutPrefs.getString(n, null)?.let { put(n, it) } }
        }
    }

    // Permiso de ubicación
    var hasLocation by remember { mutableStateOf(locationGranted(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = locationGranted(context)
        tracker.start()
    }
    val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(Unit) { if (!hasLocation) permLauncher.launch(perms) }

    LaunchedEffect(Unit) { apps = loadApps(context) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    // Wi-Fi / Bluetooth
    LaunchedEffect(Unit) { while (true) { status.refresh(); delay(3000) } }

    // Reloj
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(10_000) } }
    val es = Locale("es", "ES")
    val timeText = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    val dateShort = now.format(DateTimeFormatter.ofPattern("EEE, d MMM", es)).replaceFirstChar { it.uppercase() }
    val dateLong = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)).replaceFirstChar { it.uppercase() }

    // Clima y barrio (cuando hay ubicación)
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    val hasFix = tracker.hasFix
    LaunchedEffect(hasFix) {
        while (hasFix) {
            fetchWeather(tracker.lat, tracker.lon)?.let { weather = it }
            delay(15 * 60_000L)
        }
    }
    LaunchedEffect(hasFix) {
        while (hasFix) {
            fetchPlace(context, tracker.lat, tracker.lon)?.let { place = it }
            delay(5 * 60_000L)
        }
    }

    val maps = findApp(apps, listOf("com.google.android.apps.maps", "com.google.android.apps.mapslite"), listOf("maps"))
    val spotify = findApp(apps, listOf("com.spotify.music"), listOf("spotify"))
    val whatsapp = findApp(apps, listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp"))
    val carplay = findApp(apps, emptyList(), listOf("carplay", "car play", "carlink", "zlink", "carlife", "android auto"))
    val radio = findApp(apps, emptyList(), listOf("radio"))

    fun tint(sel: Boolean) = if (sel) p.text else p.sub
    val nav = buildList<NavEntry> {
        add(NavEntry("Resumen", screen == Screen.HOME, { screen = Screen.HOME }) {
            Icon(Icons.Filled.Home, null, tint = tint(screen == Screen.HOME), modifier = Modifier.size(32.dp))
        })
        add(NavEntry("Mapa", screen == Screen.MAP, { screen = Screen.MAP }) {
            Icon(Icons.Filled.Map, null, tint = tint(screen == Screen.MAP), modifier = Modifier.size(32.dp))
        })
        add(NavEntry("Apps", screen == Screen.APPS, { screen = Screen.APPS }) {
            AppsGridIcon(tint(screen == Screen.APPS), Modifier.size(30.dp))
        })
        add(NavEntry("Teléfono", false, { openDialer(context) }) {
            Icon(Icons.Filled.Phone, null, tint = p.sub, modifier = Modifier.size(32.dp))
        })
        carplay?.let { a -> add(NavEntry(a.label, false, { launch(context, a.packageName) }) { AppIcon(a, 34.dp) }) }
        whatsapp?.let { a -> add(NavEntry("WhatsApp", false, { launch(context, a.packageName) }) { AppIcon(a, 34.dp) }) }
        radio?.let { a ->
            add(NavEntry("Radio", false, { launch(context, a.packageName) }) {
                Icon(Icons.Filled.Radio, null, tint = p.sub, modifier = Modifier.size(32.dp))
            })
        }
        spotify?.let { a -> add(NavEntry("Spotify", false, { launch(context, a.packageName) }) { AppIcon(a, 34.dp) }) }
        add(NavEntry("Ajustes", false, { openSettingsScreen(context, Settings.ACTION_SETTINGS) }) {
            Icon(Icons.Filled.Settings, null, tint = p.sub, modifier = Modifier.size(32.dp))
        })
    }

    fun onShortcut(name: String) {
        val dest = shortcuts[name]
        if (dest.isNullOrEmpty()) editing = name else navigateTo(context, dest)
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Column(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    Screen.HOME -> Row(
                        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // ---------- Columna izquierda ----------
                        Column(Modifier.weight(0.58f).fillMaxHeight()) {
                            HeaderRow(p, CAR_NAME, place) {
                                dark = !dark
                                prefs.edit().putBoolean("dark", dark).apply()
                            }
                            SpeedRow(p, tracker.speedKmh)
                            CarImage(p, Modifier.weight(1f).fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ShortcutChip(
                                    Modifier.weight(1.35f).height(70.dp), p, "Casa", subtitleFor(shortcuts["Casa"]),
                                    { onShortcut("Casa") }, { editing = "Casa" }
                                ) { Text("🏠", fontSize = 26.sp) }
                                ShortcutChip(
                                    Modifier.weight(1.35f).height(70.dp), p, "Trabajo", subtitleFor(shortcuts["Trabajo"]),
                                    { onShortcut("Trabajo") }, { editing = "Trabajo" }
                                ) { Text("💼", fontSize = 26.sp) }
                                ShortcutChip(
                                    Modifier.weight(1f).height(70.dp), p, "Favorito", null,
                                    { onShortcut("Favorito") }, { editing = "Favorito" }
                                ) { Text("⭐", fontSize = 24.sp) }
                                ShortcutChip(
                                    Modifier.weight(1f).height(70.dp), p, "Gym", null,
                                    { onShortcut("Gym") }, { editing = "Gym" }
                                ) { Text("🏋️", fontSize = 24.sp) }
                                ShortcutChip(
                                    Modifier.weight(0.7f).height(70.dp), p, "", null,
                                    { maps?.let { launch(context, it.packageName) } }, {}
                                ) { Icon(Icons.Filled.Search, null, tint = p.text, modifier = Modifier.size(30.dp)) }
                            }
                        }

                        // ---------- Columna derecha ----------
                        Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeatherCard(p, dateLong, weather, hasFix, Modifier.fillMaxWidth().height(92.dp))
                            Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatusTile(
                                    p, "Wi-Fi",
                                    when {
                                        !status.wifiOn -> "Apagado"
                                        status.wifiName.isNotEmpty() -> status.wifiName
                                        else -> "Sin conexión"
                                    },
                                    status.wifiOn, Modifier.weight(1f).fillMaxHeight(),
                                    { openSettingsScreen(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS) }
                                ) { c -> WifiIcon(c, Modifier.fillMaxSize()) }
                                StatusTile(
                                    p, "Bluetooth", if (status.btOn) "Encendido" else "Apagado",
                                    status.btOn, Modifier.weight(1f).fillMaxHeight(),
                                    { openSettingsScreen(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
                                ) { c -> BluetoothIcon(c, Modifier.fillMaxSize()) }
                            }
                            ObdRow(p, Modifier.fillMaxWidth().height(46.dp))
                            MusicCard(
                                music, p, Modifier.weight(1f).fillMaxWidth(),
                                onOpenSpotify = { spotify?.let { launch(context, it.packageName) } },
                                onGrantAccess = {
                                    openSettingsScreen(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                }
                            )
                        }
                    }

                    Screen.MAP -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                        LiveMapPanel(
                            Modifier.fillMaxSize(), p, hasLocation,
                            requestPermission = { permLauncher.launch(perms) },
                            onOpenMaps = { maps?.let { launch(context, it.packageName) } }
                        )
                    }

                    Screen.APPS -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(apps) { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(8.dp).clickable { launch(context, app.packageName) }
                            ) {
                                Image(app.icon, app.label, Modifier.size(64.dp))
                                Text(app.label, color = p.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            BottomBar(p, nav, timeText, dateShort, Modifier.height(84.dp))
        }

        editing?.let { name ->
            ShortcutDialog(
                name = name, current = shortcuts[name] ?: "",
                hasFix = tracker.hasFix, lat = tracker.lat, lon = tracker.lon,
                onSave = { v ->
                    if (v.isEmpty()) {
                        shortcuts.remove(name)
                        shortcutPrefs.edit().remove(name).apply()
                    } else {
                        shortcuts[name] = v
                        shortcutPrefs.edit().putString(name, v).apply()
                    }
                    editing = null
                },
                onDismiss = { editing = null }
            )
        }
    }
}
