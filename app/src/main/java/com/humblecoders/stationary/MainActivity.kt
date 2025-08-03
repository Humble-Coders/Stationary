package com.humblecoders.stationary

import StationaryTheme
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.humblecoders.stationary.data.repository.FirebaseAuthRepository
import com.humblecoders.stationary.data.repository.PrintOrderRepository
import com.humblecoders.stationary.data.repository.ProfileRepository
import com.humblecoders.stationary.data.repository.ShopSettingsRepository
import com.humblecoders.stationary.data.service.RazorpayService
import com.humblecoders.stationary.navigation.PrintShopNavigation
import com.humblecoders.stationary.navigation.Screen
import com.humblecoders.stationary.ui.viewmodel.*
import com.humblecoders.stationary.ui.viewmodel.auth.LoginViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.RegisterViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.ProfileViewModel
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import android.Manifest

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var authRepository: FirebaseAuthRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var printOrderRepository: PrintOrderRepository
    private lateinit var shopSettingsRepository: ShopSettingsRepository
    private lateinit var razorpayService: RazorpayService

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var registerViewModel: RegisterViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var documentUploadViewModel: DocumentUploadViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var customerInfoViewModel: CustomerInfoViewModel

    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData) {
        razorpayPaymentId?.let { paymentId ->
            paymentViewModel.handlePaymentSuccess(paymentId, paymentData)
        }
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        paymentViewModel.handlePaymentError(errorCode, response ?: "Payment failed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()
        initializeRepositories()
        initializeGoogleSignInLauncher()
        initializeViewModels()

        setContent {
            StationaryTheme {
                PrintShopApp()
            }
        }
    }

    private fun initializeFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun initializeRepositories() {
        authRepository = FirebaseAuthRepository(firebaseAuth, this)
        profileRepository = ProfileRepository(firebaseAuth, firestore, this)
        printOrderRepository = PrintOrderRepository(firestore, storage)
        shopSettingsRepository = ShopSettingsRepository(firestore)
        razorpayService = RazorpayService()
    }

    private fun initializeGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    loginViewModel.handleGoogleSignInResult(result.data)
                    registerViewModel.handleGoogleSignInResult(result.data)
                }
                RESULT_CANCELED -> {
                    loginViewModel.cancelGoogleSignIn()
                    registerViewModel.cancelGoogleSignIn()
                }
                else -> {
                    loginViewModel.cancelGoogleSignIn()
                    registerViewModel.cancelGoogleSignIn()
                }
            }
        }
    }

    private fun initializeViewModels() {
        loginViewModel = LoginViewModel(authRepository)
        registerViewModel = RegisterViewModel(authRepository)
        profileViewModel = ProfileViewModel(profileRepository, authRepository)
        mainViewModel = MainViewModel(shopSettingsRepository)
        homeViewModel = HomeViewModel(printOrderRepository, shopSettingsRepository)
        documentUploadViewModel = DocumentUploadViewModel(printOrderRepository, shopSettingsRepository)
        paymentViewModel = PaymentViewModel(printOrderRepository, razorpayService)
    }

    @Composable
    private fun PrintShopApp() {
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
            loginViewModel = loginViewModel,
            registerViewModel = registerViewModel,
            profileViewModel = profileViewModel,
            activity = this@MainActivity,
            googleSignInLauncher = googleSignInLauncher,
        )
    }
}