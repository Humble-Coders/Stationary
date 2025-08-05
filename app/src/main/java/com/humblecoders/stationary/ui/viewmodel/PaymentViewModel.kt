package com.humblecoders.stationary.ui.viewmodel



import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.model.PaymentResult
import com.humblecoders.stationary.data.model.PaymentTransactionData
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.service.RazorpayService
import com.razorpay.PaymentData
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
    private val _currentPaymentInfo = MutableStateFlow<PaymentInfo?>(null)
    val currentPaymentInfo: StateFlow<PaymentInfo?> = _currentPaymentInfo.asStateFlow()

    fun setPaymentInfo(orderId: String, amount: Double, customerPhone: String) {
        _currentPaymentInfo.value = PaymentInfo(orderId, amount, customerPhone)
    }

    // Update the clearState method
    fun clearState() {
        _uiState.value = PaymentUiState()
        _currentPaymentInfo.value = null
        currentPaymentAmount = 0.0
    }
    fun resetPayment() {
        _uiState.value = PaymentUiState()
    }



    private fun validatePaymentInputs(orderId: String, amount: Double, customerPhone: String): String? {
        return when {
            orderId.isEmpty() -> "Invalid order ID"
            amount <= 0 -> "Invalid amount"
            else -> null
        }
    }




    // Update these methods in PaymentViewModel.kt


    fun handlePaymentError(errorCode: Int, errorMessage: String) {
        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            error = "Payment failed (Code: $errorCode): $errorMessage"
        )
    }

    // Add these properties to store payment details
    private var currentPaymentAmount: Double = 0.0

    fun processPayment(
        activity: Activity,
        orderId: String,
        amount: Double,
        customerPhone: String
    ) {
        val validationError = validatePaymentInputs(orderId, amount, customerPhone)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(error = validationError)
            return
        }

        try {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                error = null,
                orderId = orderId
            )

            // Store the amount for later use
            currentPaymentAmount = amount

            razorpayService.initiatePayment(
                activity = activity,
                amount = amount,
                orderId = orderId,
                customerPhone = customerPhone.ifEmpty { "0000000000" } // Use default if empty
            )

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = "Failed to start payment: ${e.message}"
            )
        }
    }
    // Update handlePaymentSuccess to use stored amount
    fun handlePaymentSuccess(razorpayPaymentId: String, razorpayPaymentData: PaymentData) {
        viewModelScope.launch {
            try {
                val currentOrderId = _uiState.value.orderId ?: return@launch

                val paymentDataObj = PaymentTransactionData(
                    razorpayOrderId = "",
                    razorpayPaymentId = razorpayPaymentId,
                    amount = currentPaymentAmount
                )

                printOrderRepository.updateOrderPayment(currentOrderId, paymentDataObj)

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isSuccess = true
                )

                // Clear state after short delay to show success message
                kotlinx.coroutines.delay(1500)
                clearState()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Payment verification failed: ${e.message}"
                )
            }
        }
    }

}

data class PaymentInfo(
    val orderId: String,
    val amount: Double,
    val customerPhone: String
)