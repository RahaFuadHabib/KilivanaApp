package com.example.farmapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.farmapp.network.canonical.CanonicalFarmRequest
import com.example.farmapp.repository.FarmerFarm
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber
import com.example.farmapp.ui.theme.LeafGreen

private val FarmGreen = Color(0xFF00834A)
private val FarmGreenLight = Color(0xFFE8F5EE)
private val FarmAmber = Color(0xFFFFF8E1)
private val FarmAmberText = Color(0xFFE65100)
private val FarmRed = Color(0xFFFFEBEE)
private val FarmRedText = Color(0xFFB71C1C)
private val FarmGrey = Color(0xFFF5F5F5)
private val FarmGreyText = Color(0xFF555555)
private val FarmInk = Color(0xFF101827)
private val FarmMuted = Color(0xFF526074)
private val FarmPage = Color(0xFFF0F3EE)
private val FarmSurface = Color(0xFFFFFFFF)
private val FarmBorder = Color(0xFFDCE3E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmerFarmsScreen(
    farms: List<FarmerFarm>, loading: Boolean, busy: Boolean, error: String?,
    message: String?, onRefresh: () -> Unit, onCreate: (CanonicalFarmRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var county by rememberSaveable { mutableStateOf("") }
    var subCounty by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var size by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var ownership by rememberSaveable { mutableStateOf("OWNED") }
    var ownershipMenu by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun useLocation() {
        if (!hasLocationPermission()) return
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            validation = "Turn on device location or enter the farm coordinates manually."
            locating = false; return
        }
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            validation = "No location provider is available. Enter the farm coordinates manually."
            locating = false; return
        }
        LocationManagerCompat.getCurrentLocation(manager, provider, CancellationSignal(), ContextCompat.getMainExecutor(context)) { location ->
            locating = false
            if (location == null || !location.latitude.isFinite() || !location.longitude.isFinite()) {
                validation = "Could not get a location fix. Enter the farm coordinates manually."
            } else {
                latitude = location.latitude.toString()
                longitude = location.longitude.toString()
                validation = null
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) useLocation()
        else { locating = false; validation = "Allow location access or enter the farm coordinates manually." }
    }

    Column(modifier.fillMaxSize().background(FarmPage)) {
        // Header
        Box(Modifier.fillMaxWidth().background(ForestGreen)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Agriculture, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("My Farms", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Registered farm locations", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = onRefresh, enabled = !loading && !busy) {
                    Icon(Icons.Default.Refresh, "Refresh farms", tint = Color.White)
                }
            }
        }

        if (loading && farms.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = LeafGreen)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status messages
            if (message != null) item {
                Surface(shape = RoundedCornerShape(12.dp), color = FarmGreenLight, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = FarmGreen, modifier = Modifier.size(20.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = FarmGreen)
                    }
                }
            }
            if (error != null) item {
                Surface(shape = RoundedCornerShape(12.dp), color = FarmRed, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = FarmRedText, modifier = Modifier.size(20.dp))
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = FarmRedText)
                    }
                }
            }

            // Register button
            item {
                Button(
                    onClick = { showForm = !showForm },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (showForm) FarmMuted else FarmGreen)
                ) {
                    Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (showForm) "Close form" else "Register a new farm", fontWeight = FontWeight.SemiBold)
                }
            }

            // Registration form
            if (showForm) item {
                Surface(shape = RoundedCornerShape(16.dp), color = FarmSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(FarmGreenLight), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Agriculture, null, tint = FarmGreen, modifier = Modifier.size(20.dp))
                            }
                            Text("Farm details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FarmInk)
                        }
                        OutlinedTextField(name, { name = it; validation = null }, label = { Text("Farm name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(county, { county = it; validation = null }, label = { Text("County") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                            OutlinedTextField(subCounty, { subCounty = it }, label = { Text("Sub-county") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                        }
                        OutlinedTextField(address, { address = it; validation = null }, label = { Text("Physical address") }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        HorizontalDivider(color = FarmBorder)
                        Text("Coordinates", style = MaterialTheme.typography.labelMedium, color = FarmMuted)
                        OutlinedButton(
                            onClick = {
                                locating = true
                                if (hasLocationPermission()) useLocation()
                                else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                            },
                            enabled = !locating,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (locating) "Getting location…" else "Use my current location")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(latitude, { latitude = it; validation = null }, label = { Text("Latitude") }, singleLine = true,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                            OutlinedTextField(longitude, { longitude = it; validation = null }, label = { Text("Longitude") }, singleLine = true,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                        }
                        HorizontalDivider(color = FarmBorder)
                        OutlinedTextField(size, { size = it; validation = null }, label = { Text("Farm size (acres)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                        ExposedDropdownMenuBox(expanded = ownershipMenu, onExpandedChange = { ownershipMenu = it }) {
                            OutlinedTextField(
                                value = ownership.lowercase().replaceFirstChar { it.uppercase() },
                                onValueChange = {}, readOnly = true,
                                label = { Text("Land ownership") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ownershipMenu) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = ownershipMenu, onDismissRequest = { ownershipMenu = false }) {
                                listOf("OWNED", "LEASED", "COMMUNAL").forEach { option ->
                                    DropdownMenuItem(text = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = { ownership = option; ownershipMenu = false })
                                }
                            }
                        }
                        OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, minLines = 2,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        if (validation != null) Surface(shape = RoundedCornerShape(10.dp), color = FarmRed, modifier = Modifier.fillMaxWidth()) {
                            Text(validation!!, Modifier.padding(12.dp), color = FarmRedText, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                val acreage = size.trim().toDoubleOrNull()
                                val lat = latitude.trim().toDoubleOrNull()
                                val lon = longitude.trim().toDoubleOrNull()
                                validation = when {
                                    name.isBlank() -> "Enter the farm name."
                                    county.isBlank() -> "Enter the county."
                                    address.isBlank() -> "Enter the physical farm address."
                                    lat == null || lon == null || !lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0 -> "Enter valid farm coordinates or use your current location."
                                    acreage == null || !acreage.isFinite() || acreage <= 0 -> "Enter a farm size greater than zero."
                                    else -> null
                                }
                                if (validation == null) {
                                    showForm = false
                                    onCreate(CanonicalFarmRequest(name.trim(), county.trim(), subCounty.trim().ifBlank { null },
                                        address.trim(), latitude = lat, longitude = lon, sizeAcres = acreage!!,
                                        ownershipType = ownership, description = description.trim().ifBlank { null }))
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FarmGreen)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else { Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Submit farm for review", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }

            // Empty state
            if (!loading && farms.isEmpty() && error == null) item {
                Surface(shape = RoundedCornerShape(20.dp), color = FarmSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(FarmGreenLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Agriculture, null, tint = FarmGreen, modifier = Modifier.size(36.dp))
                        }
                        Text("No farms registered yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = FarmInk)
                        Text("Register your farm so Admin can review it and arrange an inspector visit.", style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
                    }
                }
            }

            // Farm cards
            items(farms, key = { it.id }) { farm ->
                FarmCard(farm)
            }
        }
    }
}

@Composable
private fun FarmCard(farm: FarmerFarm) {
    val (statusBg, statusFg, statusIcon) = when (farm.status) {
        "ACTIVE" -> Triple(FarmGreenLight, FarmGreen, Icons.Default.CheckCircle)
        "PENDING" -> Triple(FarmAmber, FarmAmberText, Icons.Default.HourglassEmpty)
        "FALLOW" -> Triple(FarmGrey, FarmGreyText, Icons.Default.Pause)
        else -> Triple(FarmRed, FarmRedText, Icons.Default.Block)
    }
    Surface(shape = RoundedCornerShape(16.dp), color = FarmSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(FarmGreenLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Agriculture, null, tint = FarmGreen, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(farm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FarmInk)
                    Text("${farm.county}${farm.subCounty?.let { ", $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(statusIcon, null, tint = statusFg, modifier = Modifier.size(13.dp))
                        Text(farm.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall, color = statusFg, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            HorizontalDivider(color = FarmBorder)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FarmDetail(Icons.Default.Straighten, "${farm.sizeAcres} acres")
                FarmDetail(Icons.Default.LocationOn, farm.address?.take(40) ?: "No address")
            }
            if (farm.latitude != null && farm.longitude != null) {
                FarmDetail(Icons.Default.MyLocation, "%.5f, %.5f".format(farm.latitude, farm.longitude))
            }
            Surface(shape = RoundedCornerShape(10.dp), color = statusBg, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (farm.status) {
                        "PENDING" -> "Waiting for Admin review and farm inspection assignment."
                        "ACTIVE" -> if (farm.canDispatchFrom) "Approved — you can submit produce linked to this farm."
                            else "Approved, but add a valid address and coordinates before dispatching produce."
                        "FALLOW" -> "This farm is not currently available as a produce dispatch location."
                        else -> "This farm is not active for produce dispatch. Contact Admin if this is incorrect."
                    },
                    Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = statusFg
                )
            }
        }
    }
}

@Composable
private fun FarmDetail(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = FarmMuted, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
    }
}
