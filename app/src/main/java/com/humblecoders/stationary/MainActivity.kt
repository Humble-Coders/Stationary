package com.humblecoders.stationary

import StationaryTheme
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.humblecoders.stationary.ui.viewmodel.*
import com.humblecoders.stationary.ui.viewmodel.auth.LoginViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.ProfileViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.RegisterViewModel
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var authRepository: FirebaseAuthRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var printOrderRepository: PrintOrderRepository
    private lateinit var shopSettingsRepository: ShopSettingsRepository

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var registerViewModel: RegisterViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var documentUploadViewModel: DocumentUploadViewModel
    private lateinit var paymentViewModel: PaymentViewModel

    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    lateinit var razorpayService: RazorpayService
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()
        initializeRepositories()
        initializeGoogleSignInLauncher()
        initializeRazorpayService()
        initializeViewModels()
        logAuthState()

        setContent {
            StationaryTheme {
                PrintShopApp()
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        Log.d("MainActivity", "Payment success: $razorpayPaymentId")

        paymentData?.let { data ->
            Log.d("MainActivity", "Payment data: ${data.data}")

            try {
                val dataJson = JSONObject(data.data as Map<*, *>)

                Log.d("MainActivity", "Order ID: ${dataJson.optString("razorpay_order_id")}")
                Log.d("MainActivity", "Payment ID: ${dataJson.optString("razorpay_payment_id")}")
                Log.d("MainActivity", "Signature: ${dataJson.optString("razorpay_signature")}")

            } catch (e: Exception) {
                Log.e("MainActivity", "JSON error", e)
            }

        }

        // RazorpayService callback will handle the actual processing
    }

    override fun onPaymentError(errorCode: Int, errorMessage: String?, paymentData: PaymentData?) {
        Log.e("MainActivity", "Payment error: $errorCode - $errorMessage")

        paymentData?.let { data ->
            Log.e("MainActivity", "Error data: ${data.data}")
        }

        // RazorpayService callback will handle the error
    }

    private fun initializeFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun logAuthState() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("MainActivity", "User authenticated: ${currentUser.uid}")
            Log.d("MainActivity", "Email: ${currentUser.email}")
        } else {
            Log.e("MainActivity", "NO USER AUTHENTICATED!")
        }
    }

    private fun initializeRepositories() {
        authRepository = FirebaseAuthRepository(firebaseAuth, this)
        profileRepository = ProfileRepository(firebaseAuth, firestore, this)
        printOrderRepository = PrintOrderRepository(firestore, storage)
        shopSettingsRepository = ShopSettingsRepository(firestore)
    }

    private fun initializeRazorpayService() {
        razorpayService = RazorpayService(this)
    }

    private fun initializeGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("MainActivity", "Google Sign-In result: ${result.resultCode}")

            when (result.resultCode) {
                RESULT_OK -> {
                    Log.d("MainActivity", "Google Sign-In OK, processing result")
                    loginViewModel.handleGoogleSignInResult(result.data)
                    registerViewModel.handleGoogleSignInResult(result.data)
                }
                RESULT_CANCELED -> {
                    Log.d("MainActivity", "Google Sign-In cancelled by user")
                    loginViewModel.cancelGoogleSignIn()
                    registerViewModel.cancelGoogleSignIn()
                    loginViewModel.clearGoogleSignInState()
                    registerViewModel.clearGoogleSignInState()
                }
                else -> {
                    Log.d("MainActivity", "Google Sign-In failed with code: ${result.resultCode}")
                    loginViewModel.cancelGoogleSignIn()
                    registerViewModel.cancelGoogleSignIn()
                    loginViewModel.clearGoogleSignInState()
                    registerViewModel.clearGoogleSignInState()
                }
            }
        }
    }

    private fun initializeViewModels() {
        loginViewModel = LoginViewModel(authRepository)
        registerViewModel = RegisterViewModel(authRepository)
        profileViewModel = ProfileViewModel(profileRepository, authRepository)
        homeViewModel = HomeViewModel(printOrderRepository, shopSettingsRepository)
        documentUploadViewModel = DocumentUploadViewModel(printOrderRepository, shopSettingsRepository)
        paymentViewModel = PaymentViewModel()
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