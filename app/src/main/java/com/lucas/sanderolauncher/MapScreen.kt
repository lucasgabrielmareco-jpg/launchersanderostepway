package com.lucas.sanderolauncher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Mapa en vivo (OpenStreetMap) con tu ubicación
@Composable
fun LiveMapPanel(
    modifier: Modifier, p: Palette, hasLocation: Boolean,
    requestPermission: () -> Unit, onOpenMaps: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var hasFix by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.5)
        }
    }
    val locOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).also {
            mapView.overlays.add(it)
            mapView.overlays.add(CopyrightOverlay(context))
        }
    }

    DisposableEffect(hasLocation) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (hasLocation) {
                        locOverlay.enableMyLocation()
                        locOverlay.enableFollowLocation()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locOverlay.disableMyLocation()
                    mapView.onPause()
                }
                else -> {}
            }
        }
        val lifecycle = activity.lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            locOverlay.disableMyLocation()
        }
    }
    DisposableEffect(Unit) { onDispose { mapView.onDetach() } }
    LaunchedEffect(Unit) {
        while (true) { hasFix = locOverlay.myLocation != null; delay(1000) }
    }

    Box(modifier.card(p, 28.dp)) {
        AndroidView(
            factory = { mapView },
            update = {
                it.overlayManager.tilesOverlay.setColorFilter(
                    if (p.dark) TilesOverlay.INVERT_COLORS else null
                )
                it.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
        if (!hasFix) {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { if (!hasLocation) requestPermission() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    if (hasLocation) "Buscando ubicación…" else "Toca para dar permiso de ubicación",
                    color = Color.White, fontSize = 15.sp
                )
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                .clickable { locOverlay.enableFollowLocation() },
            contentAlignment = Alignment.Center
        ) { Text("◎", color = Color(0xFF14143A), fontSize = 24.sp) }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(12.dp)
                .clip(RoundedCornerShape(16.dp)).background(p.accent)
                .clickable(onClick = onOpenMaps)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Abrir Google Maps  ↗", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
