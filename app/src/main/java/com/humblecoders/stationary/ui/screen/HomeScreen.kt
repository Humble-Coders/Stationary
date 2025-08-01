package com.humblecoders.stationary.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Segment
import androidx.compose.material.icons.filled.Upload
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
import com.humblecoders.stationary.ui.component.OrderCard
import com.humblecoders.stationary.ui.component.ShopClosedCard
import com.humblecoders.stationary.ui.viewmodel.HomeViewModel
import com.humblecoders.stationary.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    homeViewModel: HomeViewModel,
    onNavigateToUpload: () -> Unit,
    onNavigateToOrderHistory : () -> Unit
) {
    val mainUiState by mainViewModel.uiState.collectAsState()
    val homeUiState by homeViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Filter orders to show only SUBMITTED and QUEUED
    val activeOrders = homeUiState.orders.filter { order ->
        order.orderStatus.toString() == "SUBMITTED" ||
                order.orderStatus.toString() == "QUEUED"
    }

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
            TopAppBar(
                title = { Text("Print Shop") },
                actions = {
                    if (homeUiState.orders.isNotEmpty()) {
                        IconButton(onClick = { homeViewModel.refreshOrders() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Orders"
                            )
                        }
                    }
                }
            )
        },
        // Replace the existing FloatingActionButton with this
        floatingActionButton = {
            if (homeUiState.isShopOpen) {
                var expanded by remember { mutableStateOf(false) }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // History FAB
                            SmallFloatingActionButton(
                                onClick = {
                                    expanded = false
                                    onNavigateToOrderHistory()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = "Order History")
                            }

                            // Upload FAB
                            SmallFloatingActionButton(
                                onClick = {
                                    expanded = false
                                    onNavigateToUpload()
                                }
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = "Upload Document")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FloatingActionButton(
                        onClick = { expanded = !expanded }
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.Close else Icons.Default.Segment,
                            contentDescription = if (expanded) "Close" else "More Options"
                        )
                    }
                }
            }
        }

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
                    .padding(16.dp)
            ) {
                if (!homeUiState.isShopOpen) {
                    ShopClosedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                // Status bar showing shop status
                AnimatedVisibility(
                    visible = homeUiState.isShopOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Green)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shop is open and ready to process your print orders",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (homeUiState.isLoading && activeOrders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading your orders...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (activeOrders.isEmpty()) {
                        EmptyOrdersPlaceholder(
                            isShopOpen = homeUiState.isShopOpen,
                            onNavigateToUpload = onNavigateToUpload,
                            hasCompletedOrders = homeUiState.orders.isNotEmpty() // Show if there are any orders (even completed ones)
                        )
                    } else {
                        OrdersListSection(
                            orders = activeOrders, // Use filtered orders
                            onOrderClick = { /* Handle order click */ },
                            viewModel = homeViewModel
                        )
                    }
                }

                // Error display
                homeUiState.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = homeUiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun EmptyOrdersPlaceholder(
    isShopOpen: Boolean,
    onNavigateToUpload: () -> Unit,
    hasCompletedOrders: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Print,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (hasCompletedOrders) "No Active Orders" else "No Print Orders Yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (hasCompletedOrders)
                    "You don't have any pending orders. Check your order history to see completed orders."
                else
                    "Your print history will appear here once you upload documents for printing.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (isShopOpen) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onNavigateToUpload,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Document")
                }
            }
        }
    }
}

@Composable
private fun OrdersListSection(
    orders: List<PrintOrder>,
    onOrderClick: (PrintOrder) -> Unit,
    viewModel: HomeViewModel
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Your Orders",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Text(
                text = "${orders.size} ${if (orders.size == 1) "order" else "orders"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = orders,
                key = { it.orderId }
            ) { order ->
                OrderCard(
                    order = order,
                    onClick = { onOrderClick(order) },
                    modifier = Modifier,
                    getPaymentStatusDisplay = viewModel::getPaymentStatusDisplay,
                    getOrderStatusDisplay = viewModel::getOrderStatusDisplay,
                )
            }
        }
    }
}

