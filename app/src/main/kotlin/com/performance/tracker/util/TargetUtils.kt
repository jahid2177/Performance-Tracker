package com.performance.tracker.util

import android.graphics.Color
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.performance.tracker.model.EmployeeTarget
import com.performance.tracker.model.Performance
import java.util.Locale

object TargetUtils {

    fun calculateAchievementRate(achieved: Int, target: Int): Float {
        if (target <= 0) return 0f
        return (achieved.toFloat() / target.toFloat()) * 100f
    }

    fun formatAchievementRate(achieved: Int, target: Int): String {
        if (target <= 0) return "No Target"
        val rate = calculateAchievementRate(achieved, target)
        return String.format(Locale.US, "%.1f%%", rate)
    }

    fun getAchievementColor(achieved: Int, target: Int): Int {
        if (target <= 0) return Color.parseColor("#757575")
        val rate = calculateAchievementRate(achieved, target)
        return when {
            rate >= 100f -> Color.parseColor("#2E7D32") // Green
            rate >= 75f -> Color.parseColor("#1976D2")  // Blue
            rate >= 50f -> Color.parseColor("#F57C00")  // Orange
            else -> Color.parseColor("#D32F2F")         // Red
        }
    }

    fun isBelowThreshold(achieved: Int, target: Int, thresholdPercent: Float = 50.0f): Boolean {
        if (target <= 0) return false
        return calculateAchievementRate(achieved, target) < thresholdPercent
    }

    fun getThresholdWarningBadgeText(achieved: Int, target: Int): String {
        val rate = calculateAchievementRate(achieved, target)
        return "⚠️ <50% Alert (${String.format(Locale.US, "%.0f%%", rate)})"
    }

    fun countCards(reports: List<Performance>, employeeId: String, month: String? = null): Int {
        val userReports = reports.filter { it.employeeId.equals(employeeId, ignoreCase = true) }
        val filtered = if (!month.isNullOrBlank() && !month.equals("All", ignoreCase = true)) {
            userReports.filter { it.month.equals(month, ignoreCase = true) }
        } else {
            userReports
        }

        return filtered.count { !it.limit.equals("NIL", ignoreCase = true) }
    }

    fun saveTarget(
        employeeId: String,
        employeeName: String,
        month: String,
        targetCards: Int,
        yearlyTargetCards: Int = 0,
        adminName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val cleanMonth = if (month.isBlank()) "General" else month
        val docId = "${employeeId}_${cleanMonth.uppercase()}"

        val calculatedYearly = if (yearlyTargetCards > 0) yearlyTargetCards else (targetCards * 12)

        val targetObj = EmployeeTarget(
            employeeId = employeeId,
            employeeName = employeeName,
            month = cleanMonth,
            targetCards = targetCards,
            yearlyTargetCards = calculatedYearly,
            year = 2026,
            updatedBy = adminName,
            updatedAt = System.currentTimeMillis()
        )

        // Save to targets collection
        db.collection("targets").document(docId)
            .set(targetObj, SetOptions.merge())
            .addOnSuccessListener {
                // Also update user's monthlyTarget and yearlyTarget field in employees collection
                val userUpdate = mapOf(
                    "monthlyTarget" to targetCards,
                    "yearlyTarget" to calculatedYearly
                )
                db.collection("employees").document(employeeId)
                    .set(userUpdate, SetOptions.merge())
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener {
                        // Even if user doc update fails, targets doc succeeded
                        onSuccess()
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error)
            }
    }
}
