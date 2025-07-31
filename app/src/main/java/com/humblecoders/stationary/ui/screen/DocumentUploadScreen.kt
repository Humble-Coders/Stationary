package com.humblecoders.stationary.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.stationary.ui.component.PrintSettingsCard
import com.humblecoders.stationary.ui.component.ShopClosedCard
import com.humblecoders.stationary.ui.viewmodel.DocumentUploadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentUploadScreen(
    viewModel: DocumentUploadViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPayment: (String, Double, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFile(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Document") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!uiState.isShopOpen) {
                ShopClosedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                return@Column
            }

            // File Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Select Document",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (uiState.selectedFile == null) {
                        Button(
                            onClick = { filePickerLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose PDF File")
                        }
                    } else {
                        Column {
                            Text("Selected: ${uiState.fileName}")
                            Text(
                                text = "Size: ${uiState.fileSize / 1024}KB | Pages: ${uiState.pageCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { filePickerLauncher.launch("application/pdf") },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Change File")
                            }
                        }
                    }
                }
            }

            // Print Settings
            if (uiState.selectedFile != null) {
                PrintSettingsCard(
                    settings = uiState.printSettings,
                    onSettingsChange = viewModel::updatePrintSettings,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Price Display
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Total: ₹${String.format("%.2f", uiState.calculatedPrice)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Action Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.submitOrderWithPayment { orderId ->
                                onNavigateToPayment(orderId, uiState.calculatedPrice, uiState.customerPhone)
                            }
                        },
                        enabled = !uiState.isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Upload & Pay Now")
                    }

                    OutlinedButton(
                        onClick = viewModel::submitOrderWithoutPayment,
                        enabled = !uiState.isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload Without Payment")
                    }

                    OutlinedButton(
                        onClick = viewModel::submitOrderWithoutSettings,
                        enabled = !uiState.isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload Without Settings")
                    }
                }
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}