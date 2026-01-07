package com.humblecoders.stationary.navigation

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
    object DocumentUpload : Screen("document_upload/{shopId}") {
        fun createRoute(shopId: String) = "document_upload/$shopId"
    }
    object OrderHistory : Screen("order_history")
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

        composable(Screen.Home.route) {
            // Initialize ViewModels with current user
            LaunchedEffect(Unit) {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    homeViewModel.setCustomerId(currentUser.uid)
                    documentUploadViewModel.setCustomerInfo(currentUser.uid, "")
                }
            }

            HomeScreen(
                homeViewModel = homeViewModel,
                onNavigateToUpload = { shopId ->
                    navController.navigate(Screen.DocumentUpload.createRoute(shopId))
                },
                onNavigateToOrderHistory = {
                    navController.navigate(Screen.OrderHistory.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(
            route = Screen.DocumentUpload.route,
            arguments = listOf(
                navArgument("shopId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            DocumentUploadScreen(
                viewModel = documentUploadViewModel,
                paymentViewModel = paymentViewModel,
                activity = activity as ComponentActivity,
                shopId = shopId,
                onNavigateBack = {
                    navController.popBackStack()
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