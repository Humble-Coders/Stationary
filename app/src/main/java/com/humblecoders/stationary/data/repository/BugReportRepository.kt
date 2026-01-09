package com.humblecoders.stationary.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.humblecoders.stationary.data.model.BugReport
import com.humblecoders.stationary.data.model.BugReportStatus
import kotlinx.coroutines.tasks.await
import java.util.*

class BugReportRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val tag = "BugReportRepository"
    private val bugReportsCollection = firestore.collection("bug_reports")
    private val storageRef = storage.reference.child("bug_reports")

    suspend fun submitBugReport(
        subject: String,
        description: String,
        screenshotUri: Uri?
    ): Result<String> {
        return try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            Log.d(tag, "Submitting bug report for user: ${currentUser.uid}")

            // Upload screenshot if provided
            var screenshotUrl = ""
            if (screenshotUri != null) {
                try {
                    Log.d(tag, "Uploading screenshot...")
                    Log.d(tag, "Screenshot URI: $screenshotUri")
                    val timestamp = System.currentTimeMillis()
                    val uniqueId = UUID.randomUUID().toString()
                    val fileName = "${timestamp}_${uniqueId}.jpg"
                    val screenshotRef = storageRef.child(fileName)

                    Log.d(tag, "Uploading to path: bug_reports/$fileName")
                    val uploadTask = screenshotRef.putFile(screenshotUri).await()
                    Log.d(tag, "✅ Screenshot upload task completed")
                    
                    // Get download URL from the reference after upload
                    screenshotUrl = screenshotRef.downloadUrl.await().toString()
                    Log.d(tag, "✅ Screenshot uploaded successfully: $screenshotUrl")
                } catch (e: Exception) {
                    Log.e(tag, "❌ Error uploading screenshot", e)
                    Log.e(tag, "Error type: ${e.javaClass.simpleName}")
                    Log.e(tag, "Error message: ${e.message}")
                    e.printStackTrace()
                    // Continue without screenshot if upload fails
                }
            } else {
                Log.d(tag, "No screenshot provided, skipping upload")
            }

            // Create bug report document
            val bugReportId = UUID.randomUUID().toString()
            val bugReport = BugReport(
                id = bugReportId,
                userId = currentUser.uid,
                userEmail = currentUser.email ?: "",
                subject = subject,
                description = description,
                screenshotUrl = screenshotUrl,
                status = BugReportStatus.PENDING
            )

            // Save to Firestore
            bugReportsCollection.document(bugReportId).set(bugReport.toFirestoreMap()).await()

            Log.d(tag, "Bug report submitted successfully with ID: $bugReportId")
            Result.success(bugReportId)
        } catch (e: Exception) {
            Log.e(tag, "Error submitting bug report", e)
            Result.failure(e)
        }
    }

    private fun BugReport.toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "userEmail" to userEmail,
            "subject" to subject,
            "description" to description,
            "screenshotUrl" to screenshotUrl,
            "status" to status.name,
            "createdAt" to createdAt
        )
    }
}

