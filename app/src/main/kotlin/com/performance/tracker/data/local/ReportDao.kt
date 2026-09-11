package com.performance.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(report: CachedReportEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(reports: List<CachedReportEntity>)

    @Update
    fun update(report: CachedReportEntity)

    @Query("SELECT * FROM cached_reports ORDER BY timestamp DESC")
    fun getAllReports(): List<CachedReportEntity>

    @Query("SELECT * FROM cached_reports WHERE employeeId = :empId ORDER BY timestamp DESC")
    fun getReportsByEmployee(empId: String): List<CachedReportEntity>

    @Query("SELECT * FROM cached_reports WHERE syncStatus = 'PENDING_SYNC'")
    fun getPendingSyncReports(): List<CachedReportEntity>

    @Query("UPDATE cached_reports SET syncStatus = 'SYNCED', firestoreId = :firestoreId WHERE localId = :localId")
    fun markAsSynced(localId: Long, firestoreId: String)

    @Query("DELETE FROM cached_reports WHERE syncStatus = 'SYNCED'")
    fun clearSyncedReports()

    @Query("DELETE FROM cached_reports WHERE localId = :localId")
    fun deleteByLocalId(localId: Long)
}
