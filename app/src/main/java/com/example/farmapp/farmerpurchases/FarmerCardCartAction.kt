package com.example.farmapp.farmerpurchases

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import java.math.RoundingMode

@Composable internal fun FarmerCardCartAction(product: CanonicalFarmerProduct,
    state: FarmerPurchaseState, add: (CanonicalFarmerProduct, Int) -> Unit) {
    val id = product.productId.toLong()
    val loading = state.cartActionId == id
    val failed = state.cartErrorId == id
    val quantity = state.items[id] ?: 0
    val minimum = runCatching { product.minimumOrderQuantity.setScale(0, RoundingMode.CEILING).intValueExact() }.getOrDefault(0)
    Column(Modifier.padding(start = 8.dp).widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = { add(product, minimum) },
            enabled = !state.busy && !state.cartWriteBlocked && minimum > 0,
            modifier = Modifier.size(48.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(when { failed -> Icons.Default.ErrorOutline; quantity > 0 -> Icons.Default.CheckCircle
                else -> Icons.Default.AddShoppingCart },
                contentDescription = if (quantity > 0) "Add more ${product.name} to cart" else "Add ${product.name} to cart")
        }
        when {
            loading -> Text("Adding", style = MaterialTheme.typography.labelSmall)
            failed -> Text(if (state.cartWriteBlocked) "Check cart" else "Try again", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
            quantity > 0 -> Text("Added ($quantity)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
