package com.example.farmapp.ui.screens

import com.example.farmapp.ui.components.KilivanaEmptyState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmapp.data.model.Order
import com.example.farmapp.data.model.Product
import com.example.farmapp.data.model.mockOrders
import com.example.farmapp.data.model.mockProducts
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber
import com.example.farmapp.ui.theme.LeafGreen

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Spa
import com.example.farmapp.ui.components.FoodcourtHeaderBlock
import com.example.farmapp.ui.theme.PastelButter
import com.example.farmapp.ui.theme.PastelMint
import com.example.farmapp.ui.theme.PastelPeach
import com.example.farmapp.ui.theme.PastelSage

enum class PriceSort {
    NONE,
    LOW_TO_HIGH,
    HIGH_TO_LOW
}

private data class FoodcourtCategoryItem(
    val name: String,
    val priceHint: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val backgroundColor: Color,
    val iconTint: Color
)

@Composable
fun BuyerScreen(
    modifier: Modifier = Modifier,
    products: List<Product> = mockProducts
) {
    var selectedScreenTab by remember { mutableIntStateOf(0) }
    val orders = remember { mutableStateListOf<Order>().apply { addAll(mockOrders.take(3)) } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All Fresh") }
    var priceSort by remember { mutableStateOf(PriceSort.NONE) }
    var selectedLocationHub by remember { mutableStateOf("Westlands Cold Pod") }
    var showLocationSelector by remember { mutableStateOf(false) }

    // BottomSheet & Dialog states
    var checkoutProduct by remember { mutableStateOf<Product?>(null) }
    var activeTrackingOrder by remember { mutableStateOf<Order?>(null) }

    // Horizontal category selector chips (Behance requirement: All Fresh, Fruits, Veggies, Cereals, Tubers)
    val categories = remember {
        listOf("All Fresh", "Fruits", "Veggies", "Cereals", "Tubers")
    }

    val filteredProducts = remember(searchQuery, selectedCategory, priceSort, products) {
        val list = products.filter { product ->
            val matchesQuery = product.name.contains(searchQuery, ignoreCase = true) ||
                    product.county.contains(searchQuery, ignoreCase = true) ||
                    product.farmerName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "All Fresh" -> true
                "Fruits" -> product.category.equals("Fruits", ignoreCase = true)
                "Veggies" -> product.category.equals("Veggies", ignoreCase = true) || product.category.contains("Veg", ignoreCase = true)
                "Cereals" -> product.category.equals("Cereals", ignoreCase = true) || product.category.contains("Cereal", ignoreCase = true) || product.category.contains("Grain", ignoreCase = true)
                "Tubers" -> product.category.equals("Tubers", ignoreCase = true) || product.category.contains("Tuber", ignoreCase = true) || product.category.contains("Root", ignoreCase = true)
                else -> product.category.equals(selectedCategory, ignoreCase = true)
            }
            matchesQuery && matchesCategory
        }

        when (priceSort) {
            PriceSort.LOW_TO_HIGH -> list.sortedBy { it.pricePerKg }
            PriceSort.HIGH_TO_LOW -> list.sortedByDescending { it.pricePerKg }
            PriceSort.NONE -> list
        }
    }

    val foodcourtCategories = remember {
        listOf(
            FoodcourtCategoryItem(
                name = "Fruits",
                priceHint = "from KES 120/kg",
                icon = Icons.Default.Spa,
                backgroundColor = PastelMint,
                iconTint = ForestGreen
            ),
            FoodcourtCategoryItem(
                name = "Veggies",
                priceHint = "from KES 85/kg",
                icon = Icons.Default.Eco,
                backgroundColor = PastelPeach,
                iconTint = Color(0xFFC05621)
            ),
            FoodcourtCategoryItem(
                name = "Cereals",
                priceHint = "from KES 65/kg",
                icon = Icons.Default.Grass,
                backgroundColor = PastelButter,
                iconTint = Color(0xFF975A16)
            ),
            FoodcourtCategoryItem(
                name = "Tubers",
                priceHint = "from KES 75/kg",
                icon = Icons.Default.Park,
                backgroundColor = PastelSage,
                iconTint = Color(0xFF2E6A3B)
            )
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Shared Foodcourt Header Block: Greeting, Search Bar, Promo Banner
        FoodcourtHeaderBlock(
            greetingName = "Sarah",
            searchPlaceholder = "Search fresh produce, veggies, fruits...",
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            promoHighlight = "Get 20% off your first order!",
            promoSubtitle = "Valid on all verified organic produce from local hubs",
            promoActionText = "Claim Offer",
            onPromoAction = { selectedCategory = "All Fresh" }
        )

        // Top Switcher: Produce Marketplace vs My Deliveries
        TabRow(
            selectedTabIndex = selectedScreenTab,
            modifier = Modifier.fillMaxWidth(),
            contentColor = ForestGreen,
            indicator = { tabPositions ->
                if (selectedScreenTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedScreenTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = selectedScreenTab == 0,
                onClick = { selectedScreenTab = 0 },
                selectedContentColor = ForestGreen,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("Produce Market", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedScreenTab == 1,
                onClick = { selectedScreenTab = 1 },
                selectedContentColor = ForestGreen,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        "My Deliveries & OTP (${orders.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }

        if (selectedScreenTab == 0) {
            // Marketplace Screen with unified responsive LazyVerticalGrid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Location Drop-Down & Sort Filter Row
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Location Hub Pill
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    selectedLocationHub = if (selectedLocationHub.contains("Westlands")) {
                                        "Kilimani Aggregation Bay"
                                    } else {
                                        "Westlands Cold Pod"
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Delivery Pod",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = selectedLocationHub,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Change Pod",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Sort Filter Pill
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(if (priceSort != PriceSort.NONE) ForestGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    priceSort = when (priceSort) {
                                        PriceSort.NONE -> PriceSort.LOW_TO_HIGH
                                        PriceSort.LOW_TO_HIGH -> PriceSort.HIGH_TO_LOW
                                        PriceSort.HIGH_TO_LOW -> PriceSort.NONE
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Sort Price",
                                    tint = if (priceSort != PriceSort.NONE) ForestGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = when (priceSort) {
                                        PriceSort.NONE -> "Sort Price"
                                        PriceSort.LOW_TO_HIGH -> "Price: Low-High"
                                        PriceSort.HIGH_TO_LOW -> "Price: High-Low"
                                    },
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (priceSort != PriceSort.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. "Shop by Category" Section Header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shop by Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (selectedCategory != "All Fresh") {
                            Text(
                                text = "Reset ($selectedCategory ✕)",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ForestGreen.copy(alpha = 0.1f))
                                    .clickable { selectedCategory = "All Fresh" }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 3. 4 Soft Pastel Category Cards (Mint, Peach, Butter, Sage)
                items(foodcourtCategories, key = { it.name }) { cat ->
                    val isSelected = selectedCategory == cat.name
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(cat.backgroundColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ForestGreen else cat.backgroundColor,
                                shape = MaterialTheme.shapes.large
                            )
                            .clickable {
                                selectedCategory = if (selectedCategory == cat.name) "All Fresh" else cat.name
                            }
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(cat.iconTint.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = cat.name,
                                        tint = cat.iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(ForestGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                color = Color(0xFF1E231F)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = cat.priceHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5A625C),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 4. Produce Listings Header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedCategory == "All Fresh") "Fresh Produce Lots" else "$selectedCategory Listings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "${filteredProducts.size} lots available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    }
                }

                // 5. Product Cards or Empty state
                if (filteredProducts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        KilivanaEmptyState(
                            title = "No produce found",
                            message = "Try adjusting your search or category filter"
                        )
                    }
                } else {
                    items(filteredProducts, key = { it.id }) { product ->
                        BehanceProduceCard(
                            product = product,
                            onAddToCart = { checkoutProduct = product }
                        )
                    }
                }
            }
        } else {
            // My Deliveries Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Active Escrow Deliveries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Release payment securely by sharing your delivery OTP at drop-off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(orders, key = { it.id }) { order ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
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
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(KilivanaAccents.success.container)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = order.orderStatus,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize
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
                                    text = "Logistics: ${order.driverName} • ${order.driverPhone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Quantity: ${"%.0f".format(order.quantityKg)} kg",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "KES ${"%,.0f".format(order.totalPrice)} (Escrow)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { activeTrackingOrder = order },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                                ) {
                                    Text("View Handshake OTP & Track")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Checkout Modal
    checkoutProduct?.let { product ->
        var quantityInput by remember { mutableStateOf("10") }
        val qty = quantityInput.toDoubleOrNull() ?: 10.0
        val total = qty * product.pricePerKg

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { checkoutProduct = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Lock Escrow & Confirm Order", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    Text(
                        text = "Produce: ${product.name} (${product.county})",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Price: KES ${"%.0f".format(product.pricePerKg)}/kg",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        label = { Text("Quantity in Kilograms") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(KilivanaAccents.success.container)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Total Escrow: KES ${"%,.0f".format(total)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Funds are locked in escrow and will only be released to the farmer after you inspect produce at delivery.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newOrder = Order(
                            id = (orders.size + 100).toLong(),
                            buyerName = "Sarah Ochieng",
                            productName = product.name,
                            quantityKg = qty,
                            totalPrice = total,
                            orderStatus = "CONFIRMED",
                            deliveryOtp = "5821",
                            driverName = "Kiprono Transporters",
                            driverPhone = "+254 722 000 111",
                            paymentMethod = "MPESA",
                            paymentStatus = "ESCROW"
                        )
                        orders.add(0, newOrder)
                        checkoutProduct = null
                        selectedScreenTab = 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Lock Escrow & Order")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { checkoutProduct = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delivery Tracking Dialog
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

// ---------------------------------------------------------------------------
// Behance Produce Card (Soft Shadow, Price Tag per kg, Prominent "+" Button)
// ---------------------------------------------------------------------------
@Composable
fun BehanceProduceCard(
    product: Product,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Category Badge & Verified Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }

                if (product.isVerified) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LeafGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = LeafGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Verified",
                                color = LeafGreen,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Produce Title
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Farmer & County Origin
            Text(
                text = "${product.farmerName} • ${product.county}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Row: Price Tag per kg & Prominent "+" Add-To-Cart Circle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KES ${"%.0f".format(product.pricePerKg)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "/kg (${"%.0f".format(product.quantityKg)}kg avail)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }

                // Prominent "+" Add-To-Cart Circle Button
                Box(
                    modifier = Modifier
                        .size(KilivanaSpacing.touchTarget)
                        .clip(CircleShape)
                        .background(ForestGreen)
                        .clickable { onAddToCart() }
                        ,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add to Cart",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
