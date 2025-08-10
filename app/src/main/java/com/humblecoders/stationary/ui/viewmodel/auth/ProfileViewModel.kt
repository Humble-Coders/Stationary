package com.humblecoders.stationary.ui.viewmodel.auth

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humblecoders.stationary.data.model.AccountDeletionState
import com.humblecoders.stationary.data.model.ProfileState
import com.humblecoders.stationary.data.model.ProfileUpdateState
import com.humblecoders.stationary.data.model.UserProfile
import com.humblecoders.stationary.data.repository.FirebaseAuthRepository
import com.humblecoders.stationary.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val tag = "ProfileViewModel"

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    private val _updateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)

    private val _deletionState = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private var isCurrentlyLoading = false
    private var signOutRequested = false

    init {
        Log.d(tag, "ProfileViewModel initialized - waiting for explicit load call")
    }

    fun loadUserProfile() {
        if (isCurrentlyLoading) {
            Log.d(tag, "Profile loading already in progress, skipping")
            return
        }

        viewModelScope.launch {
            try {
                isCurrentlyLoading = true
                _profileState.value = ProfileState.Loading
                Log.d(tag, "Loading user profile...")

                signOutRequested = false
                delay(300)

                val isSignedIn = withTimeout(5000) {
                    var authReady = profileRepository.isUserSignedIn()
                    var attempts = 0

                    while (!authReady && attempts < 15) {
                        delay(200)
                        authReady = profileRepository.isUserSignedIn()
                        attempts++
                        Log.d(tag, "Auth check attempt $attempts: $authReady")
                    }

                    authReady
                }

                if (!isSignedIn) {
                    Log.w(tag, "User not signed in after waiting")
                    _profileState.value = ProfileState.Error("Please sign in to view profile")
                    isCurrentlyLoading = false
                    return@launch
                }

                Log.d(tag, "User is signed in, loading profile...")

                val result = withTimeout(15000) {
                    profileRepository.getCurrentUserProfile()
                }

                result.fold(
                    onSuccess = { profile ->
                        Log.d(tag, "Profile loaded successfully: ${profile.name}")
                        _currentProfile.value = profile
                        _profileState.value = ProfileState.Success(profile)
                    },
                    onFailure = { exception ->
                        Log.e(tag, "Failed to load profile", exception)
                        _profileState.value = ProfileState.Error(
                            exception.message ?: "Failed to load profile"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error loading profile", e)
                _profileState.value = ProfileState.Error("Network timeout or unexpected error")
            } finally {
                isCurrentlyLoading = false
            }
        }
    }


    fun signOut() {
        signOutRequested = true
        Log.d(tag, "Sign out requested")

        viewModelScope.launch {
            try {
                _isSigningOut.value = true
                Log.d(tag, "Signing out user...")

                // First clear profile repository
                val profileResult = try {
                    withTimeout(5000) {
                        profileRepository.signOut()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Profile sign out error", e)
                    Result.failure(e)
                }

                // Then clear auth repository (this will also clear Google state)
                val authResult = try {
                    withTimeout(5000) {
                        authRepository.signOut()
                        Result.success(Unit)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Auth sign out error", e)
                    Result.failure(e)
                }

                // Clear all local state
                clearAllState()

                // Add small delay to ensure cleanup completes
                delay(500)

                Log.d(tag, "Sign out completed - Profile: ${profileResult.isSuccess}, Auth: ${authResult.isSuccess}")

            } catch (e: Exception) {
                Log.e(tag, "Error during sign out", e)
                clearAllState()
            } finally {
                _isSigningOut.value = false
                Log.d(tag, "Sign out process completed")
            }
        }
    }

    private fun clearAllState() {
        _currentProfile.value = null
        _selectedImageUri.value = null
        _profileState.value = ProfileState.Loading
        _updateState.value = ProfileUpdateState.Idle
        _deletionState.value = AccountDeletionState.Idle
        isCurrentlyLoading = false
        Log.d(tag, "All state cleared")
    }



    override fun onCleared() {
        super.onCleared()
        Log.d(tag, "ProfileViewModel cleared")
        isCurrentlyLoading = false
    }
}