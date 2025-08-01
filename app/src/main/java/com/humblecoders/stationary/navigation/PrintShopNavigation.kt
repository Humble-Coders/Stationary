package com.humblecoders.stationary.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.humblecoders.stationary.data.repository.UserPreferencesRepository
import com.humblecoders.stationary.ui.screen.*
import com.humblecoders.stationary.ui.viewmodel.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object CustomerInfo : Screen("customer_info")
    object Home : Screen("home")
    object DocumentUpload : Screen("document_upload")
    object Payment : Screen("payment/{orderId}/{amount}/{customerPhone}") {
        fun createRoute(orderId: String, amount: Double, customerPhone: String): String {
            return "payment/$orderId/$amount/$customerPhone"
        }
    }
}

@Composable
fun PrintShopNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    homeViewModel: HomeViewModel,
    documentUploadViewModel: DocumentUploadViewModel,
    paymentViewModel: PaymentViewModel,
    customerInfoViewModel: CustomerInfoViewModel,
    activity: Activity,
    startDestination: String = Screen.CustomerInfo.route,
    userPreferencesRepository: UserPreferencesRepository // Add this parameter
) {
    val coroutineScope = rememberCoroutineScope() // Add this for launching coroutines

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.CustomerInfo.route) {
            CustomerInfoScreen(
                viewModel = customerInfoViewModel,
                onCustomerInfoSubmitted = { customerId, customerPhone ->
                    // Save customer info to preferences
                    coroutineScope.launch {
                        userPreferencesRepository.saveCustomerInfo(customerId, customerPhone)
                    }

                    homeViewModel.setCustomerId(customerId)
                    documentUploadViewModel.setCustomerInfo(customerId, customerPhone)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.CustomerInfo.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                mainViewModel = mainViewModel,
                homeViewModel = homeViewModel,
                onNavigateToUpload = {
                    navController.navigate(Screen.DocumentUpload.route)
                }
            )
        }

        composable(Screen.DocumentUpload.route) {
            DocumentUploadScreen(
                viewModel = documentUploadViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPayment = { orderId, amount, customerPhone ->
                    navController.navigate(
                        Screen.Payment.createRoute(orderId, amount, customerPhone)
                    )
                }
            )
        }

        composable(Screen.Payment.route) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            val amount = backStackEntry.arguments?.getString("amount")?.toDoubleOrNull() ?: 0.0
            val customerPhone = backStackEntry.arguments?.getString("customerPhone") ?: ""

            PaymentScreen(
                viewModel = paymentViewModel,
                orderId = orderId,
                amount = amount,
                customerPhone = customerPhone,
                activity = activity,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPaymentSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.DocumentUpload.route) { inclusive = true }
                    }
                }
            )
        }
    }
}