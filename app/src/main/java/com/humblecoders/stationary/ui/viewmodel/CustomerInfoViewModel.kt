package com.humblecoders.stationary.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CustomerInfoUiState(
    val customerId: String = "",
    val customerPhone: String = "",
    val isValidId: Boolean = false,
    val isValidPhone: Boolean = false,
    val error: String? = null
)

class CustomerInfoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerInfoUiState())
    val uiState: StateFlow<CustomerInfoUiState> = _uiState.asStateFlow()

    fun updateCustomerId(id: String) {
        val isValid = id.trim().length >= 3
        _uiState.value = _uiState.value.copy(
            customerId = id.trim(),
            isValidId = isValid,
            error = null
        )
    }

    fun updateCustomerPhone(phone: String) {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val isValid = cleanPhone.length == 10

        _uiState.value = _uiState.value.copy(
            customerPhone = cleanPhone,
            isValidPhone = isValid,
            error = null
        )
    }

    fun isFormValid(): Boolean {
        return _uiState.value.isValidId && _uiState.value.isValidPhone
    }

    fun getCustomerInfo(): Pair<String, String> {
        return Pair(_uiState.value.customerId, _uiState.value.customerPhone)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}