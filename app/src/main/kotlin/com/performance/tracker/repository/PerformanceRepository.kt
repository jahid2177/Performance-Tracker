package com.performance.tracker.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.performance.tracker.model.EmployeeThresholdStatus
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.util.TargetUtils
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerformanceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        const val TAG = "PerformanceRepo"
        const val DEFAULT_THRESHOLD_PERCENT = 50.0f
    }

    // ========================================================
    // 👤 User Operations (Safe & Case-Insensitive)
    // ========================================================

    suspend fun getUser(employeeId: String): User? {
        return try {
            if (employeeId.isBlank()) return null
            val cleanId = employeeId.trim()
            
            val snapshot = db.collection("employees").document(cleanId).get().await()
            if (snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)
                if (user != null) {
                    return if (user.employeeId.isBlank()) user.copy(employeeId = snapshot.id) else user
                }
            }
            
            // Fallback: Query by employeeId field
            val querySnapshot = db.collection("employees").whereEqualTo("employeeId", cleanId).get().await()
            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents.first()
                val user = doc.toObject(User::class.java)
                if (user != null) {
                    return if (user.employeeId.isBlank()) user.copy(employeeId = doc.id) else user
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user $employeeId: ${e.message}")
            null 
        }
    }

    suspend fun registerUser(user: User) {
        val cleanUser = if (user.employeeId.isBlank()) user else user.copy(employeeId = user.employeeId.trim())
        db.collection("employees").document(cleanUser.employeeId).set(cleanUser, SetOptions.merge()).await()
    }

    suspend fun getAllApprovedEmployees(): List<User> {
        return try {
            val snapshot = db.collection("employees").get().await()
            snapshot.documents.mapNotNull { doc ->
                val user = doc.toObject(User::class.java) ?: return@mapNotNull null
                val effectiveUser = if (user.employeeId.isBlank()) user.copy(employeeId = doc.id) else user
                val isApproved = effectiveUser.status.isBlank() ||
                        effectiveUser.status.trim().equals("Approved", ignoreCase = true)
                if (isApproved) effectiveUser else null
            }.sortedBy { it.name.lowercase(Locale.ROOT) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading approved employees: ${e.message}")
            emptyList()
        }
    }

    // ========================================================
    // 📊 Performance Data Operations
    // ========================================================

    suspend fun submitPerformance(data: Performance) {
        val cleanEmpId = data.employeeId.trim()
        val cleanMonth = data.month.trim()
        val cleanAcNo = data.accountNo.trim()

        if (cleanEmpId.isNotEmpty() && cleanMonth.isNotEmpty()) {
            val existingSnapshot = db.collection("performance")
                .whereEqualTo("employeeId", cleanEmpId)
                .whereEqualTo("month", cleanMonth)
                .get().await()

            val existingDocs = existingSnapshot.documents

            if (data.limit.equals("NIL", ignoreCase = true) || cleanAcNo.equals("N/A", ignoreCase = true)) {
                if (existingDocs.isNotEmpty()) {
                    val alreadyHasNil = existingDocs.any { 
                        it.getString("limit")?.equals("NIL", ignoreCase = true) == true ||
                        it.getString("accountNo")?.equals("N/A", ignoreCase = true) == true
                    }
                    if (alreadyHasNil) return
                }
            } else if (cleanAcNo.isNotEmpty() && !cleanAcNo.equals("N/A", ignoreCase = true)) {
                // Delete existing NIL placeholder entries for this month when real accounts are being submitted
                for (doc in existingDocs) {
                    val limitVal = doc.getString("limit") ?: ""
                    val acVal = doc.getString("accountNo") ?: ""
                    if (limitVal.equals("NIL", ignoreCase = true) || acVal.equals("N/A", ignoreCase = true)) {
                        try {
                            db.collection("performance").document(doc.id).delete().await()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting NIL record: ${e.message}")
                        }
                    }
                }

                // Check if entry with same account number already exists for this employee and month
                val matchDoc = existingDocs.find { doc ->
                    val docAc = doc.getString("accountNo")?.trim() ?: ""
                    docAc.equals(cleanAcNo, ignoreCase = true)
                }

                if (matchDoc != null) {
                    // Update existing record instead of creating duplicate
                    db.collection("performance").document(matchDoc.id).set(data).await()
                    return
                }
            }
        }

        val docRef = db.collection("performance").document()
        docRef.set(data).await()
    }

    suspend fun getMyReports(employeeId: String): List<Performance> {
        return try {
            if (employeeId.isBlank()) return emptyList()
            val cleanId = employeeId.trim()
            val snapshot = db.collection("performance")
                .whereEqualTo("employeeId", cleanId)
                .get().await()
            var reports = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Performance::class.java)?.apply { id = doc.id }
            }

            if (reports.isEmpty()) {
                val allSnapshot = db.collection("performance").get().await()
                reports = allSnapshot.documents.mapNotNull { doc ->
                    val p = doc.toObject(Performance::class.java)?.apply { id = doc.id }
                    if (p != null && p.employeeId.trim().equals(cleanId, ignoreCase = true)) p else null
                }
            }
            reports
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching reports for $employeeId: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAllReports(): List<Performance> {
        return try {
            val snapshot = db.collection("performance").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Performance::class.java)?.apply { id = doc.id }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all reports: ${e.message}")
            emptyList()
        }
    }

    fun listenAllReports(onUpdate: (List<Performance>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("performance").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to all reports: ${error.message}")
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Performance::class.java)?.apply { id = doc.id }
            }.orEmpty()
            onUpdate(list)
        }
    }

    fun listenMyReports(employeeId: String, onUpdate: (List<Performance>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        val cleanId = employeeId.trim()
        return db.collection("performance").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to my reports: ${error.message}")
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                val p = doc.toObject(Performance::class.java)?.apply { id = doc.id }
                if (p != null && p.employeeId.trim().equals(cleanId, ignoreCase = true)) p else null
            }.orEmpty()
            onUpdate(list)
        }
    }

    // ========================================================
    // 🎯 Threshold Monitoring System
    // ========================================================

    /**
     * Evaluates a single employee's performance against their monthly target.
     * Flags employee if target > 0 and achievement percentage is strictly below the threshold (default 50%).
     */
    fun evaluateEmployeeThreshold(
        user: User,
        performances: List<Performance>,
        targetMonth: String = SimpleDateFormat("MMMM", Locale.US).format(Date()),
        thresholdPercent: Float = DEFAULT_THRESHOLD_PERCENT
    ): EmployeeThresholdStatus {
        val target = user.monthlyTarget
        val achieved = TargetUtils.countCards(performances, user.employeeId, targetMonth)
        val rate = TargetUtils.calculateAchievementRate(achieved, target)
        
        // Flag applies only if employee has a target and is target-eligible
        val isBelow = user.isTargetEligible && target > 0 && rate < thresholdPercent
        
        val thresholdRequiredCards = if (target > 0) Math.ceil(target * (thresholdPercent / 100.0)).toInt() else 0
        val cardsNeeded = if (isBelow) (thresholdRequiredCards - achieved).coerceAtLeast(0) else 0

        val message = when {
            !user.isTargetEligible -> "Management/Admin - No Target Required"
            target <= 0 -> "No Target Assigned"
            isBelow -> "⚠️ Below 50% Threshold (${String.format(Locale.US, "%.1f%%", rate)}) - Need $cardsNeeded more card(s) to reach 50%"
            rate >= 100f -> "🎉 Target Achieved (${String.format(Locale.US, "%.1f%%", rate)})"
            else -> "On Track (${String.format(Locale.US, "%.1f%%", rate)})"
        }

        return EmployeeThresholdStatus(
            employeeId = user.employeeId,
            employeeName = user.name,
            branch = user.branch,
            zone = user.zone,
            salesManager = user.salesManager,
            department = user.displayDepartment,
            profileImage = user.profileImage,
            month = targetMonth,
            monthlyTarget = target,
            totalAchievedCards = achieved,
            achievementPercentage = rate,
            thresholdPercentage = thresholdPercent,
            isBelowThreshold = isBelow,
            cardsNeededToMeetThreshold = cardsNeeded,
            statusMessage = message
        )
    }

    /**
     * Monitors and evaluates all active target-eligible employees for a specific month.
     */
    suspend fun monitorThresholds(
        targetMonth: String = SimpleDateFormat("MMMM", Locale.US).format(Date()),
        thresholdPercent: Float = DEFAULT_THRESHOLD_PERCENT
    ): List<EmployeeThresholdStatus> {
        val employees = getAllApprovedEmployees().filter { it.isTargetEligible }
        val allReports = getAllReports()
        return employees.map { user ->
            evaluateEmployeeThreshold(user, allReports, targetMonth, thresholdPercent)
        }
    }

    /**
     * Returns only employees flagged for underperforming (below 50% target threshold).
     */
    suspend fun getFlaggedUnderperformingEmployees(
        targetMonth: String = SimpleDateFormat("MMMM", Locale.US).format(Date()),
        thresholdPercent: Float = DEFAULT_THRESHOLD_PERCENT
    ): List<EmployeeThresholdStatus> {
        return monitorThresholds(targetMonth, thresholdPercent).filter { it.isBelowThreshold }
    }

    /**
     * Returns the threshold evaluation for a single employee ID.
     */
    suspend fun getEmployeeThresholdStatus(
        employeeId: String,
        targetMonth: String = SimpleDateFormat("MMMM", Locale.US).format(Date()),
        thresholdPercent: Float = DEFAULT_THRESHOLD_PERCENT
    ): EmployeeThresholdStatus? {
        val user = getUser(employeeId) ?: return null
        val reports = getMyReports(employeeId)
        return evaluateEmployeeThreshold(user, reports, targetMonth, thresholdPercent)
    }
}
