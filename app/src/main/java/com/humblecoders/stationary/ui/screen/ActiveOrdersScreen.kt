package com.humblecoders.stationary.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

// Modern color palette
private val BlueBtn = Color(0xFF3B82F6)
private val BackgroundGray = Color(0xFFF9FAFB)
private val CardWhite = Color.White
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val BorderGray = Color(0xFFE5E7EB)
private val SuccessGreen = Color(0xFF10B981)
private val OrangeAccent = Color(0xFFF59E0B)
private val ErrorRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveOrdersScreen(
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit
) {
    val homeUiState by homeViewModel.uiState.collectAsState()

    val activeOrders = homeUiState.orders.filter { order ->
        order.orderStatus.toString() == "SUBMITTED" ||
                order.orderStatus.toString() == "QUEUED"
    }

    var selectedOrder by remember { mutableStateOf<PrintOrder?>(null) }

    Scaffold(
        topBar = {
            Surface(
                color = CardWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active Orders",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (activeOrders.isNotEmpty()) {
                            Text(
                                text = "${activeOrders.size} ${if (activeOrders.size == 1) "order" else "orders"} in progress",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        containerColor = BackgroundGray
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (activeOrders.isEmpty()) {
                EmptyActiveOrdersView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeOrders, key = { it.orderId }) { order ->
                        ActiveOrderCard(
                            order = order,
                            onClick = { selectedOrder = order },
                            viewModel = homeViewModel
                        )
                    }
                }
            }
        }
    }

    // Order Details Dialog
    selectedOrder?.let { order ->
        OrderDetailsDialog(
            order = order,
            onDismiss = { selectedOrder = null },
            viewModel = homeViewModel
        )
    }
}

@Composable
private fun EmptyActiveOrdersView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BlueBtn.copy(alpha = 0.1f),
                modifier = Modifier.size(100.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = BlueBtn,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "No Active Orders",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "You don't have any orders in progress.\nStart printing to see your active orders here.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ActiveOrderCard(
    order: PrintOrder,
    onClick: () -> Unit,
    viewModel: HomeViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = getStatusColor(order).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = getStatusColor(order),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Order #${order.orderId.take(8)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = formatDate(order.createdAt.toDate()),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = getStatusColor(order).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = viewModel.getOrderStatusDisplay(order),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = getStatusColor(order),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Order info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailItem(label = "Shop", value = order.shopId)
                    DetailItem(
                        label = "Documents",
                        value = "${getDocumentCount(order)} ${if (getDocumentCount(order) == 1) "file" else "files"}"
                    )
                }

                if (order.paymentAmount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹${String.format("%.2f", order.paymentAmount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueBtn
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Paid",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Tap to view details",
                fontSize = 12.sp,
                color = BlueBtn,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderDetailsDialog(
    order: PrintOrder,
    onDismiss: () -> Unit,
    viewModel: HomeViewModel
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    color = BlueBtn,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Order Details",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Order #${order.orderId}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Status card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = getStatusColor(order).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = getStatusColor(order),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = viewModel.getOrderStatusDisplay(order),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = getStatusColor(order)
                                )
                                Text(
                                    text = "Your order is being processed",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Basic Information
                    DetailSection(title = "Basic Information") {
                        DetailRowWithIcon(
                            label = "Order Date",
                            value = formatFullDate(order.createdAt.toDate()),
                            icon = Icons.Default.CalendarToday
                        )
                        DetailRowWithIcon(
                            label = "Shop Location",
                            value = order.shopId,
                            icon = Icons.Default.Store
                        )
                        if (order.customerPhone.isNotEmpty()) {
                            DetailRowWithIcon(
                                label = "Customer Phone",
                                value = order.customerPhone,
                                icon = Icons.Default.Receipt
                            )
                        }
                    }

                    HorizontalDivider(color = BorderGray)

                    // Payment Information
                    if (order.paymentAmount > 0) {
                        DetailSection(title = "Payment Information") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Payment Status",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuccessGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = viewModel.getPaymentStatusDisplay(order),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Total Amount",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "₹${String.format("%.2f", order.paymentAmount)}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueBtn
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = BorderGray)
                    }

                    // Documents Information
                    DetailSection(title = "Documents (${getDocumentCount(order)})") {
                        DocumentsList(order = order)
                    }

                    // Print Settings
                    val printSettingsInfo = getDetailedPrintSettings(order)
                    if (printSettingsInfo.isNotEmpty()) {
                        HorizontalDivider(color = BorderGray)
                        DetailSection(title = "Print Settings") {
                            printSettingsInfo.forEach { (label, value) ->
                                PrintSettingRow(label = label, value = value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        content()
    }
}

@Composable
private fun DetailRowWithIcon(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BlueBtn.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BlueBtn,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextSecondary
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun DocumentsList(order: PrintOrder) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (order.individualDocuments.isNotEmpty()) {
            order.individualDocuments.forEachIndexed { index, doc ->
                DocumentItemCard(
                    fileName = (doc["fileName"] as? String) ?: "Document ${index + 1}",
                    fileType = (doc["fileType"] as? String) ?: order.fileType,
                    printSettings = doc["printSettings"] as? Map<*, *>
                )
            }
        } else {
            DocumentItemCard(
                fileName = "Document",
                fileType = order.fileType,
                printSettings = null
            )
        }
    }
}

@Composable
private fun DocumentItemCard(
    fileName: String,
    fileType: String,
    printSettings: Map<*, *>?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = BackgroundGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BlueBtn.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = BlueBtn,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getFileTypeDisplay(fileType),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        if (printSettings != null) {
                            val copies = (printSettings["copies"] as? Number)?.toInt() ?: 1
                            if (copies > 1) {
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$copies ${if (copies > 1) "copies" else "copy"}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Print settings details
            printSettings?.let { settings ->
                val bwPages = settings["customBWPages"] as? String ?: ""
                val colorPages = settings["customColorPages"] as? String ?: ""

                if (bwPages.isNotEmpty() || colorPages.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (bwPages.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TextSecondary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "B&W Pages: $bwPages",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (colorPages.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BlueBtn.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Color Pages: $colorPages",
                                    fontSize = 11.sp,
                                    color = BlueBtn,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintSettingRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

private fun getFileTypeDisplay(extension: String): String {
    return when (extension) {
        ".pdf" -> "PDF"
        ".docx" -> "Word"
        ".doc" -> "Word (Legacy)"
        ".pptx" -> "PowerPoint"
        ".ppt" -> "PowerPoint (Legacy)"
        ".xlsx" -> "Excel"
        ".xls" -> "Excel (Legacy)"
        ".txt" -> "Text"
        ".rtf" -> "Rich Text"
        ".jpg", ".jpeg", ".png", ".webp" -> "Image"
        else -> "Document"
    }
}

private fun getDetailedPrintSettings(order: PrintOrder): List<Pair<String, String>> {
    val settings = mutableListOf<Pair<String, String>>()

    try {
        if (order.individualDocuments.isNotEmpty()) {
            order.individualDocuments.forEachIndexed { index, doc ->
                val printSettings = doc["printSettings"] as? Map<*, *>
                printSettings?.let { ps ->
                    val copies = (ps["copies"] as? Number)?.toInt() ?: 1
                    val bwPages = ps["customBWPages"] as? String ?: ""
                    val colorPages = ps["customColorPages"] as? String ?: ""

                    if (order.individualDocuments.size > 1) {
                        settings.add("Document ${index + 1}" to "")
                    }

                    if (copies > 1) {
                        settings.add("  Copies" to copies.toString())
                    }

                    if (bwPages.isNotEmpty()) {
                        settings.add("  B&W Pages" to bwPages)
                    }
                    if (colorPages.isNotEmpty()) {
                        settings.add("  Color Pages" to colorPages)
                    }

                    if (index < order.individualDocuments.size - 1) {
                        settings.add("" to "") // Spacer
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Handle error silently
    }

    return settings
}

// Helper functions
private fun getStatusColor(order: PrintOrder): Color {
    return when (order.orderStatus.toString()) {
        "QUEUED" -> BlueBtn
        "SUBMITTED" -> OrangeAccent
        "PRINTED" -> SuccessGreen
        else -> ErrorRed
    }
}

private fun getDocumentCount(order: PrintOrder): Int {
    return order.documentCount.takeIf { it > 0 } ?: run {
        if (order.individualDocuments.isNotEmpty()) {
            order.individualDocuments.size
        } else {
            1
        }
    }
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(date)
}

private fun formatFullDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    return formatter.format(date)
}