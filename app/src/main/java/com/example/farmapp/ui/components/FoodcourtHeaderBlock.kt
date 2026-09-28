package com.example.farmapp.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber

/**
 * Shared Foodcourt Header Block:
 * - Greeting row: "Good morning, {name} 👋" (large, bold) + small circular avatar/profile icon
 * - Rounded search bar beneath it (cream/surface fill, no border, soft shadow, leading search icon)
 * - Promo/summary banner card: HarvestAmber-tinted card with a highlight + small pill action button
 */
@Composable
fun FoodcourtHeaderBlock(
    greetingName: String,
    searchPlaceholder: String,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    promoHighlight: String,
    promoSubtitle: String? = null,
    promoActionText: String,
    onPromoAction: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KilivanaSpacing.content, vertical = KilivanaSpacing.small)
    ) {
        // 1. Greeting Row: "Good morning, {name} 👋" + small circular avatar/profile icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Good morning, $greetingName 👋",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(KilivanaSpacing.touchTarget)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile Avatar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Rounded Search Bar (cream/surface fill, no border, soft shadow, leading search icon)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = searchPlaceholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Promo/Summary Banner Card: HarvestAmber-tinted card + small pill action button
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = KilivanaAccents.warning.container // HarvestAmber soft tint
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, HarvestAmber.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = promoHighlight,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = KilivanaAccents.warning.content,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    if (promoSubtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = promoSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = KilivanaAccents.warning.content,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Small pill action button
                Button(
                    onClick = onPromoAction,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = KilivanaSpacing.touchTarget)
                ) {
                    Text(
                        text = promoActionText,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
