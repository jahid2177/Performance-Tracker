package com.performance.tracker.model
import java.io.Serializable

data class ReportSummary(
    val employeeId: String,
    val employeeName: String,
    val branch: String,
    val month: String,
    val timestamp: Long,
    val totalRecords: Int
) : Serializable
