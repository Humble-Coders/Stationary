package com.humblecoders.stationary.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.humblecoders.stationary.SharedFilesData
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
    
    object ActiveOrders : Screen("active_orders")

    object ShopSelection : Screen("shop_selection")

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
    googleSignInLauncher: ActivityResultLauncher<Intent>,
    sharedFilesData: SharedFilesData? = null,
    onSharedFilesHandled: () -> Unit = {}
) {
    // Check if user is already logged in
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    // Track shared files to pass to DocumentUploadScreen
    var pendingSharedFiles by remember { mutableStateOf<List<Uri>?>(null) }
    // Flag to prevent double navigation to shop selection
    var hasNavigatedToShopSelection by remember { mutableStateOf(false) }

    // Navigate to shop selection when shared files are received and user is logged in
    LaunchedEffect(sharedFilesData) {
        if (sharedFilesData != null && sharedFilesData.uris.isNotEmpty()) {
            // Check current screen
            val currentRoute = navController.currentDestination?.route
            val isOnDocumentUpload = currentRoute?.startsWith("document_upload") == true
            val isOnShopSelection = currentRoute == Screen.ShopSelection.route
            
            // Always update pending files with the accumulated list
            pendingSharedFiles = sharedFilesData.uris
            
            if (isOnDocumentUpload || isOnShopSelection) {
                // Already on DocumentUploadScreen or ShopSelection, just update pending files
                // No navigation needed - the files will be processed when ready
            } else {
                // Need to navigate to shop selection
                hasNavigatedToShopSelection = false
                
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    // User is logged in, navigate to shop selection
                    hasNavigatedToShopSelection = true
                    navController.navigate(Screen.ShopSelection.route)
                }
                // If user not logged in, they'll need to login first
                // The HomeScreen LaunchedEffect will handle navigation after login
            }
        }
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

        composable(Screen.ActiveOrders.route) {
            ActiveOrdersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                homeViewModel = homeViewModel
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
            LaunchedEffect(Unit) {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    homeViewModel.setCustomerId(currentUser.uid)
                    documentUploadViewModel.setCustomerInfo(currentUser.uid, "")
                }
            }

            // Check if we have pending shared files after login (handles case when user was not logged in)
            LaunchedEffect(pendingSharedFiles, hasNavigatedToShopSelection) {
                if (pendingSharedFiles != null && 
                    !hasNavigatedToShopSelection && 
                    FirebaseAuth.getInstance().currentUser != null) {
                    hasNavigatedToShopSelection = true
                    navController.navigate(Screen.ShopSelection.route)
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
                },
                onNavigateToActiveOrders = {
                    navController.navigate(Screen.ActiveOrders.route)
                }
            )
        }

        // Shop Selection Screen for shared files
        composable(Screen.ShopSelection.route) {
            ShopSelectionScreen(
                homeViewModel = homeViewModel,
                sharedFileCount = pendingSharedFiles?.size ?: 0,
                onShopSelected = { shopId ->
                    // Reset flag after navigating to document upload
                    hasNavigatedToShopSelection = false
                    navController.navigate(Screen.DocumentUpload.createRoute(shopId)) {
                        popUpTo(Screen.ShopSelection.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    // Clear state and reset flag when user cancels
                    pendingSharedFiles = null
                    hasNavigatedToShopSelection = false
                    onSharedFilesHandled()
                    navController.popBackStack()
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
            
            // Capture shared files and clear them
            val filesToLoad = remember(pendingSharedFiles) { pendingSharedFiles }
            
            DocumentUploadScreen(
                viewModel = documentUploadViewModel,
                paymentViewModel = paymentViewModel,
                activity = activity as ComponentActivity,
                shopId = shopId,
                sharedFiles = filesToLoad,
                onSharedFilesProcessed = {
                    // Only clear local pending state, don't clear MainActivity's sharedFilesData
                    // This allows new files to accumulate while on this screen
                    pendingSharedFiles = null
                },
                onNavigateBack = {
                    // Clear shared files and reset flag when navigating back
                    pendingSharedFiles = null
                    hasNavigatedToShopSelection = false
                    onSharedFilesHandled()
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