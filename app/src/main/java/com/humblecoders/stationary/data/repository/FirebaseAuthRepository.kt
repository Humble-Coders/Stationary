package com.humblecoders.stationary.data.repository

import android.util.Log
import android.util.Patterns
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import android.content.Context
import android.content.Intent
import com.humblecoders.stationary.R
import com.google.firebase.auth.EmailAuthProvider

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val context: Context
) {

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    private suspend fun checkIfEmailExistsInEmailAuth(email: String?): Boolean {
        if (email == null) return false

        return try {
            val existingProfile = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email)
                .whereEqualTo("googleSignIn", false)
                .limit(1)
                .get()
                .await()

            val exists = existingProfile.documents.isNotEmpty()
            Log.d("AuthRepository", "Email $email exists in email/password auth: $exists")
            exists
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error checking email in email auth", e)
            false
        }
    }

    private suspend fun checkIfEmailExistsInGoogleAuth(email: String): Boolean {
        return try {
            val existingProfile = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email)
                .whereEqualTo("googleSignIn", true)
                .limit(1)
                .get()
                .await()

            val exists = existingProfile.documents.isNotEmpty()
            Log.d("AuthRepository", "Email $email exists in Google auth: $exists")
            exists
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error checking email in Google auth", e)
            false
        }
    }

    suspend fun handleGoogleSignInResult(data: Intent?): Result<Unit> {
        return try {
            Log.d("AuthRepository", "Processing Google Sign-In result")

            if (data == null) {
                Log.e("AuthRepository", "Google Sign-In data is null")
                return Result.failure(Exception("Google Sign-In was cancelled or failed"))
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            Log.d("AuthRepository", "Google account retrieved: ${account.email}")

            val emailExists = checkIfEmailExistsInEmailAuth(account.email)

            if (emailExists) {
                Log.d("AuthRepository", "Email ${account.email} already registered with email/password")
                return Result.failure(Exception("This email is already registered with email and password. Please sign in using your email and password instead."))
            }

            signInWithGoogle(account)
        } catch (e: ApiException) {
            Log.e("AuthRepository", "Google sign-in ApiException: ${e.statusCode}", e)
            Result.failure(Exception("Google sign-in failed: ${getGoogleSignInErrorMessage(e.statusCode)}"))
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google sign-in general exception", e)
            Result.failure(Exception("Google sign-in error: ${e.message}"))
        }
    }

    suspend fun createUserWithEmailAndPassword(name: String, email: String, password: String, phone: String = ""): Result<Unit> {
        return try {
            val trimmedEmail = email.trim()

            Log.d("AuthRepository", "Attempting to create user with email: '$trimmedEmail'")

            if (!isValidEmail(trimmedEmail)) {
                Log.e("AuthRepository", "Invalid email format: $trimmedEmail")
                return Result.failure(IllegalArgumentException("Invalid email format"))
            }

            val googleAccountExists = checkIfEmailExistsInGoogleAuth(trimmedEmail)
            if (googleAccountExists) {
                Log.d("AuthRepository", "Email $trimmedEmail already registered with Google")
                return Result.failure(Exception("This email is already registered with Google. Please sign in using Google instead."))
            }

            withContext(Dispatchers.IO) {
                val authResult = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                val userId = authResult.user?.uid ?: throw Exception("Failed to create user account")

                Log.d("AuthRepository", "User created successfully with UID: $userId")

                val encodedEmail = trimmedEmail

                val tempUserDoc = try {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(encodedEmail)
                        .get()
                        .await()
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Error checking for temp user: ${e.message}")
                    null
                }

                val userProfileData = hashMapOf(
                    "email" to trimmedEmail,
                    "encodedEmail" to encodedEmail,
                    "name" to name,
                    "phone" to phone,
                    "createdAt" to System.currentTimeMillis(),
                    "isTemporary" to false,
                    "googleSignIn" to false
                )

                if (tempUserDoc != null && tempUserDoc.exists()) {
                    Log.d("AuthRepository", "Temporary user document found, migrating data")
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(encodedEmail)
                        .delete()
                        .await()
                }

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .set(userProfileData)
                    .await()

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()

                firebaseAuth.currentUser?.updateProfile(profileUpdates)?.await()
                Log.d("AuthRepository", "User profile updated successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        return try {
            val currentUser = firebaseAuth.currentUser
                ?: return Result.failure(Exception("No authenticated user"))

            withContext(Dispatchers.IO) {
                val credential = EmailAuthProvider.getCredential(currentUser.email!!, password)
                currentUser.reauthenticate(credential).await()
                Log.d("AuthRepository", "Reauthentication successful")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Reauthentication failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("AuthRepository", "Creating Firebase credential")

                if (account.idToken == null) {
                    throw Exception("Google ID token is null. Check your configuration.")
                }

                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                Log.d("AuthRepository", "Firebase credential created, signing in...")

                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val user = authResult.user ?: throw Exception("Failed to get user from Google sign-in")

                Log.d("AuthRepository", "Firebase auth successful for user: ${user.uid}")

                val isNewUser = authResult.additionalUserInfo?.isNewUser == true
                Log.d("AuthRepository", "Is new user: $isNewUser")

                if (isNewUser) {
                    createGoogleUserProfile(user.uid, account)
                } else {
                    updateExistingUserWithGoogleData(user.uid, account)
                }

                Log.d("AuthRepository", "Google sign-in completed successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google sign-in error: ${e.message}", e)
            Result.failure(e)
        }
    }


    private fun getGoogleSignInErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            12501 -> "Google Sign-In was cancelled"
            12502 -> "Network error occurred"
            12500 -> "Internal error occurred"
            else -> "Google Sign-In failed (Code: $statusCode)"
        }
    }

    // In FirebaseAuthRepository.kt, update these methods:

    fun signOutGoogle() {
        try {
            googleSignInClient.signOut().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("AuthRepository", "Google sign out successful")
                } else {
                    Log.e("AuthRepository", "Google sign out failed: ${task.exception}")
                }
            }
            // Also revoke access to completely clear the account
            googleSignInClient.revokeAccess()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error during Google sign out", e)
        }
    }

    fun signOut() {
        try {
            // Sign out from Firebase first
            firebaseAuth.signOut()
            // Then sign out from Google
            signOutGoogle()
            Log.d("AuthRepository", "Complete sign out successful")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error during complete sign out", e)
        }
    }

    // Update the getGoogleSignInIntent method:
    fun getGoogleSignInIntent(): Intent {
        // Clear any existing Google Sign-In state
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d("AuthRepository", "Google state cleared before new sign-in")
        }
        return googleSignInClient.signInIntent
    }
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                firebaseAuth.sendPasswordResetEmail(email).await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val trimmedEmail = email.trim()
        return Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
    }

    fun getCurrentUser() = firebaseAuth.currentUser

    fun isUserLoggedIn() = firebaseAuth.currentUser != null

    private suspend fun createGoogleUserProfile(userId: String, account: GoogleSignInAccount) {
        try {
            val userProfileData = hashMapOf(
                "email" to (account.email ?: ""),
                "encodedEmail" to (account.email ?: ""),
                "name" to (account.displayName ?: ""),
                "phone" to "",
                "createdAt" to System.currentTimeMillis(),
                "isTemporary" to false,
                "googleSignIn" to true,
                "profilePictureUrl" to (account.photoUrl?.toString() ?: ""),
                "googleId" to (account.id ?: "")
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .set(userProfileData)
                .await()

            Log.d("AuthRepository", "Google user profile created successfully")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error creating Google user profile", e)
            throw e
        }
    }

    private suspend fun updateExistingUserWithGoogleData(userId: String, account: GoogleSignInAccount) {
        try {
            val updateData = hashMapOf<String, Any>(
                "googleSignIn" to true,
                "profilePictureUrl" to (account.photoUrl?.toString() ?: ""),
                "googleId" to (account.id ?: "")
            )

            val currentUser = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .await()

            if (currentUser.exists()) {
                val currentName = currentUser.getString("name") ?: ""
                if (currentName.isBlank() && !account.displayName.isNullOrBlank()) {
                    updateData["name"] = account.displayName!!
                }
            }

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update(updateData)
                .await()

            Log.d("AuthRepository", "Existing user updated with Google data")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error updating existing user with Google data", e)
        }
    }
}