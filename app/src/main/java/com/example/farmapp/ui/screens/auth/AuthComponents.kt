package com.example.farmapp.ui.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber
import com.example.farmapp.ui.theme.LeafGreen

internal enum class SignupRole(val label: String) { FARMER("Farmer"), BUYER("Buyer"), SUPPLIER("Supplier"), INSPECTOR("Inspector") }

// Presentation data only: the four pre-existing demo accounts, not routing rules.
internal enum class DemoAccount(val label: String, val email: String) {
    FARMER("Farmer", "farmer@kilivana.com"),
    BUYER("Buyer", "buyer@kilivana.com"),
    SUPPLIER("Supplier", "supplier@kilivana.com"),
    INSPECTOR("Inspector", "inspector@kilivana.com");

    val password: String get() = "1234"
    companion object {
        fun forEmail(email: String): DemoAccount? = entries.firstOrNull {
            it.email.equals(email.trim(), ignoreCase = true)
        }
    }
}

@Composable
internal fun AuthTheme(dark: Boolean, content: @Composable () -> Unit) {
    // Scoped to onboarding; the application and dashboard palettes remain untouched.
    val colors = MaterialTheme.colorScheme.copy(
        primary = if (dark) Color(0xFFA6D9B5) else ForestGreen,
        background = if (dark) Color(0xFF101C16) else Color(0xFFFEFDFC),
        surface = if (dark) Color(0xFF101C16) else Color(0xFFFEFDFC),
        surfaceVariant = if (dark) Color(0xFF213329) else Color(0xFFF2F4F1),
        onSurface = if (dark) Color(0xFFF1F5EF) else Color(0xFF1B2C21),
        onSurfaceVariant = if (dark) Color(0xFFBDCABE) else Color(0xFF59665D),
        outline = if (dark) Color(0xFF7E9E89) else Color(0xFF839C8B)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun AuthPage(
    title: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
            } else Spacer(Modifier.size(48.dp))
            Text(title, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onToggleTheme) {
                Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth()) {
            val viewportHeight = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(horizontal = 28.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
internal fun AuthHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AuthPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = Color.White)) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun AuthNotice(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary
)

@Composable
internal fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onValueChange, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(20.dp),
        colors = authFieldColors(),
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = if (isPassword) ({
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (visible) "Hide password" else "Show password",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }) else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountTypeDropdown(selectedAccount: DemoAccount?, onSelect: (DemoAccount) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedAccount?.label.orEmpty(), onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("Account type") }, placeholder = { Text("Select account type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp), colors = authFieldColors(), singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DemoAccount.entries.forEach { account ->
                DropdownMenuItem(text = { Text(account.label) },
                    onClick = { onSelect(account); expanded = false },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = account == selectedAccount })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignupRoleDropdown(selectedAccount: SignupRole?, onSelect: (SignupRole) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedAccount?.label.orEmpty(), onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("Account type") }, placeholder = { Text("Select account type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp), colors = authFieldColors(), singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            SignupRole.entries.forEach { account ->
                DropdownMenuItem(text = { Text(account.label) },
                    onClick = { onSelect(account); expanded = false },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = account == selectedAccount })
            }
        }
    }
}

/** Original flat illustration: a sun, planted fields and a growing seedling. */
@Composable
internal fun AgriculturalVisual(modifier: Modifier = Modifier, password: Boolean = false) {
    val pale = MaterialTheme.colorScheme.surfaceVariant
    val lockTint = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().height(if (password) 200.dp else 280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (password) 200.dp else 280.dp)) {
            scale(size.width / 280f, size.height / 280f, pivot = Offset.Zero) {
                drawCircle(pale, 122f, Offset(140f, 140f))
                if (!password) {
                    drawCircle(HarvestAmber, 23f, Offset(205f, 65f))
                    val field = Path().apply {
                        moveTo(24f, 194f); cubicTo(85f, 157f, 159f, 164f, 256f, 202f)
                        cubicTo(229f, 272f, 60f, 276f, 24f, 194f); close()
                    }
                    drawPath(field, ForestGreen)
                    for (i in 0..3) {
                        val row = Path().apply {
                            moveTo(55f + i * 38, 249f)
                            quadraticTo(116f + i * 30, 207f, 246f, 204f + i * 10)
                        }
                        drawPath(row, Color(0xFF9CC9A4), style = Stroke(3f, cap = StrokeCap.Round))
                    }
                    drawLine(ForestGreen, Offset(139f, 187f), Offset(139f, 86f), 7f, StrokeCap.Round)
                    drawPath(Path().apply {
                        moveTo(138f, 145f); cubicTo(86f, 146f, 70f, 119f, 75f, 91f)
                        cubicTo(113f, 90f, 141f, 108f, 138f, 145f); close()
                    }, LeafGreen)
                    drawPath(Path().apply {
                        moveTo(141f, 121f); cubicTo(139f, 77f, 170f, 59f, 196f, 72f)
                        cubicTo(198f, 100f, 178f, 125f, 141f, 121f); close()
                    }, ForestGreen)
                    drawLine(Color(0xFFA6D9B5), Offset(139f, 145f), Offset(91f, 108f), 2f, StrokeCap.Round)
                    drawLine(Color(0xFFA6D9B5), Offset(142f, 120f), Offset(181f, 84f), 2f, StrokeCap.Round)
                }
            }
        }
        if (password) {
            Icon(Icons.Default.LockOpen, null, tint = lockTint, modifier = Modifier.size(104.dp))
            Icon(Icons.Default.Spa, null, tint = LeafGreen,
                modifier = Modifier.offset(x = 61.dp, y = 44.dp).size(45.dp))
        }
    }
}
