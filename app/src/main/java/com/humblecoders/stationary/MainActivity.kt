package com.humblecoders.stationary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.data.service.RazorpayService
import com.humblecoders.stationary.navigation.PrintShopNavigation
import com.humblecoders.stationary.ui.theme.StationaryTheme
import com.humblecoders.stationary.ui.viewmodel.*
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : ComponentActivity(),PaymentResultWithDataListener {

    private lateinit var printOrderRepository: PrintOrderRepository
    private lateinit var shopSettingsRepository: ShopSettingsRepository
    private lateinit var razorpayService: RazorpayService

    private lateinit var mainViewModel: MainViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var documentUploadViewModel: DocumentUploadViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var customerInfoViewModel: CustomerInfoViewModel

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData) {
        // Handle successful payment
        razorpayPaymentId?.let { paymentId ->
            paymentViewModel.handlePaymentSuccess(paymentId, paymentData)
        }
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        // Handle payment error
        paymentViewModel.handlePaymentError(errorCode, response ?: "Payment failed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeRepositories()
        initializeViewModels()

        setContent {
            StationaryTheme {
                PrintShopApp()
            }
        }
    }

    private fun initializeRepositories() {
        printOrderRepository = PrintOrderRepository()
        shopSettingsRepository = ShopSettingsRepository()
        razorpayService = RazorpayService()
    }

    private fun initializeViewModels() {
        mainViewModel = MainViewModel(shopSettingsRepository)
        homeViewModel = HomeViewModel(printOrderRepository, shopSettingsRepository)
        documentUploadViewModel = DocumentUploadViewModel(printOrderRepository, shopSettingsRepository)
        paymentViewModel = PaymentViewModel(printOrderRepository, razorpayService)
        customerInfoViewModel = CustomerInfoViewModel()
    }

    @Composable
    private fun PrintShopApp() {
        // Create NavController with proper context and lifecycle
        val navController = remember {
            NavHostController(this@MainActivity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
        }

        PrintShopNavigation(
            navController = navController,
            mainViewModel = mainViewModel,
            homeViewModel = homeViewModel,
            documentUploadViewModel = documentUploadViewModel,
            paymentViewModel = paymentViewModel,
            customerInfoViewModel = customerInfoViewModel,
            activity = this@MainActivity
        )
    }

}