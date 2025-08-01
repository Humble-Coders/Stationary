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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetPayment() {
        _uiState.value = PaymentUiState()
    }

    fun clearState() {
        _uiState.value = PaymentUiState()
        currentPaymentAmount = 0.0
    }


    private fun extractOrderIdFromPaymentData(paymentData: PaymentData): String {
        // Extract order ID from payment data if available
        return try {
            // PaymentData contains various fields, check if order_id is available
            paymentData.toString() // This might contain JSON data
            "" // Return empty if not found
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractAmountFromPaymentData(paymentData: PaymentData?): Double? {
        // Extract amount from payment data if available
        return null // Razorpay doesn't always return amount in payment data
    }


    private fun validatePaymentInputs(orderId: String, amount: Double, customerPhone: String): String? {
        return when {
            orderId.isEmpty() -> "Invalid order ID"
            amount <= 0 -> "Invalid amount"
            customerPhone.length != 10 -> "Invalid phone number"
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
                customerPhone = customerPhone
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
