// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.model.Product
import com.example.farmapp.buyers.model.formatKes
import com.example.farmapp.buyers.ui.theme.*

@Composable
fun SearchScreen(products: List<Product>, open: (Product) -> Unit, back: () -> Unit, onSearch:(String)->Unit={}, hasMore:Boolean=false, loading:Boolean=false, more:()->Unit={}) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) { onSearch(query) }
    val results = products.filter {
        it.name.contains(query.trim(), ignoreCase = true) ||
            it.category.contains(query.trim(), ignoreCase = true) ||
            it.sellerName.orEmpty().contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().background(NeutralBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Search produce", style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Try tomatoes or vegetables") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { query = "" }) { Text("Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Text(
            if (query.isBlank()) "Popular produce" else "${results.size} results",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium
        )
        if (results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No matches yet", style = MaterialTheme.typography.titleLarge)
                    Text("Try a different product, category, or seller.", color = TextSecondary)
                    TextButton(onClick = { query = "" }) { Text("Clear search") }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if(hasMore) item { TextButton(onClick=more,enabled=!loading) { Text("Load more results") } }
                items(results, key = { it.id }) { product ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { open(product) },
                        shape = RoundedCornerShape(16.dp), color = NeutralSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralDivider.copy(alpha = .65f))
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProductImage(Modifier.size(76.dp), product.name, product.imageUrl)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(product.category, product.sellerName?.takeIf(String::isNotBlank)).joinToString(" · "),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("${formatKes(product.pricePerUnit)} / ${product.unit}", color = Green700, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

