package com.example.farmapp.ui.screens

import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ---------------------------------------------------------------------------
// Visual Design Color Tokens (Soft Neomorphic Dual-Tone)
// ---------------------------------------------------------------------------
private val ColorDeepForestGreen = Color(0xFF0D4A2D)
private val ColorVibrantLeafGreen = Color(0xFF2E9B42)
private val ColorSoftOffWhite: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val ColorHarvestGold = Color(0xFFF49D0C)
private val ColorChartLeafGreen = Color(0xFF48CF79)
private val ColorSkyBlue = Color(0xFF5A9BFF)
private val ColorCoralOrangeStart = Color(0xFFFF6D43)
private val ColorCoralOrangeEnd = Color(0xFFFF8B66)
private val ColorCardSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val ColorTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// ---------------------------------------------------------------------------
// Custom Organic Asymmetrical Wave Shape
// ---------------------------------------------------------------------------
class OrganicHeaderWaveShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - 45f)
            // Asymmetrical bezier wave curve for natural organic flow
            cubicTo(
                size.width * 0.72f, size.height,
                size.width * 0.28f, size.height - 75f,
                0f, size.height - 25f
            )
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------------------------------------------------------------------------
// State Models
// ---------------------------------------------------------------------------
data class WeeklyYieldData(
    val dayLabel: String,
    val yieldRatio: Float,   // Harvest Gold (Yield volume)
    val soldRatio: Float,    // Leaf Green (Sold volume)
    val escrowRatio: Float   // Sky Blue (In Escrow lock)
)

data class FarmListingItem(
    val id: String,
    val title: String,
    val grade: String,
    val stockKg: Double,
    val pricePerKg: Double,
    val category: String,
    val iconTint: Color = ColorVibrantLeafGreen
)

data class FarmerDashboardUiState(
    val farmerName: String = "James Mwangi",
    val farmLocation: String = "Greenfield Farm, Kiambu",
    val isVerified: Boolean = true,
    val unreadNotificationsCount: Int = 3,
    val weeklyYields: List<WeeklyYieldData> = listOf(
        WeeklyYieldData("Mon", 0.65f, 0.45f, 0.25f),
        WeeklyYieldData("Tue", 0.85f, 0.70f, 0.40f),
        WeeklyYieldData("Wed", 0.50f, 0.35f, 0.20f),
        WeeklyYieldData("Thu", 0.95f, 0.80f, 0.55f),
        WeeklyYieldData("Fri", 0.75f, 0.60f, 0.30f),
        WeeklyYieldData("Sat", 0.90f, 0.85f, 0.60f),
        WeeklyYieldData("Sun", 0.40f, 0.30f, 0.15f)
    ),
    val activeListings: List<FarmListingItem> = listOf(
        FarmListingItem(
            id = "1",
            title = "Organic Hass Avocado",
            grade = "Grade A • 480 kg available",
            stockKg = 480.0,
            pricePerKg = 120.0,
            category = "Fruits",
            iconTint = Color(0xFF2E7D32)
        ),
        FarmListingItem(
            id = "2",
            title = "Red Roma Tomatoes",
            grade = "Grade A+ • 850 kg available",
            stockKg = 850.0,
            pricePerKg = 95.0,
            category = "Veggies",
            iconTint = Color(0xFFC62828)
        ),
        FarmListingItem(
            id = "3",
            title = "French Beans (Export Grade)",
            grade = "Grade 1 • 320 kg available",
            stockKg = 320.0,
            pricePerKg = 180.0,
            category = "Veggies",
            iconTint = Color(0xFF388E3C)
        ),
        FarmListingItem(
            id = "4",
            title = "Yellow Maize Grains",
            grade = "Grade A Dry • 2,400 kg available",
            stockKg = 2400.0,
            pricePerKg = 65.0,
            category = "Cereals",
            iconTint = Color(0xFFF57C00)
        ),
        FarmListingItem(
            id = "5",
            title = "Irish Potatoes (Shangi)",
            grade = "Grade A Large • 1,600 kg available",
            stockKg = 1600.0,
            pricePerKg = 85.0,
            category = "Tubers",
            iconTint = Color(0xFF6D4C41)
        )
    ),
    val selectedNavIndex: Int = 0
)

// ---------------------------------------------------------------------------
// Main Screen Composable
// ---------------------------------------------------------------------------
@Composable
fun FarmerDashboardScreen(
    modifier: Modifier = Modifier,
    uiState: FarmerDashboardUiState = remember { FarmerDashboardUiState() },
    showBottomBar: Boolean = true,
    onPostProduceClick: () -> Unit = {},
    onSeeAllClick: () -> Unit = {},
    onListingClick: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onNavItemSelected: (Int) -> Unit = {}
) {
    var selectedIndex by remember { mutableIntStateOf(uiState.selectedNavIndex) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorSoftOffWhite,
        bottomBar = {
            if (showBottomBar) {
                SoftFloatingBottomBar(
                    selectedIndex = selectedIndex,
                    onItemSelected = { index ->
                        selectedIndex = index
                        onNavItemSelected(index)
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Organic Wave Header with Dynamic Weekly Chart & Floating CTA Bridge
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OrganicChartHeader(
                        farmerName = uiState.farmerName,
                        farmLocation = uiState.farmLocation,
                        isVerified = uiState.isVerified,
                        unreadNotificationsCount = uiState.unreadNotificationsCount,
                        weeklyYields = uiState.weeklyYields,
                        onNotificationClick = onNotificationClick,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Floating CTA Pill: Bridging header curve & body list
                    FloatingPostProduceCtaPill(
                        onClick = onPostProduceClick,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 24.dp)
                            .zIndex(10f)
                    )
                }
            }

            // Spacing to accommodate floating CTA pill overlap
            item {
                Spacer(modifier = Modifier.height(36.dp))
            }

            // 3. Section Title Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Produce Stock & Listings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextPrimary
                        )
                        Text(
                            text = "Active inventory ready for market dispatch",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary
                        )
                    }

                    TextButton(onClick = onSeeAllClick) {
                        Text(
                            text = "See All",
                            color = ColorDeepForestGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                    }
                }
            }

            // 4. Scrollable List of Produce Stock Cards
            items(uiState.activeListings, key = { it.id }) { listing ->
                FarmerStockCard(
                    listing = listing,
                    onClick = { onListingClick(listing.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Organic Chart Header Composable
// ---------------------------------------------------------------------------
@Composable
fun OrganicChartHeader(
    farmerName: String,
    farmLocation: String,
    isVerified: Boolean,
    unreadNotificationsCount: Int,
    weeklyYields: List<WeeklyYieldData>,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(OrganicHeaderWaveShape())
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ColorDeepForestGreen, ColorVibrantLeafGreen)
                )
            )
            .padding(top = 48.dp, bottom = 48.dp, start = 20.dp, end = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Identity Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Farmer Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = farmerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (isVerified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Producer",
                                    tint = ColorHarvestGold,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = farmLocation,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )
                        }
                    }
                }

                // Notification Bell with Badge
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationsCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = unreadNotificationsCount.toString(),
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Chart Title & Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Weekly Harvest & Yield",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Real-time Field Performance Tracker",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "This Week",
                        color = Color.White,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Vertical Pillar Bar Chart Canvas
            WeeklyYieldBarChart(
                yields = weeklyYields,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Legend Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendDot(color = ColorHarvestGold, label = "Yield")
                Spacer(modifier = Modifier.width(20.dp))
                ChartLegendDot(color = ColorChartLeafGreen, label = "Sold")
                Spacer(modifier = Modifier.width(20.dp))
                ChartLegendDot(color = ColorSkyBlue, label = "Escrow")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Native Compose Vertical Pillar Bar Chart
// ---------------------------------------------------------------------------
@Composable
fun WeeklyYieldBarChart(
    yields: List<WeeklyYieldData>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        yields.forEach { data ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Triple Vertical Pillar Bars
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Yield Bar (Harvest Gold)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(data.yieldRatio.coerceIn(0.1f, 1.0f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(ColorHarvestGold)
                    )

                    // Sold Bar (Leaf Green)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(data.soldRatio.coerceIn(0.1f, 1.0f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(ColorChartLeafGreen)
                    )

                    // Escrow Bar (Sky Blue)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(data.escrowRatio.coerceIn(0.1f, 1.0f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(ColorSkyBlue)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Day Label
                Text(
                    text = data.dayLabel,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chart Legend Dot Composable
// ---------------------------------------------------------------------------
@Composable
fun ChartLegendDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// Floating CTA Pill Bridge Button ("+ Post New Produce")
// ---------------------------------------------------------------------------
@Composable
fun FloatingPostProduceCtaPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(ForestGreen)
                .padding(horizontal = 24.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Post Produce",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Post New Produce",
                    color = Color.White,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Produce Stock Card Composable
// ---------------------------------------------------------------------------
@Composable
fun FarmerStockCard(
    listing: FarmListingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ColorCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Tinted Icon Container + Titles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Tinted Icon Container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(listing.iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = listing.title,
                        tint = listing.iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title & Subtitle with Grade and Available Stock
                Column {
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = listing.grade,
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextSecondary,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Price Badge & Overflow More Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorVibrantLeafGreen.copy(alpha = 0.1f))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "KSh ${"%,.0f".format(listing.pricePerKg)}/kg",
                        color = ColorDeepForestGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }

                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(KilivanaSpacing.touchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Listing Options",
                        tint = ColorTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Minimalist Bottom Navigation Dock Composable
// ---------------------------------------------------------------------------
private data class DockNavItem(
    val icon: ImageVector,
    val label: String
)

@Composable
fun SoftFloatingBottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        DockNavItem(Icons.Default.Home, "Home"),
        DockNavItem(Icons.Default.BarChart, "Analytics"),
        DockNavItem(Icons.Default.AccountBalanceWallet, "Escrow"),
        DockNavItem(Icons.Default.Person, "Profile")
    )

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = ColorCardSurface,
        tonalElevation = 6.dp
    ) {
        NavigationBar(
            containerColor = ColorCardSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.heightIn(min = 80.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onItemSelected(index) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(23.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = ColorTextSecondary,
                        unselectedTextColor = ColorTextSecondary
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Compose Previews
// ---------------------------------------------------------------------------
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun FarmerDashboardScreenPreview() {
    MaterialTheme {
        FarmerDashboardScreen()
    }
}
