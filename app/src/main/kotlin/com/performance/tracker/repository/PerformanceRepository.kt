package com.performance.tracker.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import kotlinx.coroutines.tasks.await

class PerformanceRepository {
    private val db = FirebaseFirestore.getInstance()

    // নিরাপদ getUser ফাংশন
    suspend fun getUser(employeeId: String): User? {
        return try {
            if (employeeId.isEmpty()) return null
            
            val snapshot = db.collection("employees").document(employeeId).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("RepoError", "Error fetching user: ${e.message}")
            null 
        }
    }

    suspend fun registerUser(user: User) {
        db.collection("employees").document(user.employeeId).set(user).await()
    }

    suspend fun submitPerformance(data: Performance) {
        val docRef = db.collection("performance").document()
        // 🔥 আগে data.copy(id = ...) ছিল, কিন্তু আমাদের মডেলে 'id' নেই। তাই এটি সরাসরি সেভ হবে।
        docRef.set(data).await()
    }

    suspend fun getMyReports(employeeId: String): List<Performance> {
        return try {
            val snapshot = db.collection("performance")
                // 🔥 .whereEqualTo(id = empId) এরর ছিল, এখন ঠিক করা হয়েছে
                .whereEqualTo("employeeId", employeeId)
                .get().await()
            snapshot.toObjects(Performance::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllReports(): List<Performance> {
        return try {
            db.collection("performance").get().await().toObjects(Performance::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
