package com.humblecoders.stationary.ui.viewmodel

// ui/viewmodel/HomeViewModel.kt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.model.PrintOrder
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    }

    fun setCustomerId(customerId: String) {
        _uiState.value = _uiState.value.copy(
            customerId = customerId,
            isCustomerIdSet = true
        )
        observeUserOrders()
    }

    private fun observeShopStatus() {
        viewModelScope.launch {
            try {
                shopSettingsRepository.observeShopStatus().collect { isOpen ->
                    _uiState.value = _uiState.value.copy(
                        isShopOpen = isOpen,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun observeUserOrders() {
        if (_uiState.value.customerId.isEmpty()) return

        viewModelScope.launch {
            try {
                printOrderRepository.observeUserOrders(_uiState.value.customerId).collect { orders ->
                    _uiState.value = _uiState.value.copy(
                        orders = orders,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}