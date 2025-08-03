package com.humblecoders.stationary.navigation

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Profile : Screen("profile")
    object Home : Screen("home")
    object DocumentUpload : Screen("document_upload")
    object OrderHistory : Screen("order_history")
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
                },
                onAccountDeleted = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
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
                    documentUploadViewModel.clearState()
                    paymentViewModel.clearState()

                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.DocumentUpload.route) { inclusive = true }
                    }
                }
            )
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