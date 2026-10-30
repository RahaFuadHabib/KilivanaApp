// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.model.Product
import com.example.farmapp.buyers.ui.theme.*

@Composable
fun SavedItemsScreen(
    products: List<Product>,
    open: (Product) -> Unit,
    add: suspend (Product) -> Unit,
    back: () -> Unit,
    shop: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(NeutralBackground)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Saved items", style = MaterialTheme.typography.headlineSmall)
        }
        if (products.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(72.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = Green50) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FavoriteBorder, null, tint = Green600, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Save produce for later", style = MaterialTheme.typography.titleLarge)
                    Text("Tap the heart on an item to keep it here.", color = TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = shop) { Text("Browse produce") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
                item { ProductGrid(products, open, add) }
            }
        }
    }
}
