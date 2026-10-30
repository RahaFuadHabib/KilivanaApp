package com.example.farmapp.supplier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.canonical.CanonicalProductStatus
import com.example.farmapp.ui.screens.ProductImage

/** Supplier-only presentation. No shared role palette or domain status is changed. */
internal object SupplierStyle {
    val forest = Color(0xFF0D4A2D)
    val leaf = Color(0xFF2E9B42)
    val cream = Color(0xFFFAF6EF)
    val card = RoundedCornerShape(24.dp)
}

@Composable
internal fun SupplierTheme(dark: Boolean, content: @Composable () -> Unit) {
    val inherited = MaterialTheme.colorScheme
    val colors = if (dark) inherited.copy(
        primary = Color(0xFF9BD8AB), onPrimary = Color(0xFF082D1B),
        primaryContainer = Color(0xFF173E2C), onPrimaryContainer = Color(0xFFD2EED8),
        background = Color(0xFF101B15), surface = Color(0xFF19271F),
        surfaceVariant = Color(0xFF25372D), onSurface = Color(0xFFEAF1E9),
        onBackground = Color(0xFFEAF1E9), onSurfaceVariant = Color(0xFFBDCDBF),
        outline = Color(0xFF6E8374), secondaryContainer = Color(0xFF503C13),
        onSecondaryContainer = Color(0xFFFFE2A1), surfaceContainer = Color(0xFF19271F),
        surfaceContainerHigh = Color(0xFF203227), surfaceContainerHighest = Color(0xFF25372D)
    ) else inherited.copy(
        primary = SupplierStyle.forest, onPrimary = Color.White,
        primaryContainer = Color(0xFFE3EEE3), onPrimaryContainer = Color(0xFF163D25),
        background = SupplierStyle.cream, surface = Color.White,
        surfaceVariant = Color(0xFFEDF2E9), onSurfaceVariant = Color(0xFF4C6052),
        outline = Color(0xFF78867A), secondaryContainer = Color(0xFFFFE8B7),
        onSecondaryContainer = Color(0xFF593C00), surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFF4F6EF), surfaceContainerHighest = Color(0xFFEDF2E9)
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes.copy(large = SupplierStyle.card, extraLarge = SupplierStyle.card), content = content)
}

/** Original procedural gradient fallback, deliberately not a photographic product representation. */
@Composable
internal fun SupplierHero(name: String, enabled: Boolean, add: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(SupplierStyle.card).background(
        Brush.linearGradient(listOf(Color(0xFF082E20), SupplierStyle.forest, Color(0xFF245A37))))) {
        Canvas(Modifier.matchParentSize()) {
            repeat(7) { index ->
                val y = size.height * (0.24f + index * 0.13f)
                val path = Path().apply {
                    moveTo(size.width * 0.58f, size.height)
                    cubicTo(size.width * 0.94f, y, size.width * 0.70f, y, size.width * 1.2f, y - size.height * 0.20f)
                    lineTo(size.width * 1.2f, y - size.height * 0.12f)
                    cubicTo(size.width * 0.77f, y + 14f, size.width * 1.02f, y + 12f, size.width * 0.70f, size.height)
                    close()
                }
                drawPath(path, Color(0xFF85B96A).copy(alpha = 0.10f))
            }
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Eco, null, tint = Color(0xFFBBDDB4))
                Text("KILIVANA • SUPPLIER", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD3E6CE))
            }
            Text("Welcome, ${name.ifBlank { "Supplier" }}", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text("Grow Your\nAgribusiness", color = Color.White, style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("Quality inputs. Stronger farms. Manage your supplies in one place.",
                color = Color(0xFFE1EBDD), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = add, enabled = enabled, colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE4F1DC), contentColor = Color(0xFF123D24),
                disabledContainerColor = Color(0xFF344E3E), disabledContentColor = Color(0xFFCFDACE))) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add supply")
            }
        }
    }
}

@Composable
internal fun SupplierStatus(status: CanonicalProductStatus) {
    val pending = status == CanonicalProductStatus.PENDING_APPROVAL
    val active = status == CanonicalProductStatus.ACTIVE
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(50),
        color = if (pending) colors.secondaryContainer else if (active) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (pending) colors.onSecondaryContainer else if (active) colors.onPrimaryContainer else colors.onSurfaceVariant) {
        Text(status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

internal fun supplierCategoryIcon(name: String): ImageVector = when {
    name.contains("seed", true) || name.contains("nursery", true) -> Icons.Default.Grass
    name.contains("fertili", true) || name.contains("soil", true) -> Icons.Default.Eco
    name.contains("protect", true) -> Icons.Default.Shield
    name.contains("irrig", true) -> Icons.Default.WaterDrop
    name.contains("livestock", true) || name.contains("poultry", true) -> Icons.Default.Pets
    name.contains("greenhouse", true) -> Icons.Default.Yard
    name.contains("tool", true) || name.contains("machin", true) -> Icons.Default.Handyman
    else -> Icons.Default.Inventory2
}

@Composable
internal fun SupplierPhotoPlaceholder(modifier: Modifier = Modifier, label: String = "No product photo") {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Inventory2, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SupplierListingPhoto(listing: SupplyListing, modifier: Modifier) {
    val image = listing.images.firstOrNull { it.isPrimary == true } ?: listing.images.firstOrNull()
    if (image?.url.isNullOrBlank()) SupplierPhotoPlaceholder(modifier)
    else ProductImage(image!!.url!!, listing.name, modifier) { SupplierPhotoPlaceholder(modifier, "Photo unavailable") }
}

@Composable
internal fun SupplierGallery(listing: SupplyListing) {
    val photos = remember(listing.images) { listing.images.sortedBy { it.sortOrder ?: Int.MAX_VALUE } }
    val memory = com.example.farmapp.ui.screens.rememberGalleryImageMemory(listing.id to listing.images)
    var selectedId by rememberSaveable(listing.id) { mutableStateOf<Long?>(null) }
    val selected = photos.firstOrNull { it.id == selectedId } ?: photos.firstOrNull { it.isPrimary == true } ?: photos.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val hero = Modifier.fillMaxWidth().height(240.dp).clip(SupplierStyle.card)
        if (selected?.url.isNullOrBlank()) SupplierPhotoPlaceholder(hero)
        else ProductImage(selected!!.url!!, listing.name, hero, memory = memory) { SupplierPhotoPlaceholder(hero, "Photo unavailable") }
        if (photos.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(photos, key = { _, image -> image.id!! }) { index, image ->
                val chosen = image.id == selected?.id
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                    .border(if (chosen) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { selectedId = image.id }
                    .semantics { contentDescription = "View ${listing.name} photo ${index + 1}"; this.selected = chosen }) {
                    ProductImage(image.url.orEmpty(), listing.name, Modifier.fillMaxSize(), showRetry = false, memory = memory) {
                        SupplierPhotoPlaceholder(Modifier.fillMaxSize(), "Unavailable")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SupplierSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = SupplierStyle.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
