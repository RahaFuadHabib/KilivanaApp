package com.example.farmapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmapp.data.model.Order
import com.example.farmapp.data.model.Product
import com.example.farmapp.data.model.mockOrders
import com.example.farmapp.data.model.mockProducts

enum class PriceSort {
    NONE,
    LOW_TO_HIGH,
    HIGH_TO_LOW
}

@Composable
fun BuyerScreen(
    modifier: Modifier = Modifier,
    products: List<Product> = mockProducts
) {
    var selectedScreenTab by remember { mutableIntStateOf(0) }
    val orders = remember { mutableStateListOf<Order>().apply { addAll(mockOrders.take(3)) } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var priceSort by remember { mutableStateOf(PriceSort.NONE) }

    // BottomSheet & Dialog states
    var checkoutProduct by remember { mutableStateOf<Product?>(null) }
    var activeTrackingOrder by remember { mutableStateOf<Order?>(null) }

    val categories = remember(products) {
        listOf("All") + products.map { it.category }.distinct()
    }

    val filteredProducts = remember(searchQuery, selectedCategory, priceSort, products) {
        val list = products.filter { product ->
            val matchesQuery = product.name.contains(searchQuery, ignoreCase = true) ||
                    product.county.contains(searchQuery, ignoreCase = true) ||
                    product.farmerName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || product.category.equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }

        when (priceSort) {
            PriceSort.LOW_TO_HIGH -> list.sortedBy { it.pricePerKg }
            PriceSort.HIGH_TO_LOW -> list.sortedByDescending { it.pricePerKg }
            PriceSort.NONE -> list
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top Switcher: Produce Marketplace vs My Deliveries
        TabRow(
            selectedTabIndex = selectedScreenTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedScreenTab == 0,
                onClick = { selectedScreenTab = 0 },
                text = { Text("Produce Market", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedScreenTab == 1,
                onClick = { selectedScreenTab = 1 },
                text = {
                    Text(
                        "My Deliveries & OTP (${orders.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }

        if (selectedScreenTab == 0) {
            // Marketplace Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search produce, county, or farmer...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Price Sort Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${filteredProducts.size} produce items available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(
                            onClick = {
                                priceSort = if (priceSort == PriceSort.LOW_TO_HIGH) PriceSort.NONE else PriceSort.LOW_TO_HIGH
                            },
                            label = {
                                Text(
                                    text = "Price: Low-High",
                                    fontSize = 11.sp,
                                    fontWeight = if (priceSort == PriceSort.LOW_TO_HIGH) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        SuggestionChip(
                            onClick = {
                                priceSort = if (priceSort == PriceSort.HIGH_TO_LOW) PriceSort.NONE else PriceSort.HIGH_TO_LOW
                            },
                            label = {
                                Text(
                                    text = "Price: High-Low",
                                    fontSize = 11.sp,
                                    fontWeight = if (priceSort == PriceSort.HIGH_TO_LOW) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Produce Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onBuyClick = { checkoutProduct = product }
                        )
                    }
                }
            }
        } else {
            // My Orders & Delivery Tracking List
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(orders, key = { it.id }) { order ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Order #${order.id}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                val statusColor = when (order.orderStatus) {
                                    "DELIVERED" -> Color(0xFF2E7D32)
                                    "OUT_FOR_DELIVERY" -> Color(0xFFE65100)
                                    else -> Color(0xFF1976D2)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = order.orderStatus,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = order.productName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${"%.0f".format(order.quantityKg)} kg • Total: KES ${"%,.0f".format(order.totalPrice)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Carrier: ${order.driverName} (${order.driverPhone})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (order.orderStatus == "OUT_FOR_DELIVERY") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF8E1))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Delivery OTP: ${order.deliveryOtp} (Share upon delivery)",
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { activeTrackingOrder = order },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Track & Delivery Handshake")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Checkout Bottom Sheet
    checkoutProduct?.let { product ->
        CheckoutBottomSheet(
            product = product,
            onDismiss = { checkoutProduct = null },
            onCheckoutSuccess = { newOrder ->
                orders.add(0, newOrder)
                checkoutProduct = null
                activeTrackingOrder = newOrder
            }
        )
    }

    // Order Tracking & Delivery Handshake Modal
    activeTrackingOrder?.let { order ->
        OrderTrackingDialog(
            order = order,
            isReceiver = true,
            onDismiss = { activeTrackingOrder = null },
            onOrderUpdated = { updatedOrder ->
                val index = orders.indexOfFirst { it.id == updatedOrder.id }
                if (index != -1) {
                    orders[index] = updatedOrder
                }
                activeTrackingOrder = updatedOrder
            }
        )
    }
}

@Composable
fun ProductCard(
    product: Product,
    onBuyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Category & Verification badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (product.isVerified) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Verified",
                                color = Color(0xFF2E7D32),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Product Name
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Farmer & County
            Text(
                text = "${product.farmerName} • ${product.county}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Price & Quantity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "KES ${"%.0f".format(product.pricePerKg)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "per kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = "${"%.0f".format(product.quantityKg)} kg",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Buy Action Button
            Button(
                onClick = onBuyClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text("Order", fontSize = 13.sp)
            }
        }
    }
}
