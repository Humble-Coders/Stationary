package com.humblecoders.stationary.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.humblecoders.stationary.data.model.ColorMode
import com.humblecoders.stationary.data.model.OrderStatus
import com.humblecoders.stationary.data.model.PrintOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCard(
    order: PrintOrder,
    onClick: (PrintOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = getOrderCardColor(order)
    val statusText = getOrderStatusText(order)
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
            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    Column {
                        Text(
                            text = order.documentName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Order ID: ${order.orderId.take(8)}...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusChip(
                    text = statusText,
                    color = getStatusChipColor(order)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            // Order Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column - Order details
                Column {
                    OrderDetail(
                        label = "Pages",
                        value = "${order.pageCount} ${if (order.pageCount != 1) "pages" else "page"}"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    order.printSettings?.let { settings ->
                        OrderDetail(
                            label = "Print Settings",
                            value = "${settings.colorMode.displayName} • ${settings.copies} ${if (settings.copies > 1) "copies" else "copy"}"
                        )
                    } ?: OrderDetail(
                        label = "Print Settings",
                        value = "Not configured"
                    )
                }

                // Right Column - Price and date
                Column(horizontalAlignment = Alignment.End) {
                    if (order.paymentAmount > 0) {
                        Text(
                            text = "₹${String.format("%.2f", order.paymentAmount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (order.isPaid)
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

            // Action indicators (only show if needed)
            if (!order.isPaid || !order.hasSettings) {
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
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (!order.isPaid && !order.hasSettings) {
                            "Payment and print settings needed"
                        } else if (!order.isPaid) {
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

private fun getOrderCardColor(order: PrintOrder): Color {
    return when {
        order.orderStatus == OrderStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.05f) // Green
        order.hasSettings && order.isPaid -> Color(0xFF2196F3).copy(alpha = 0.05f) // Blue
        order.hasSettings && !order.isPaid -> Color(0xFFFF9800).copy(alpha = 0.05f) // Orange
        !order.hasSettings && order.isPaid -> Color(0xFFFF5722).copy(alpha = 0.05f) // Deep Orange
        else -> Color(0xFFF44336).copy(alpha = 0.05f) // Red
    }
}

private fun getStatusChipColor(order: PrintOrder): Color {
    return when {
        order.orderStatus == OrderStatus.COMPLETED -> Color(0xFF4CAF50) // Green
        order.orderStatus == OrderStatus.PRINTING -> Color(0xFF2196F3) // Blue
        order.orderStatus == OrderStatus.QUEUED -> Color(0xFF9C27B0) // Purple
        order.hasSettings && order.isPaid -> Color(0xFF2196F3) // Blue
        order.hasSettings && !order.isPaid -> Color(0xFFFF9800) // Orange
        !order.hasSettings && order.isPaid -> Color(0xFFFF5722) // Deep Orange
        else -> Color(0xFFF44336) // Red
    }
}

private fun getStatusIconBackgroundColor(order: PrintOrder): Color {
    return when {
        order.orderStatus == OrderStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        order.orderStatus == OrderStatus.PRINTING -> Color(0xFF2196F3).copy(alpha = 0.2f)
        order.orderStatus == OrderStatus.QUEUED -> Color(0xFF9C27B0).copy(alpha = 0.2f)
        order.hasSettings && order.isPaid -> Color(0xFF2196F3).copy(alpha = 0.2f)
        order.hasSettings && !order.isPaid -> Color(0xFFFF9800).copy(alpha = 0.2f)
        !order.hasSettings && order.isPaid -> Color(0xFFFF5722).copy(alpha = 0.2f)
        else -> Color(0xFFF44336).copy(alpha = 0.2f)
    }
}

private fun getStatusIconColor(order: PrintOrder): Color {
    return when {
        order.orderStatus == OrderStatus.COMPLETED -> Color(0xFF4CAF50)
        order.orderStatus == OrderStatus.PRINTING -> Color(0xFF2196F3)
        order.orderStatus == OrderStatus.QUEUED -> Color(0xFF9C27B0)
        order.hasSettings && order.isPaid -> Color(0xFF2196F3)
        order.hasSettings && !order.isPaid -> Color(0xFFFF9800)
        !order.hasSettings && order.isPaid -> Color(0xFFFF5722)
        else -> Color(0xFFF44336)
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

private fun getOrderStatusIcon(order: PrintOrder): ImageVector {
    return when {
        order.orderStatus == OrderStatus.COMPLETED -> Icons.Outlined.CheckCircle
        order.orderStatus == OrderStatus.PRINTING || order.orderStatus == OrderStatus.QUEUED -> Icons.Outlined.Description
        order.hasSettings && !order.isPaid -> Icons.Outlined.Payments
        !order.hasSettings -> Icons.Outlined.Settings
        else -> Icons.Outlined.Description
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                text = "We're currently closed for service. Please check back during our operating hours or contact support for assistance.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Contact support */ },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Contact Support")
                }

                Button(
                    onClick = { /* View hours */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("View Hours")
                }
            }
        }
    }
}