package com.humblecoders.stationary.ui.component


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.stationary.data.model.ColorMode
import com.humblecoders.stationary.data.model.OrderStatus
import com.humblecoders.stationary.data.model.Orientation
import com.humblecoders.stationary.data.model.PageSelection
import com.humblecoders.stationary.data.model.PaperSize
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.PrintSettings
import com.humblecoders.stationary.data.model.Quality
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrderCard(
    order: PrintOrder,
    modifier: Modifier = Modifier
) {
    val cardColor = getOrderCardColor(order)
    val statusText = getOrderStatusText(order)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.documentName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = "Order ID: ${order.orderId.take(8)}...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusChip(
                    text = statusText,
                    backgroundColor = cardColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Pages: ${order.pageCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    order.printSettings?.let { settings ->
                        Text(
                            text = "${settings.colorMode.displayName} • ${settings.copies} copies",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (order.paymentAmount > 0) {
                        Text(
                            text = "₹${String.format("%.2f", order.paymentAmount)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = formatDate(order.createdAt.toDate()),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    backgroundColor: Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = backgroundColor.copy(alpha = 0.8f),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun getOrderCardColor(order: PrintOrder): Color {
    return when {
        order.hasSettings && order.isPaid -> Color(0xFF4CAF50).copy(alpha = 0.1f) // Green
        order.hasSettings && !order.isPaid -> Color(0xFFFF9800).copy(alpha = 0.1f) // Yellow
        !order.hasSettings && order.isPaid -> Color(0xFFFF5722).copy(alpha = 0.1f) // Orange
        else -> Color(0xFFF44336).copy(alpha = 0.1f) // Red
    }
}

private fun getOrderStatusText(order: PrintOrder): String {
    return when {
        order.orderStatus == OrderStatus.COMPLETED -> "Completed"
        order.orderStatus == OrderStatus.PRINTING -> "Printing"
        order.orderStatus == OrderStatus.QUEUED -> "In Queue"
        order.hasSettings && order.isPaid -> "Ready to Print"
        order.hasSettings && !order.isPaid -> "Payment Pending"
        !order.hasSettings && order.isPaid -> "Settings Needed"
        else -> "Action Required"
    }
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(date)
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsCard(
    settings: PrintSettings,
    onSettingsChange: (PrintSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Print Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Color Mode Section
            Text(
                text = "Color Mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                ColorMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = settings.colorMode == mode,
                                onClick = { onSettingsChange(settings.copy(colorMode = mode)) },
                                role = Role.RadioButton
                            )
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.colorMode == mode,
                            onClick = null
                        )
                        Text(
                            text = "${mode.displayName} (₹${if (mode == ColorMode.COLOR) "5" else "2"}/page)",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Page Selection Section
            Text(
                text = "Pages to Print",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                PageSelection.values().forEach { selection ->
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = settings.pagesToPrint == selection,
                                onClick = { onSettingsChange(settings.copy(pagesToPrint = selection)) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.pagesToPrint == selection,
                            onClick = null
                        )
                        Text(
                            text = selection.displayName,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (settings.pagesToPrint == PageSelection.CUSTOM) {
                    OutlinedTextField(
                        value = settings.customPages,
                        onValueChange = { onSettingsChange(settings.copy(customPages = it)) },
                        label = { Text("Custom Pages") },
                        placeholder = { Text("e.g., 1-3,5,7-10") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }

            // Copies Section
            Text(
                text = "Number of Copies",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = settings.copies.toString(),
                onValueChange = { value ->
                    val copies = value.toIntOrNull()?.coerceIn(1, 10) ?: 1
                    onSettingsChange(settings.copy(copies = copies))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(120.dp)
                    .padding(bottom = 16.dp)
            )

            // Paper Size Section
            Text(
                text = "Paper Size",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var paperSizeExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = paperSizeExpanded,
                onExpandedChange = { paperSizeExpanded = !paperSizeExpanded },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = settings.paperSize.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = paperSizeExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .width(140.dp)
                )

                ExposedDropdownMenu(
                    expanded = paperSizeExpanded,
                    onDismissRequest = { paperSizeExpanded = false }
                ) {
                    PaperSize.values().forEach { size ->
                        DropdownMenuItem(
                            text = { Text(size.displayName) },
                            onClick = {
                                onSettingsChange(settings.copy(paperSize = size))
                                paperSizeExpanded = false
                            }
                        )
                    }
                }
            }

            // Orientation Section
            Text(
                text = "Orientation",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Orientation.values().forEach { orientation ->
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = settings.orientation == orientation,
                                onClick = { onSettingsChange(settings.copy(orientation = orientation)) },
                                role = Role.RadioButton
                            )
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.orientation == orientation,
                            onClick = null
                        )
                        Text(
                            text = orientation.displayName,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Quality Section
            Text(
                text = "Print Quality",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var qualityExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = qualityExpanded,
                onExpandedChange = { qualityExpanded = !qualityExpanded }
            ) {
                OutlinedTextField(
                    value = settings.quality.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .width(140.dp)
                )

                ExposedDropdownMenu(
                    expanded = qualityExpanded,
                    onDismissRequest = { qualityExpanded = false }
                ) {
                    Quality.values().forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality.displayName) },
                            onClick = {
                                onSettingsChange(settings.copy(quality = quality))
                                qualityExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}




@Composable
fun ShopClosedCard(
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 12.dp)
            )

            Text(
                text = "Shop is Currently Closed",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Please check back later or contact the shop directly for assistance.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}



private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

@Composable
fun PrintShopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

