package com.humblecoders.stationary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.data.service.RazorpayService
import com.humblecoders.stationary.ui.screen.*
import com.humblecoders.stationary.ui.theme.StationaryTheme
import com.humblecoders.stationary.ui.viewmodel.*

class MainActivity : ComponentActivity() {

    private lateinit var printOrderRepository: PrintOrderRepository
    private lateinit var shopSettingsRepository: ShopSettingsRepository
    private lateinit var razorpayService: RazorpayService

    private lateinit var mainViewModel: MainViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var documentUploadViewModel: DocumentUploadViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var customerInfoViewModel: CustomerInfoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeRepositories()
        initializeViewModels()

        setContent {
            StationaryTheme  {
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
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "customer_info"
        ) {
            composable("customer_info") {
                CustomerInfoScreen(
                    viewModel = customerInfoViewModel,
                    onCustomerInfoSubmitted = { customerId, customerPhone ->
                        homeViewModel.setCustomerId(customerId)
                        documentUploadViewModel.setCustomerInfo(customerId, customerPhone)
                        navController.navigate("home") {
                            popUpTo("customer_info") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    mainViewModel = mainViewModel,
                    homeViewModel = homeViewModel,
                    onNavigateToUpload = {
                        navController.navigate("document_upload")
                    }
                )
            }

            composable("document_upload") {
                DocumentUploadScreen(
                    viewModel = documentUploadViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToPayment = { orderId, amount, customerPhone ->
                        navController.navigate("payment/$orderId/$amount/$customerPhone")
                    }
                )
            }

            composable("payment/{orderId}/{amount}/{customerPhone}") { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                val amount = backStackEntry.arguments?.getString("amount")?.toDoubleOrNull() ?: 0.0
                val customerPhone = backStackEntry.arguments?.getString("customerPhone") ?: ""

                PaymentScreen(
                    viewModel = paymentViewModel,
                    orderId = orderId,
                    amount = amount,
                    customerPhone = customerPhone,
                    activity = this@MainActivity,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onPaymentSuccess = {
                        navController.navigate("home") {
                            popUpTo("document_upload") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

