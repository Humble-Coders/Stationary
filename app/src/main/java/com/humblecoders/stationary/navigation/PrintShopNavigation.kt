package com.humblecoders.stationary.navigation

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.FirebaseAuth
import com.humblecoders.stationary.ui.screen.*
import com.humblecoders.stationary.ui.screen.auth.LoginScreen
import com.humblecoders.stationary.ui.screen.auth.RegisterScreen
import com.humblecoders.stationary.ui.screen.auth.ProfileScreen
import com.humblecoders.stationary.ui.viewmodel.*
import com.humblecoders.stationary.ui.viewmodel.auth.LoginViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.RegisterViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.ProfileViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Profile : Screen("profile")
    object Home : Screen("home")
    object DocumentUpload : Screen("document_upload")
    object OrderHistory : Screen("order_history")
    object Payment : Screen("payment") // Simplified route

}

@Composable
fun PrintShopNavigation(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    documentUploadViewModel: DocumentUploadViewModel,
    paymentViewModel: PaymentViewModel,
    loginViewModel: LoginViewModel,
    registerViewModel: RegisterViewModel,
    profileViewModel: ProfileViewModel,
    activity: Activity,
    googleSignInLauncher: ActivityResultLauncher<Intent>
) {

    // Check if user is already logged in
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication Screens
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = loginViewModel,
                navController = navController,
                googleSignInLauncher = googleSignInLauncher
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = registerViewModel,
                navController = navController,
                googleSignInLauncher = googleSignInLauncher
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = profileViewModel,
                navController = navController,
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }


        // In PrintShopNavigation.kt, update the Home screen composable:

        composable(Screen.Home.route) {
            // Initialize HomeViewModel with current user when navigating to Home
            LaunchedEffect(Unit) {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    homeViewModel.setCustomerId(currentUser.uid)
                    documentUploadViewModel.setCustomerInfo(currentUser.uid, "")
                }
            }

            HomeScreen(
                homeViewModel = homeViewModel,
                onNavigateToUpload = {
                    navController.navigate(Screen.DocumentUpload.route)
                },
                onNavigateToOrderHistory = {
                    navController.navigate(Screen.OrderHistory.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
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
                    // Only navigate to payment for PDF files
                    if (amount > 0) {
                        paymentViewModel.setPaymentInfo(orderId, amount, customerPhone)
                        navController.navigate(Screen.Payment.route)
                    } else {
                        // For non-PDF files, go back to home
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(Screen.Payment.route) {
            // Get the payment info from the ViewModel instead of navigation arguments
            val paymentInfo = paymentViewModel.currentPaymentInfo.collectAsState().value

            if (paymentInfo != null) {
                PaymentScreen(
                    viewModel = paymentViewModel,
                    orderId = paymentInfo.orderId,
                    amount = paymentInfo.amount,
                    customerPhone = paymentInfo.customerPhone,
                    activity = activity,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onPaymentSuccess = {
                        documentUploadViewModel.clearState()
                        paymentViewModel.clearState()

                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.DocumentUpload.route) { inclusive = true }
                        }
                    }
                )
            } else {
                // Handle case where payment info is not available
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(Screen.OrderHistory.route) {
            OrderHistoryScreen(
                viewModel = homeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}