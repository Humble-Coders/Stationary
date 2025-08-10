package com.humblecoders.stationary.data.model


data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: String = "", // Format: "yyyy-MM-dd"
    val profilePictureUrl: String = "",
    val isGoogleSignIn: Boolean = false,
    val createdAt: Long = 0L,
    val localImagePath: String = "" // For locally stored images
)

data class ProfileUpdateRequest(
    val name: String,
    val phone: String,
    val dateOfBirth: String
)

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val profile: UserProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

sealed class ProfileUpdateState {
    object Idle : ProfileUpdateState()
    object Loading : ProfileUpdateState()
    object Success : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}

sealed class AccountDeletionState {
    object Idle : AccountDeletionState()
    object Loading : AccountDeletionState()
    object ReauthenticationRequired : AccountDeletionState()
    object Success : AccountDeletionState()
    data class Error(val message: String) : AccountDeletionState()
}