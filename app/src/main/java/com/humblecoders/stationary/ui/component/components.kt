package com.humblecoders.stationary.ui.component

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.stationary.data.model.PrintOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCard(
    order: PrintOrder,
    onClick: (PrintOrder) -> Unit,
    modifier: Modifier = Modifier,
    getPaymentStatusDisplay: (PrintOrder) -> String,
    getOrderStatusDisplay: (PrintOrder) -> String
) {
    val cardColor = getOrderCardColor(order)
    val statusText = getOrderStatusDisplay(order)
    val statusIcon = getOrderStatusIcon(order)

    val animatedColor by animateColorAsState(
        targetValue = cardColor,
        animationSpec = tween(durationMillis = 300),
        label = "CardColorAnimation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = animatedColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(order) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row - Icon and Document Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(getStatusIconBackgroundColor(order)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = getStatusIconColor(order),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = getDisplayDocumentName(order),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Order ID: ${order.orderId.take(8)}...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                StatusChip(
                    text = statusText,
                    color = getStatusChipColor(order)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Order Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))

                    OrderDetail(
                        label = "Payment",
                        value = getPaymentStatusDisplay(order)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Show document count if multiple documents
                    val documentCount = getDocumentCount(order)
                    if (documentCount > 1) {
                        OrderDetail(
                            label = "Documents",
                            value = "$documentCount files"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OrderDetail(
                        label = "Print Settings",
                        value = getPrintSettingsDisplay(order)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (order.paymentAmount > 0) {
                        Text(
                            text = "₹${String.format("%.2f", order.paymentAmount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (getPaymentStatusDisplay(order) == "Paid")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = formatDate(order.createdAt.toDate()),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action indicators
            val isPaid = getPaymentStatusDisplay(order) == "Paid"
            val hasSettings = order.individualDocuments.isNotEmpty()

            if (!isPaid || !hasSettings) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(getStatusIconBackgroundColor(order)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (com.humblecoders.stationary.data.model.FileType.getDisplayNameFromFileTypeString(order.fileType) == "Word")
                                Icons.Default.Description
                            else
                                Icons.Outlined.Description,
                            contentDescription = null,
                            tint = getStatusIconColor(order),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (!isPaid && !hasSettings) {
                            "Payment and print settings needed"
                        } else if (!isPaid) {
                            "Payment needed to proceed"
                        } else {
                            "Configure print settings"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


private fun getOrderCardColor(order: PrintOrder): Color {
    return when (order.orderStatus.toString()) {
        "PRINTED" -> Color(0xFF4CAF50).copy(alpha = 0.05f) // Green
        "QUEUED" -> Color(0xFF2196F3).copy(alpha = 0.05f) // Blue
        "SUBMITTED" -> Color(0xFFFF9800).copy(alpha = 0.05f) // Orange
        else -> Color(0xFFF44336).copy(alpha = 0.05f) // Red
    }
}

private fun getStatusChipColor(order: PrintOrder): Color {
    return when (order.orderStatus.toString()) {
        "PRINTED" -> Color(0xFF4CAF50) // Green
        "QUEUED" -> Color(0xFF2196F3) // Blue
        "SUBMITTED" -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}

private fun getStatusIconBackgroundColor(order: PrintOrder): Color {
    return when (order.orderStatus.toString()) {
        "PRINTED" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        "QUEUED" -> Color(0xFF2196F3).copy(alpha = 0.2f)
        "SUBMITTED" -> Color(0xFFFF9800).copy(alpha = 0.2f)
        else -> Color(0xFFF44336).copy(alpha = 0.2f)
    }
}

private fun getStatusIconColor(order: PrintOrder): Color {
    return when (order.orderStatus.toString()) {
        "PRINTED" -> Color(0xFF4CAF50)
        "QUEUED" -> Color(0xFF2196F3)
        "SUBMITTED" -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

private fun getOrderStatusIcon(order: PrintOrder): ImageVector {
    return when (order.orderStatus.toString()) {
        "PRINTED" -> Icons.Outlined.CheckCircle
        "QUEUED" -> Icons.Outlined.Description
        "SUBMITTED" -> Icons.Outlined.Payments
        else -> Icons.Outlined.Settings
    }
}

@Composable
private fun OrderDetail(
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50), // Pill shape
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(date)
}

@Composable
fun ShopClosedCard(
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Shop is Currently Closed",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "We're currently closed for service. Please check back during our operating hours.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )



        }
    }
}

private fun getDocumentCount(order: PrintOrder): Int {
    return order.documentCount.takeIf { it > 0 } ?: run {
        // Fallback for older orders without documentCount field
        if (order.individualDocuments.isNotEmpty()) {
            order.individualDocuments.size
        } else {
            1
        }
    }
}



// Get display document name from individualDocuments
private fun getDisplayDocumentName(order: PrintOrder): String {
    return when {
        order.individualDocuments.isEmpty() -> "Unknown document"
        order.individualDocuments.size == 1 -> {
            val fileName = order.individualDocuments.first()["fileName"] as? String
            fileName ?: "Unknown document"
        }
        else -> {
            "${order.documentCount} ${com.humblecoders.stationary.data.model.FileType.getDisplayNameFromFileTypeString(order.fileType)} files"
        }
    }
}

// In components.kt - Update the getPrintSettingsDisplaySafe function

private fun getPrintSettingsDisplay(order: PrintOrder): String {
    return try {
        when {
            order.individualDocuments.isEmpty() -> "Not configured"
            order.individualDocuments.size == 1 -> {
                val doc = order.individualDocuments.first()
                val printSettings = doc["printSettings"] as? Map<*, *> ?: return "Not configured"
                val customBWPages = printSettings["customBWPages"] as? String ?: ""
                val customColorPages = printSettings["customColorPages"] as? String ?: ""
                val copies = (printSettings["copies"] as? Number)?.toInt() ?: 1

                when {
                    customBWPages.isNotEmpty() && customColorPages.isNotEmpty() -> {
                        "Mixed (B&W: $customBWPages, Color: $customColorPages) • $copies ${if (copies > 1) "copies" else "copy"}"
                    }
                    customBWPages.isNotEmpty() -> {
                        "B&W pages: $customBWPages • $copies ${if (copies > 1) "copies" else "copy"}"
                    }
                    customColorPages.isNotEmpty() -> {
                        "Color pages: $customColorPages • $copies ${if (copies > 1) "copies" else "copy"}"
                    }
                    else -> "Not configured"
                }
            }
            else -> {
                "Individual settings (${order.individualDocuments.size} docs)"
            }
        }
    } catch (e: Exception) {
        Log.w("OrderCard", "Error parsing print settings: ${e.message}")
        "Settings configured"
    }
}
