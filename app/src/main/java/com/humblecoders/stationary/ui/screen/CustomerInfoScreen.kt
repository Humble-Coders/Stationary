package com.humblecoders.stationary.ui.screen


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.stationary.ui.viewmodel.CustomerInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerInfoScreen(
    viewModel: CustomerInfoViewModel,
    onCustomerInfoSubmitted: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Print Shop",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = uiState.customerId,
            onValueChange = viewModel::updateCustomerId,
            label = { Text("Customer ID") },
            placeholder = { Text("Enter your name or ID") },
            isError = uiState.customerId.isNotEmpty() && !uiState.isValidId,
            supportingText = {
                if (uiState.customerId.isNotEmpty() && !uiState.isValidId) {
                    Text("ID must be at least 3 characters")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = uiState.customerPhone,
            onValueChange = viewModel::updateCustomerPhone,
            label = { Text("Phone Number") },
            placeholder = { Text("Enter 10-digit mobile number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = uiState.customerPhone.isNotEmpty() && !uiState.isValidPhone,
            supportingText = {
                if (uiState.customerPhone.isNotEmpty() && !uiState.isValidPhone) {
                    Text("Enter valid 10-digit mobile number")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = {
                val (customerId, customerPhone) = viewModel.getCustomerInfo()
                onCustomerInfoSubmitted(customerId, customerPhone)
            },
            enabled = viewModel.isFormValid(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Continue", fontSize = 16.sp)
        }
    }
}