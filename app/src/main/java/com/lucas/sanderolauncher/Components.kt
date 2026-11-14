package com.lucas.sanderolauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------
//  Paleta (oscuro por defecto, como el diseño de referencia)
// ---------------------------------------------------------------
data class Palette(
    val bg: Color, val bar: Color, val card: Color, val stroke: Color,
    val text: Color, val sub: Color, val accent: Color, val dark: Boolean
)

val DarkPalette = Palette(
    bg = Color(0xFF07080C), bar = Color(0xFF0B0D13), card = Color(0xFF12151D),
    stroke = Color(0xFF212736), text = Color.White, sub = Color(0xFF9AA3B5),
    accent = Color(0xFF2F6BFF), dark = true
)
val LightPalette = Palette(
    bg = Color(0xFFEDF0F6), bar = Color.White, card = Color.White,
    stroke = Color(0xFFD5DBE7), text = Color(0xFF12161F), sub = Color(0xFF5B6475),
    accent = Color(0xFF2F6BFF), dark = false
)

fun Modifier.card(p: Palette, radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(p.card, shape).border(1.dp, p.stroke, shape)
}

fun isCoords(s: String): Boolean =
    Regex("^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$").matches(s.trim())

// ---------------------------------------------------------------
//  Cabecera, velocidad y auto
// ---------------------------------------------------------------
@Composable
fun HeaderRow(p: Palette, carName: String, place: String?, onTheme: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).background(Color(0xFFE53935)))
        Spacer(Modifier.width(10.dp))
        Text(carName, color = p.text, fontSize = 15.sp, letterSpacing = 3.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onTheme), contentAlignment = Alignment.Center) {
            Text("◐", color = p.text, fontSize = 28.sp)
        }
        if (!place.isNullOrEmpty()) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
            Spacer(Modifier.width(6.dp))
            Text(place.uppercase(), color = Color(0xFF22C55E), fontSize = 14.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SpeedRow(p: Palette, speed: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(speed.toString(), color = p.text, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text("  km/h", color = p.sub, fontSize = 20.sp, modifier = Modifier.padding(bottom = 14.dp))
    }
}

@Composable
fun CarImage(p: Palette, modifier: Modifier) {
    val context = LocalContext.current
    val carId = remember {
        listOf("car", "car_off")
            .map { context.resources.getIdentifier(it, "drawable", context.packageName) }
            .firstOrNull { it != 0 } ?: 0
    }
    Box(
        modifier.background(
            Brush.radialGradient(listOf(p.accent.copy(alpha = if (p.dark) 0.12f else 0.10f), Color.Transparent))
        ),
        contentAlignment = Alignment.Center
    ) {
        if (carId != 0) {
            Image(painterResource(carId), "Mi Sandero", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        } else {
            Text("Agrega car.png", color = p.sub)
        }
    }
}

// ---------------------------------------------------------------
//  Lado derecho: clima, Wi-Fi, Bluetooth, datos del auto
// ---------------------------------------------------------------
@Composable
fun WeatherCard(p: Palette, dateText: String, weather: WeatherInfo?, hasFix: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val brush = if (p.dark) Brush.verticalGradient(listOf(Color(0xFF2B3444), Color(0xFF151A25)))
    else Brush.verticalGradient(listOf(Color(0xFFCFE0FA), Color(0xFFEAF1FD)))
    Column(
        modifier.clip(shape).background(brush).border(1.dp, p.stroke, shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(dateText, color = p.text.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weather?.icon ?: "🌡️", fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Text(weather?.let { "${it.temp}°" } ?: "--°", color = p.text, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(14.dp))
            Text(
                weather?.desc ?: (if (hasFix) "Cargando…" else "Esperando ubicación"),
                color = p.text, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WifiIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val cx = w / 2f
        val cy = size.height * 0.85f
        listOf(0.50f, 0.34f, 0.18f).forEach { r ->
            val rad = w * r * 1.6f
            drawArc(
                color, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - rad, cy - rad), size = Size(rad * 2, rad * 2),
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
            )
        }
        drawCircle(color, w * 0.07f, Offset(cx, cy))
    }
}

@Composable
fun BluetoothIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.28f); lineTo(w * 0.75f, h * 0.72f); lineTo(w * 0.5f, h * 0.92f)
            lineTo(w * 0.5f, h * 0.08f); lineTo(w * 0.75f, h * 0.28f); lineTo(w * 0.25f, h * 0.72f)
        }
        drawPath(path, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun StatusTile(
    p: Palette, title: String, subtitle: String, active: Boolean,
    modifier: Modifier, onClick: () -> Unit, icon: @Composable (Color) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val fg = if (active) Color.White else p.text
    Row(
        modifier.clip(shape)
            .background(if (active) p.accent else p.card, shape)
            .border(1.dp, if (active) p.accent else p.stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp)) { icon(fg) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = fg.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Datos del motor: necesitan un adaptador OBD2 (pendiente)
@Composable
fun ObdRow(p: Palette, modifier: Modifier) {
    Row(
        modifier.card(p, 22.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("🌡️" to "--°C", "🔋" to "-- V", "⛽" to "-- %").forEach { (e, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(v, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------
//  Música
// ---------------------------------------------------------------
@Composable
fun TransportIcon(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (kind) {
            "play" -> drawPath(Path().apply {
                moveTo(w * 0.32f, h * 0.2f); lineTo(w * 0.82f, h * 0.5f); lineTo(w * 0.32f, h * 0.8f); close()
            }, color)
            "pause" -> {
                drawRect(color, Offset(w * 0.27f, h * 0.2f), Size(w * 0.17f, h * 0.6f))
                drawRect(color, Offset(w * 0.56f, h * 0.2f), Size(w * 0.17f, h * 0.6f))
            }
            "next" -> {
                drawPath(Path().apply {
                    moveTo(w * 0.2f, h * 0.2f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.2f, h * 0.8f); close()
                }, color)
                drawRect(color, Offset(w * 0.74f, h * 0.2f), Size(w * 0.09f, h * 0.6f))
            }
            "prev" -> {
                drawPath(Path().apply {
                    moveTo(w * 0.8f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.8f, h * 0.8f); close()
                }, color)
                drawRect(color, Offset(w * 0.17f, h * 0.2f), Size(w * 0.09f, h * 0.6f))
            }
        }
    }
}

@Composable
fun MusicCard(
    music: MusicController, p: Palette, modifier: Modifier,
    onOpenSpotify: () -> Unit, onGrantAccess: () -> Unit
) {
    LaunchedEffect(music.playing, music.hasSession) {
        while (true) { music.refreshPosition(); delay(1000) }
    }
    Column(modifier.card(p).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        if (!music.hasAccess) {
            Text(
                "Para ver y controlar Spotify, activa el acceso a notificaciones para este launcher.",
                color = p.text, fontSize = 16.sp
            )
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(p.accent)
                    .clickable(onClick = onGrantAccess)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) { Text("Activar acceso", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1DB954)).clickable(onClick = onOpenSpotify),
                    contentAlignment = Alignment.Center
                ) {
                    val a = music.art
                    if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("♪", color = Color.Black, fontSize = 36.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (music.title.isNotEmpty()) music.title else "Spotify",
                        color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (music.hasSession && music.artist.isNotEmpty()) music.artist else "Reproduce una canción",
                        color = p.sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val frac = if (music.durationMs > 0) music.positionMs.toFloat() / music.durationMs else 0f
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = p.text, trackColor = p.sub.copy(alpha = 0.3f)
            )
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).clickable { music.previous() }, contentAlignment = Alignment.Center) {
                    TransportIcon("prev", p.text, Modifier.size(34.dp))
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(p.text).clickable { music.playPause() },
                    contentAlignment = Alignment.Center
                ) { TransportIcon(if (music.playing) "pause" else "play", p.bg, Modifier.size(34.dp)) }
                Box(Modifier.size(52.dp).clip(CircleShape).clickable { music.next() }, contentAlignment = Alignment.Center) {
                    TransportIcon("next", p.text, Modifier.size(34.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------
//  Atajos (Casa, Trabajo, Favorito, Gym)
// ---------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutChip(
    modifier: Modifier, p: Palette, title: String, subtitle: String?,
    onClick: () -> Unit, onLongClick: () -> Unit, icon: @Composable () -> Unit
) {
    val base = modifier.card(p, 20.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    if (subtitle != null) {
        Row(base.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = p.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            if (title.isNotEmpty()) Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun ShortcutDialog(
    name: String, current: String, hasFix: Boolean, lat: Double, lon: Double,
    onSave: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(if (isCoords(current)) "" else current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                Text("Escribe una dirección o usa tu ubicación actual. Mantén pulsado un atajo para editarlo.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("Dirección") }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (hasFix) TextButton(onClick = { onSave("$lat,$lon") }) { Text("Usar mi ubicación") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

// ---------------------------------------------------------------
//  Barra inferior
// ---------------------------------------------------------------
class NavEntry(
    val label: String, val selected: Boolean, val onClick: () -> Unit,
    val icon: @Composable () -> Unit
)

@Composable
fun AppsGridIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val step = w / 4f
        for (r in 1..3) for (c in 1..3) {
            drawCircle(color, w * 0.07f, Offset(step * c, step * r))
        }
    }
}

@Composable
fun BottomBar(p: Palette, items: List<NavEntry>, time: String, date: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(p.bar).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(onClick = item.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { item.icon() }
                Text(
                    item.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (item.selected) p.text else p.sub
                )
            }
        }
        Column(Modifier.padding(horizontal = 14.dp), horizontalAlignment = Alignment.End) {
            Text(time, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(date, color = p.sub, fontSize = 14.sp, maxLines = 1)
        }
    }
}
