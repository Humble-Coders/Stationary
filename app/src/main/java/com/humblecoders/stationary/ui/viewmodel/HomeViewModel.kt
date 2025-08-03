package com.humblecoders.stationary.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch


data class HomeUiState(
    val orders: List<PrintOrder> = emptyList(),
    val isShopOpen: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null,
    val customerId: String = "",
    val isCustomerIdSet: Boolean = false
)

class HomeViewModel(
    private val printOrderRepository: PrintOrderRepository,
    private val shopSettingsRepository: ShopSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeShopStatus()
        // Auto-observe orders when user is authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            _uiState.value = _uiState.value.copy(
                customerId = currentUser.uid,
                isCustomerIdSet = true,
                isLoading = true
            )
            observeUserOrders()
        }
    }



    private fun observeShopStatus() {
        viewModelScope.launch {
            shopSettingsRepository.observeShopStatus()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load shop status: ${e.message}"
                    )
                }
                .collect { isOpen ->
                    _uiState.value = _uiState.value.copy(
                        isShopOpen = isOpen,
                        error = null
                    )
                }
        }
    }

    private fun observeUserOrders() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) return

        val customerId = currentUser.uid

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, customerId = customerId)

            try {
                printOrderRepository.observeUserOrders(customerId)
                    .collect { orders ->
                        _uiState.value = _uiState.value.copy(
                            orders = orders,
                            error = null,
                            isLoading = false
                        )
                    }
            }catch (e: Exception) {
                // Only show error if we don't have existing orders
                val currentOrders = _uiState.value.orders
                Log.d("HomeViewModel","${_uiState.value.orders}")

                if (currentOrders.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to load orders: ${e.message}",
                        isLoading = false
                    )
                } else {
                    // Keep existing orders, just stop loading
                    _uiState.value = _uiState.value.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refreshOrders() {
        if (_uiState.value.customerId.isEmpty()) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        observeUserOrders()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getPaymentStatusDisplay(order: PrintOrder): String {
        return when (order.paymentStatus.toString()) {
            "PAID" -> "Paid"
            else -> "unknown"
        }
    }

    fun getOrderStatusDisplay(order: PrintOrder): String {
        return when (order.orderStatus.toString()) {
            "SUBMITTED" -> "Submitted"
            "QUEUED" -> "Queued"
            "PRINTED" -> "Printed"
            else -> "Unknown"
        }
    }

}