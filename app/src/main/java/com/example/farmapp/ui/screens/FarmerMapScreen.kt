package com.example.farmapp.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.delay

/** Foreground location belongs to this tab; leaving it disposes the map and listener. */
@Composable
internal fun FarmerMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember { context.getSharedPreferences("farmer_map_permissions", Context.MODE_PRIVATE) }
    fun readPermission() = mapPermission(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
    fun servicesEnabled() = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
    var permission by remember { mutableStateOf(readPermission()) }
    var services by remember { mutableStateOf(servicesEnabled()) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var requested by remember { mutableStateOf(preferences.getBoolean("requested", false)) }
    var fix by remember { mutableStateOf<Point?>(null) }
    var pending by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var timedOut by remember { mutableStateOf(false) }
    var requestInFlight by remember { mutableStateOf(false) }
    val viewport = rememberMapViewportState {
        setCameraOptions { center(Point.fromLngLat(37.9, 0.3)); zoom(5.0) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestInFlight = false
        permission = readPermission()
        services = servicesEnabled()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = readPermission()
                services = servicesEnabled()
                resumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
                fix = null
                pending = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val locating = resumed && permission != MapPermission.DENIED && services
    LaunchedEffect(locating, retry) {
        timedOut = false
        if (locating) { delay(15_000); if (fix == null) { timedOut = true; pending = false } }
    }
    LaunchedEffect(fix, pending) {
        val point = fix
        if (pending && point != null) {
            viewport.setCameraOptions(CameraOptions.Builder().center(point)
                .zoom(if (permission == MapPermission.APPROXIMATE) 12.0 else 15.0).build())
            pending = false
        }
    }
    val activity = context.findMapActivity()
    val rationale = activity != null && (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION) ||
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION))
    val settingsRequired = requested && !rationale && permission == MapPermission.DENIED
    fun openSettings(action: String) {
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) data = Uri.parse("package:${context.packageName}")
        }
        // Settings can be absent on unusual devices; keep the map usable.
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }
    Column(modifier) {
        // Controls occupy their own area, leaving the SDK logo and attribution unobstructed.
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(when {
                    permission == MapPermission.DENIED && settingsRequired -> "Location is denied. Enable it in app settings, or explore the map."
                    permission == MapPermission.DENIED && rationale -> "Location helps show where you are. Approximate location is enough; you can keep using the map without it."
                    permission == MapPermission.DENIED -> "Explore Kenya. Allow location to show your position."
                    !services -> "Device location is off. Enable it to find your position."
                    timedOut -> "No location fix yet. Check device location or move outdoors, then retry."
                    fix == null -> "Waiting for your location. You can still pan and zoom."
                    permission == MapPermission.APPROXIMATE -> "Approximate location available."
                    else -> "Current location available."
                }, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(enabled = !requestInFlight, onClick = {
                        permission = readPermission()
                        services = servicesEnabled()
                        if (!services || permission == MapPermission.DENIED) fix = null
                        when (recenterAction(permission, services, fix != null)) {
                            RecenterAction.REQUEST_PERMISSION -> if (settingsRequired) openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) else {
                                preferences.edit().putBoolean("requested", true).apply()
                                requested = true
                                requestInFlight = true
                                launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                            RecenterAction.OPEN_LOCATION_SETTINGS -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            RecenterAction.WAIT_FOR_FIX -> { pending = true; timedOut = false; retry++ }
                            RecenterAction.CENTER -> { pending = true }
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (permission == MapPermission.DENIED) {
                            if (settingsRequired) "App settings" else if (rationale) "Retry location" else "Allow location"
                        } else if (!services) "Enable location" else "Current location")
                    }
                    if (timedOut && services) TextButton(onClick = { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }) { Text("Settings") }
                }
            }
        }
        MapboxMap(Modifier.fillMaxWidth().weight(1f), mapViewportState = viewport) {
            DisposableMapEffect(locating) { mapView ->
                val listener = OnIndicatorPositionChangedListener { point ->
                    fix = point
                    timedOut = false
                }
                try {
                    mapView.location.updateSettings {
                        locationPuck = createDefault2DPuck()
                        enabled = locating
                    }
                    if (locating) mapView.location.addOnIndicatorPositionChangedListener(listener)
                } catch (_: SecurityException) {
                    permission = readPermission()
                    fix = null
                }
                onDispose {
                    mapView.location.removeOnIndicatorPositionChangedListener(listener)
                    mapView.location.enabled = false
                }
            }
        }
    }
}

private fun Context.findMapActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findMapActivity()
    else -> null
}
