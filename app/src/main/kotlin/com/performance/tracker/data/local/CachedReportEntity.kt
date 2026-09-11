package com.performance.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.performance.tracker.model.Performance

@Entity(tableName = "cached_reports")
data class CachedReportEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0L,
    val firestoreId: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val branch: String = "",
    val zone: String = "",
    val salesManager: String = "",
    val month: String = "",
    val applicantName: String = "",
    val accountNo: String = "",
    val limit: String = "",
    val timestamp: Long = 0L,
    val syncStatus: String = "SYNCED" // "SYNCED", "PENDING_SYNC"
) {
    fun toPerformance(): Performance {
        return Performance(
            id = firestoreId,
            employeeId = employeeId,
            employeeName = employeeName,
            branch = branch,
            zone = zone,
            salesManager = salesManager,
            month = month,
            applicantName = applicantName,
            accountNo = accountNo,
            limit = limit,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromPerformance(p: Performance, syncStatus: String = "SYNCED"): CachedReportEntity {
            return CachedReportEntity(
                firestoreId = p.id,
                employeeId = p.employeeId,
                employeeName = p.employeeName,
                branch = p.branch,
                zone = p.zone,
                salesManager = p.salesManager,
                month = p.month,
                applicantName = p.applicantName,
                accountNo = p.accountNo,
                limit = p.limit,
                timestamp = if (p.timestamp > 0) p.timestamp else System.currentTimeMillis(),
                syncStatus = syncStatus
            )
        }
    }
}
