package com.lucas.sanderolauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

// Apps favoritas del dock (en este orden)
private val FAVORITES = listOf(
    "com.google.android.apps.maps",
    "com.spotify.music",
    "com.google.android.youtube",
    "com.netflix.mediaclient",
    "com.instagram.android",
    "com.zhiliaoapp.musically" // TikTok
)

data class AppItem(val label: String, val packageName: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { LauncherScreen() }
        }
    }
}

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

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { apps = loadApps(context) }
    BackHandler(enabled = showAll) { showAll = false }

    val bg = if (dark)
        Brush.linearGradient(listOf(Color(0xFF0B0F3B), Color(0xFF3A2A8F)))
    else
        Brush.linearGradient(listOf(Color(0xFFE6D9FF), Color(0xFF8FB4FF)))
    val glass = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val textColor = if (dark) Color.White else Color(0xFF14143A)

    Box(Modifier.fillMaxSize().background(bg).safeDrawingPadding()) {
        if (showAll) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable { launch(context, app.packageName) }
                    ) {
                        Image(app.icon, app.label, Modifier.size(64.dp))
                        Text(app.label, color = textColor, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            // Zona central: aquí irán las tarjetas (mapa, música, clima)
            Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
                Text("Mi Sandero", color = textColor, fontSize = 40.sp)
            }
        }

        // Dock inferior
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(glass)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de todas las apps
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                    .background(glass).clickable { showAll = !showAll },
                contentAlignment = Alignment.Center
            ) { Text(if (showAll) "✕" else "⋮⋮⋮", color = textColor, fontSize = 24.sp) }

            FAVORITES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
                .forEach { app ->
                    Image(
                        app.icon, app.label,
                        Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                            .clickable { launch(context, app.packageName) }
                    )
                }
        }
    }
}
