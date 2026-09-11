package com.performance.tracker.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class EmployeeTarget(
    val employeeId: String = "",
    val employeeName: String = "",
    val month: String = "",
    val targetCards: Int = 0,
    val year: Int = 2026,
    val updatedBy: String = "",
    val updatedAt: Long = 0L
)
