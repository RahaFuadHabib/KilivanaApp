package com.example.farmapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.farmapp.ui.theme.KilivanaSpacing

@Composable
fun KilivanaEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(KilivanaSpacing.section), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun KilivanaLoadingState(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(KilivanaSpacing.section), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KilivanaErrorMessage(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Text(message, modifier = Modifier.padding(KilivanaSpacing.content), style = MaterialTheme.typography.bodyMedium)
    }
}
