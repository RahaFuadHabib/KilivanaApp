// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.farmapp.R
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.theme.*
import kotlinx.coroutines.delay

@Composable fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(1500); onFinished() }
    Surface(Modifier.fillMaxSize(), color = Green600) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Kilivana", style = MaterialTheme.typography.headlineLarge, color = NeutralSurface); Text("Farm produce, delivered.", color = Green50) } }
}

@Composable fun HomeScreen(cartCount: Int, products: List<Product>, categories: List<String>, search: () -> Unit, category: (String) -> Unit, seeAll: () -> Unit, openProduct: (Product) -> Unit, openCart: () -> Unit, add: suspend (Product) -> Unit) {
    var showAllCategories by remember { mutableStateOf(false) }
    val visibleCategories = if (showAllCategories) categories else categories.take(8)
    val sellers = products.mapNotNull { product ->
        product.sellerName?.takeIf(String::isNotBlank)?.let { name ->
            name to product.sellerLocation?.takeIf(String::isNotBlank)
        }
    }.distinct()
    LazyColumn(Modifier.fillMaxSize().background(NeutralBackground), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Row(Modifier.fillMaxWidth().background(Green600).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Kilivana", style = MaterialTheme.typography.titleMedium, color = NeutralSurface); Text("Set delivery location", style = MaterialTheme.typography.labelMedium, color = Green50) }; IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, "Notifications", tint = NeutralSurface) }; IconButton(onClick = openCart) { BadgedBox(badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }) { Icon(Icons.Default.ShoppingCart, "Cart", tint = NeutralSurface) } } } }
        item { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField("", {}, Modifier.weight(1f).clickable(onClick = search), enabled = false, placeholder = { Text("Search produce") }, leadingIcon = { Icon(Icons.Default.Search, "Search") }, shape = RoundedCornerShape(12.dp), singleLine = true); Spacer(Modifier.width(8.dp)); FilledTonalIconButton(onClick = search) { Icon(Icons.Default.Tune, "Filters") } } }
        item { Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))) { Image(painterResource(R.drawable.banner_bulk_orders), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Surface(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), shape = RoundedCornerShape(12.dp), color = NeutralSurface.copy(alpha = 0.94f)) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { Text("Fresh from local farms", style = MaterialTheme.typography.titleLarge, color = TextPrimary); Text("Browse produce from Kilivana sellers", color = TextSecondary); Spacer(Modifier.height(8.dp)); FilledTonalButton(onClick = seeAll) { Text("Shop products") } } } } }
        item { SectionHeader("Shop by category", "") }
        item { Column(Modifier.padding(horizontal = 12.dp)) { visibleCategories.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { name ->
            val icon = when {
                name.contains("vegetable", true) -> Icons.Default.Eco
                name.contains("fruit", true) -> Icons.Default.LunchDining
                name.contains("grain", true) || name.contains("cereal", true) -> Icons.Default.Grass
                name.contains("tuber", true) -> Icons.Default.Kitchen
                name.contains("legume", true) -> Icons.Default.Spa
                name.contains("dairy", true) -> Icons.Default.WaterDrop
                name.contains("egg", true) || name.contains("poultry", true) -> Icons.Default.Egg
                else -> Icons.Default.Category
            }
            Column(Modifier.weight(1f).clickable { category(name) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(Modifier.size(60.dp), shape = RoundedCornerShape(14.dp), color = Green50) { Box(contentAlignment = Alignment.Center) { Icon(icon, name, tint = Green700, modifier = Modifier.size(30.dp)) } }; Text(name, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 2) }
        } }; repeat(4-row.size) { Spacer(Modifier.weight(1f)) } }
            if (categories.size > 8) {
                TextButton(onClick = { showAllCategories = !showAllCategories }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (showAllCategories) "Show fewer categories" else "See all categories")
                }
            }
        } }
        item { SectionHeader("Recommended for you", "See all", seeAll) }
        item { ProductGrid(products.take(4), openProduct, add) }
        item { SectionHeader("Fresh this week", "See all", seeAll) }
        item { ProductGrid(products.drop(4).take(4), openProduct, add) }
        if (sellers.isNotEmpty()) {
            item { SectionHeader("Top sellers", "") }
            item {
                Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    sellers.forEach { (name, location) ->
                        Text(listOfNotNull(name, location).joinToString(" · "), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
