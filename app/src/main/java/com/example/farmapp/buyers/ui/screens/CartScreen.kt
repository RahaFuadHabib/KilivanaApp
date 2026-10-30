// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.data.CartViewModel
import com.example.farmapp.buyers.model.CartItem
import com.example.farmapp.buyers.model.formatKes
import com.example.farmapp.buyers.ui.theme.*

@Composable
fun CartScreen(items: List<CartItem>, vm: CartViewModel, checkout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NeutralBackground).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your cart", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            if (items.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(50), color = Green50) {
                    Text("${items.sumOf { it.quantity }} items", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Green700, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(72.dp), shape = RoundedCornerShape(24.dp), color = Green50) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ShoppingBasket, contentDescription = null, tint = Green600, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Your cart is waiting", style = MaterialTheme.typography.titleLarge)
                    Text("Add fresh produce and it will show up here.", color = TextSecondary)
                    Button(onClick = checkout) { Text("Start shopping") }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp)
            ) {
                items(items, key = { it.product.id }) { line ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Green100),
                        colors = CardDefaults.cardColors(containerColor = NeutralSurface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    ProductImage(Modifier.size(112.dp), line.product.name, line.product.imageUrl)
                                    Surface(
                                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                                        shape = RoundedCornerShape(50),
                                        color = NeutralSurface.copy(alpha = .94f)
                                    ) {
                                        Text(line.product.category, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Green700, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(line.product.name, style = MaterialTheme.typography.titleLarge)
                                    line.product.sellerName?.let { Text("From $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                                    Spacer(Modifier.height(10.dp))
                                    Text(formatKes(line.product.pricePerUnit), style = MaterialTheme.typography.titleMedium, color = Green700)
                                    Text("per ${line.product.unit}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = NeutralDivider)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedIconButton(
                                        onClick = { vm.decrement(line.product.id) },
                                        modifier = Modifier.size(42.dp),
                                        shape = RoundedCornerShape(13.dp),
                                        enabled = line.quantity.toBigDecimal() > line.product.minimumOrder
                                    ) { Icon(Icons.Default.Remove, contentDescription = "Decrease quantity") }
                                    Text(line.quantity.toString(), style = MaterialTheme.typography.titleMedium)
                                    OutlinedIconButton(
                                        onClick = { vm.increment(line.product.id) },
                                        modifier = Modifier.size(42.dp),
                                        shape = RoundedCornerShape(13.dp)
                                    ) { Icon(Icons.Default.Add, contentDescription = "Increase quantity") }
                                }
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Line total", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(formatKes(line.product.pricePerUnit.multiply(line.quantity.toBigDecimal())), style = MaterialTheme.typography.titleMedium, color = Green700)
                                }
                            }
                            TextButton(
                                onClick = { vm.remove(line.product.id) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                Text("Remove item")
                            }
                        }
                    }
                }
                if (items.size == 1) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Green50
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(12.dp), color = NeutralSurface, modifier = Modifier.size(42.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.ShoppingBasket, null, tint = Green600)
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text("A great start", style = MaterialTheme.typography.titleSmall, color = Green700)
                                    Text("Add more local favourites whenever you're ready.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Icon(Icons.Default.ArrowForward, null, tint = Green600, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = NeutralSurface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Items subtotal", color = TextSecondary)
                        Text(formatKes(vm.subtotal))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivery", color = TextSecondary)
                        Text("Shown at checkout", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = NeutralDivider)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total before delivery", style = MaterialTheme.typography.titleMedium)
                        Text(formatKes(vm.subtotal), style = MaterialTheme.typography.titleMedium, color = Green700)
                    }
                }
            }
            Button(onClick = checkout, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = items.isNotEmpty()) {
                Text("Proceed to checkout")
            }
        }
    }
}

