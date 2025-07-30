package com.humblecoders.stationary.ui.viewmodel



import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.model.PaymentData
import com.humblecoders.stationary.data.model.PaymentResult
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.service.RazorpayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentUiState(
    val isProcessing: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val orderId: String? = null
)

class PaymentViewModel(
    private val printOrderRepository: PrintOrderRepository,
    private val razorpayService: RazorpayService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    fun processPayment(
        activity: Activity,
        orderId: String,
        amount: Double,
        customerPhone: String
    ) {
        if (customerPhone.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Customer phone number is required")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isProcessing = true, error = null)

                val result = razorpayService.initiatePayment(
                    activity = activity,
                    amount = amount,
                    orderId = orderId,
                    customerPhone = customerPhone
                )

                when (result) {
                    is PaymentResult.Success -> {
                        val paymentData = PaymentData(
                            razorpayOrderId = result.orderId,
                            razorpayPaymentId = result.paymentId,
                            amount = result.amount
                        )

                        printOrderRepository.updateOrderPayment(orderId, paymentData)

                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isSuccess = true,
                            orderId = orderId
                        )
                    }

                    is PaymentResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            error = result.errorMessage
                        )
                    }
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Payment failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetPayment() {
        _uiState.value = PaymentUiState()
    }
}
