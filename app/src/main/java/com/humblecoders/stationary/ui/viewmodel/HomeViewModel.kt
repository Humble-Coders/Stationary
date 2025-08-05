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
import kotlinx.coroutines.delay

data class HomeUiState(
    val orders: List<PrintOrder> = emptyList(),
    val isShopOpen: Boolean = true,
    val isLoading: Boolean = false, // Changed default to false
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

    // Add auth state listener
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user != null && user.uid != _uiState.value.customerId) {
            Log.d("HomeViewModel", "Auth state changed, user: ${user.uid}")
            setCustomerId(user.uid)
        } else if (user == null) {
            Log.d("HomeViewModel", "User signed out, clearing data")
            clearUserData()
        }
    }

    init {
        observeShopStatus()

        // Add auth state listener
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)

        // Check if user is already signed in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("HomeViewModel", "User already signed in: ${currentUser.uid}")
            setCustomerId(currentUser.uid)
        }
    }

    fun setCustomerId(customerId: String) {
        if (customerId.isNotEmpty() && customerId != _uiState.value.customerId) {
            Log.d("HomeViewModel", "Setting customer ID: $customerId")
            _uiState.value = _uiState.value.copy(
                customerId = customerId,
                isCustomerIdSet = true
            )
            observeUserOrders()
        }
    }

    private fun clearUserData() {
        _uiState.value = _uiState.value.copy(
            orders = emptyList(),
            customerId = "",
            isCustomerIdSet = false,
            isLoading = false,
            error = null
        )
    }

    private fun observeShopStatus() {
        viewModelScope.launch {
            shopSettingsRepository.observeShopStatus()
                .catch { e ->
                    Log.e("HomeViewModel", "Error observing shop status", e)
                    _uiState.value = _uiState.value.copy(
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
        val customerId = _uiState.value.customerId
        if (customerId.isEmpty()) {
            Log.w("HomeViewModel", "Cannot observe orders: customerId is empty")
            return
        }

        viewModelScope.launch {
            try {
                // Add small delay to ensure Firebase Auth is fully initialized
                delay(500)

                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                Log.d("HomeViewModel", "Starting to observe orders for customer ID: $customerId")

                printOrderRepository.observeUserOrders(customerId)
                    .collect { orders ->
                        Log.d("HomeViewModel", "Received ${orders.size} orders")
                        _uiState.value = _uiState.value.copy(
                            orders = orders,
                            error = null,
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error observing user orders", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load orders: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun refreshOrders() {
        val customerId = _uiState.value.customerId
        if (customerId.isEmpty()) {
            Log.w("HomeViewModel", "Cannot refresh orders: customerId is empty")
            return
        }

        Log.d("HomeViewModel", "Refreshing orders for customer: $customerId")
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        observeUserOrders()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getPaymentStatusDisplay(order: PrintOrder): String {
        return when (order.paymentStatus.toString()) {
            "PAID" -> "Paid"
            "UNPAID" -> "Unpaid"
            "FAILED" -> "Failed"
            else -> "Unknown"
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

    override fun onCleared() {
        super.onCleared()
        // Remove auth state listener
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }
}