package com.humblecoders.stationary.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.model.ShopId
import com.humblecoders.stationary.data.model.PricePerPage
import com.humblecoders.stationary.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

// Modern color palette
private val BlueBtn = Color(0xFF3B82F6)
private val BackgroundGray = Color(0xFFF9FAFB)
private val CardWhite = Color.White
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val BorderGray = Color(0xFFE5E7EB)
private val SuccessGreen = Color(0xFF10B981)
private val ErrorRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onNavigateToUpload: (String) -> Unit,
    onNavigateToOrderHistory: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val homeUiState by homeViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = homeUiState.isLoading,
        onRefresh = {
            coroutineScope.launch {
                homeViewModel.refreshOrders()
            }
        }
    )

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
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PrintQ",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Select a shop to start printing",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onNavigateToOrderHistory,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "Order History",
                                tint = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = TextSecondary
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
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shop cards section
                Text(
                    text = "Available Shops",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // GBlock Shop Card
                ShopCard(
                    shopName = ShopId.GBLOCK.displayName,
                    shopId = ShopId.GBLOCK.name,
                    isOpen = homeUiState.isGBlockShopOpen,
                    pricePerPage = homeUiState.gblockPricePerPage,
                    onSelectShop = { onNavigateToUpload(ShopId.GBLOCK.name) }
                )

                // COS Shop Card
                ShopCard(
                    shopName = ShopId.COS.displayName,
                    shopId = ShopId.COS.name,
                    isOpen = homeUiState.isCosShopOpen,
                    pricePerPage = homeUiState.cosPricePerPage,
                    onSelectShop = { onNavigateToUpload(ShopId.COS.name) }
                )

                // Loading indicator
                if (homeUiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = BlueBtn,
                            strokeWidth = 3.dp
                        )
                    }
                }

                // Error display
                homeUiState.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = homeUiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = CardWhite,
                contentColor = BlueBtn
            )
        }
    }
}

@Composable
private fun ShopCard(
    shopName: String,
    shopId: String,
    isOpen: Boolean,
    pricePerPage: PricePerPage,
    onSelectShop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isOpen, onClick = onSelectShop),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
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
                    // Shop icon - larger
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

                // Arrow icon - larger
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