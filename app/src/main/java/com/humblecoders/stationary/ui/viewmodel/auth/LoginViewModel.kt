package com.humblecoders.stationary.ui.viewmodel.auth

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.repository.FirebaseAuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel(private val repository: FirebaseAuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _userProfile = MutableStateFlow<Map<String, Any>?>(null)

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                Log.d("GoogleSignIn", "Handling Google Sign-In result, data: ${data != null}")

                val result = repository.handleGoogleSignInResult(data)
                Log.d("GoogleSignIn", "Repository result: ${result.isSuccess}")

                result.fold(
                    onSuccess = {
                        Log.d("GoogleSignIn", "Google Sign-In successful")
                        _loginState.value = LoginState.Success

                        repository.getCurrentUser()?.uid?.let { userId ->
                            Log.d("GoogleSignIn", "User ID: $userId")
                            fetchUserProfile(userId)
                        }
                    },
                    onFailure = { exception ->
                        Log.e("GoogleSignIn", "Google Sign-In failed: ${exception.message}", exception)

                        val errorMessage = when {
                            exception.message?.contains("already registered with email and password") == true ->
                                "This email is already registered. Please sign in using your email and password."
                            else -> exception.message ?: "Google Sign-In failed"
                        }

                        _loginState.value = LoginState.Error(errorMessage)
                    }
                )
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Unexpected error in handleGoogleSignInResult", e)
                _loginState.value = LoginState.Error(
                    "An unexpected error occurred: ${e.message}"
                )
            }
        }
    }


    fun cancelGoogleSignIn() {
        if (_loginState.value is LoginState.GoogleSignInLoading) {
            _loginState.value = LoginState.Idle
        }
    }

    private fun fetchUserProfile(userId: String) {
        viewModelScope.launch {
            try {
                val profileSnapshot = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .get()
                    .await()

                if (profileSnapshot.exists()) {
                    _userProfile.value = profileSnapshot.data
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String) {
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = repository.signInWithEmailAndPassword(email, password)

            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success

                    repository.getCurrentUser()?.uid?.let { userId ->
                        fetchUserProfile(userId)
                    }
                },
                onFailure = { _loginState.value = LoginState.Error(it.message ?: "Authentication failed") }
            )
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            val result = repository.resetPassword(email)

            result.fold(
                onSuccess = { _loginState.value = LoginState.PasswordResetSent },
                onFailure = { _loginState.value = LoginState.Error(it.message ?: "Failed to send reset email") }
            )
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    fun isUserLoggedIn() = repository.isUserLoggedIn()

    // Add this method to LoginViewModel:
    fun clearGoogleSignInState() {
        viewModelScope.launch {
            try {
                repository.signOutGoogle()
                Log.d("LoginViewModel", "Google state cleared")
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Error clearing Google state", e)
            }
        }
    }

    // Update startGoogleSignIn method:
    fun startGoogleSignIn(): Intent {
        Log.d("GoogleSignIn", "Starting Google Sign-In")
        _loginState.value = LoginState.GoogleSignInLoading

        viewModelScope.launch {
            delay(30000) // 30 seconds timeout
            if (_loginState.value is LoginState.GoogleSignInLoading) {
                Log.w("GoogleSignIn", "Google Sign-In timed out")
                _loginState.value = LoginState.Error("Google Sign-In timed out. Please try again.")
            }
        }

        // Clear any existing state before starting new sign-in
        clearGoogleSignInState()
        return repository.getGoogleSignInIntent()
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    object PasswordResetSent : LoginState()
    data class Error(val message: String) : LoginState()
    object GoogleSignInLoading : LoginState()
}