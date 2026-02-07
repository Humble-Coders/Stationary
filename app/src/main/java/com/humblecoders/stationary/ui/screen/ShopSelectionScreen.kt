package com.humblecoders.stationary.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.stationary.data.model.PricePerPage
import com.humblecoders.stationary.data.model.ShopId
import com.humblecoders.stationary.ui.viewmodel.DocumentUploadViewModel
import com.humblecoders.stationary.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

// Modern color palette (same as HomeScreen)
private val BlueBtn = Color(0xFF3B82F6)
private val BackgroundGray = Color(0xFFF9FAFB)
private val CardWhite = Color.White
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val BorderGray = Color(0xFFE5E7EB)
private val SuccessGreen = Color(0xFF10B981)
private val ErrorRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSelectionScreen(
    homeViewModel: HomeViewModel,
    documentUploadViewModel: DocumentUploadViewModel,
    sharedFileCount: Int,
    onShopSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val homeUiState by homeViewModel.uiState.collectAsState()
    
    // Check if there are existing documents with a shop ID
    var isCheckingExistingShop by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        // Brief delay to check for existing shop
        delay(300)
        
        val existingShopId = documentUploadViewModel.getExistingShopId()
        if (existingShopId != null) {
            // Navigate directly to the existing shop
            onShopSelected(existingShopId)
        } else {
            // No existing shop, allow selection
            isCheckingExistingShop = false
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = CardWhite,
                shadowElevation = 2.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Select Shop",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                }
            }
        ,
        containerColor = BackgroundGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shared files info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BlueBtn.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BlueBtn.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = BlueBtn,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Column {
                        Text(
                            text = "$sharedFileCount ${if (sharedFileCount == 1) "file" else "files"} ready to upload",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Select a shop to continue",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Available Shops",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            // Show loading indicator while checking for existing shop
            if (isCheckingExistingShop) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardWhite
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BlueBtn,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Checking for existing orders...",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // GBlock Shop Card
            SelectableShopCard(
                shopName = ShopId.GBLOCK.displayName,
                isOpen = homeUiState.isGBlockShopOpen,
                isEnabled = !isCheckingExistingShop,
                pricePerPage = homeUiState.gblockPricePerPage,
                onSelectShop = { onShopSelected(ShopId.GBLOCK.name) }
            )

            // COS Shop Card
            SelectableShopCard(
                shopName = ShopId.COS.displayName,
                isOpen = homeUiState.isCosShopOpen,
                isEnabled = !isCheckingExistingShop,
                pricePerPage = homeUiState.cosPricePerPage,
                onSelectShop = { onShopSelected(ShopId.COS.name) }
            )

            // Info text
            if (!homeUiState.isGBlockShopOpen && !homeUiState.isCosShopOpen) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = "All shops are currently closed. Please try again later.",
                        modifier = Modifier.padding(16.dp),
                        color = ErrorRed,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableShopCard(
    shopName: String,
    isOpen: Boolean,
    isEnabled: Boolean = true,
    pricePerPage: PricePerPage,
    onSelectShop: () -> Unit
) {
    val canSelect = isOpen && isEnabled
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canSelect, onClick = onSelectShop),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        )) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with shop name and status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Shop icon
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isOpen) BlueBtn.copy(alpha = 0.12f) else Color(0xFFE5E7EB),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = null,
                                tint = if (isOpen) BlueBtn else TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Shop name
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = shopName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOpen) TextPrimary else TextSecondary
                        )

                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isOpen) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOpen) SuccessGreen else ErrorRed)
                                )
                                Text(
                                    text = if (isOpen) "Open Now" else "Closed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOpen) SuccessGreen else ErrorRed
                                )
                            }
                        }
                    }
                }

                // Arrow icon
                if (isOpen) {
                    Surface(
                        shape = CircleShape,
                        color = BlueBtn.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Select Shop",
                                tint = BlueBtn,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Divider
            HorizontalDivider(
                color = BorderGray.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Pricing information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // B&W pricing
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isOpen) Color(0xFFF3F4F6) else Color(0xFFE5E7EB).copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Black & White",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOpen) TextSecondary else TextSecondary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "₹${String.format("%.0f", pricePerPage.bw)}/page",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOpen) TextPrimary else TextSecondary.copy(alpha = 0.6f)
                        )
                    }
                }

                // Color pricing
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isOpen) Color(0xFFFEF3C7).copy(alpha = 0.6f) else Color(0xFFE5E7EB).copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Color",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOpen) Color(0xFFD97706) else TextSecondary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "₹${String.format("%.0f", pricePerPage.color)}/page",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOpen) Color(0xFFD97706) else TextSecondary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
