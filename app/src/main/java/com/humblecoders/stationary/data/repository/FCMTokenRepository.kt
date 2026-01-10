package com.humblecoders.stationary.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FCMTokenRepository {
    companion object {
        private const val TAG = "FCMService"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun saveToken(token: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, cannot save token")
            return
        }

        try {
            withContext(Dispatchers.IO) {
                val tokenData = hashMapOf(
                    "fcmToken" to token,
                    "userId" to currentUser.uid,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )

                // Save token in users collection under fcmTokens subcollection
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("fcmTokens")
                    .document(token) // Use token as document ID to avoid duplicates
                    .set(tokenData)
                    .await()

                // Also save a reference in the user document for quick access
                // Use set with merge to create document if it doesn't exist
                firestore.collection("users")
                    .document(currentUser.uid)
                    .set(hashMapOf("latestFcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                    .await()

                Log.d(TAG, "FCM token saved successfully for user: ${currentUser.uid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token", e)
        }
    }

    suspend fun getLatestToken(userId: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                val userDoc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .await()

                if (userDoc.exists()) {
                    val data = userDoc.data
                    data?.get("latestFcmToken") as? String
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
            null
        }
    }
}

