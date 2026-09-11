package com.performance.tracker.model

/**
 * Data class representing an employee's performance against their monthly target,
 * with threshold monitoring indicators (flags if < 50% of target).
 */
data class EmployeeThresholdStatus(
    val employeeId: String = "",
    val employeeName: String = "",
    val branch: String = "",
    val zone: String = "",
    val salesManager: String = "",
    val department: String = "",
    val profileImage: String = "",
    val month: String = "",
    val monthlyTarget: Int = 0,
    val totalAchievedCards: Int = 0,
    val achievementPercentage: Float = 0.0f,
    val thresholdPercentage: Float = 50.0f,
    val isBelowThreshold: Boolean = false,
    val cardsNeededToMeetThreshold: Int = 0,
    val statusMessage: String = ""
)
