package com.performance.tracker.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

interface ReportDao {
    fun insert(report: CachedReportEntity): Long
    fun insertAll(reports: List<CachedReportEntity>)
    fun update(report: CachedReportEntity)
    fun getAllReports(): List<CachedReportEntity>
    fun getReportsByEmployee(empId: String): List<CachedReportEntity>
    fun getPendingSyncReports(): List<CachedReportEntity>
    fun markAsSynced(localId: Long, firestoreId: String)
    fun clearSyncedReports()
    fun deleteByLocalId(localId: Long)
    fun deleteAllReports()
}

class ReportDaoImpl(private val dbHelper: SQLiteOpenHelper) : ReportDao {

    override fun insert(report: CachedReportEntity): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("firestoreId", report.firestoreId)
            put("employeeId", report.employeeId)
            put("employeeName", report.employeeName)
            put("branch", report.branch)
            put("zone", report.zone)
            put("salesManager", report.salesManager)
            put("month", report.month)
            put("applicantName", report.applicantName)
            put("accountNo", report.accountNo)
            put("limitVal", report.limit)
            put("timestamp", report.timestamp)
            put("syncStatus", report.syncStatus)
        }
        return db.insertWithOnConflict("cached_reports", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun insertAll(reports: List<CachedReportEntity>) {
        if (reports.isEmpty()) return
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (report in reports) {
                val values = ContentValues().apply {
                    put("firestoreId", report.firestoreId)
                    put("employeeId", report.employeeId)
                    put("employeeName", report.employeeName)
                    put("branch", report.branch)
                    put("zone", report.zone)
                    put("salesManager", report.salesManager)
                    put("month", report.month)
                    put("applicantName", report.applicantName)
                    put("accountNo", report.accountNo)
                    put("limitVal", report.limit)
                    put("timestamp", report.timestamp)
                    put("syncStatus", report.syncStatus)
                }
                db.insertWithOnConflict("cached_reports", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun update(report: CachedReportEntity) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("firestoreId", report.firestoreId)
            put("employeeId", report.employeeId)
            put("employeeName", report.employeeName)
            put("branch", report.branch)
            put("zone", report.zone)
            put("salesManager", report.salesManager)
            put("month", report.month)
            put("applicantName", report.applicantName)
            put("accountNo", report.accountNo)
            put("limitVal", report.limit)
            put("timestamp", report.timestamp)
            put("syncStatus", report.syncStatus)
        }
        db.update("cached_reports", values, "localId = ?", arrayOf(report.localId.toString()))
    }

    override fun getAllReports(): List<CachedReportEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM cached_reports ORDER BY timestamp DESC", null)
        return cursor.use { extractList(it) }
    }

    override fun getReportsByEmployee(empId: String): List<CachedReportEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM cached_reports WHERE employeeId = ? ORDER BY timestamp DESC", arrayOf(empId))
        return cursor.use { extractList(it) }
    }

    override fun getPendingSyncReports(): List<CachedReportEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM cached_reports WHERE syncStatus = 'PENDING_SYNC'", null)
        return cursor.use { extractList(it) }
    }

    override fun markAsSynced(localId: Long, firestoreId: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("syncStatus", "SYNCED")
            put("firestoreId", firestoreId)
        }
        db.update("cached_reports", values, "localId = ?", arrayOf(localId.toString()))
    }

    override fun clearSyncedReports() {
        val db = dbHelper.writableDatabase
        db.delete("cached_reports", "syncStatus = 'SYNCED'", null)
    }

    override fun deleteByLocalId(localId: Long) {
        val db = dbHelper.writableDatabase
        db.delete("cached_reports", "localId = ?", arrayOf(localId.toString()))
    }

    override fun deleteAllReports() {
        val db = dbHelper.writableDatabase
        db.delete("cached_reports", null, null)
    }

    private fun extractList(cursor: Cursor): List<CachedReportEntity> {
        val list = mutableListOf<CachedReportEntity>()
        val idIdx = cursor.getColumnIndex("localId")
        val fsIdIdx = cursor.getColumnIndex("firestoreId")
        val empIdIdx = cursor.getColumnIndex("employeeId")
        val empNameIdx = cursor.getColumnIndex("employeeName")
        val branchIdx = cursor.getColumnIndex("branch")
        val zoneIdx = cursor.getColumnIndex("zone")
        val salesMgrIdx = cursor.getColumnIndex("salesManager")
        val monthIdx = cursor.getColumnIndex("month")
        val appNameIdx = cursor.getColumnIndex("applicantName")
        val accNoIdx = cursor.getColumnIndex("accountNo")
        val limitIdx = cursor.getColumnIndex("limitVal")
        val timeIdx = cursor.getColumnIndex("timestamp")
        val syncIdx = cursor.getColumnIndex("syncStatus")

        while (cursor.moveToNext()) {
            list.add(
                CachedReportEntity(
                    localId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                    firestoreId = if (fsIdIdx >= 0) cursor.getString(fsIdIdx) ?: "" else "",
                    employeeId = if (empIdIdx >= 0) cursor.getString(empIdIdx) ?: "" else "",
                    employeeName = if (empNameIdx >= 0) cursor.getString(empNameIdx) ?: "" else "",
                    branch = if (branchIdx >= 0) cursor.getString(branchIdx) ?: "" else "",
                    zone = if (zoneIdx >= 0) cursor.getString(zoneIdx) ?: "" else "",
                    salesManager = if (salesMgrIdx >= 0) cursor.getString(salesMgrIdx) ?: "" else "",
                    month = if (monthIdx >= 0) cursor.getString(monthIdx) ?: "" else "",
                    applicantName = if (appNameIdx >= 0) cursor.getString(appNameIdx) ?: "" else "",
                    accountNo = if (accNoIdx >= 0) cursor.getString(accNoIdx) ?: "" else "",
                    limit = if (limitIdx >= 0) cursor.getString(limitIdx) ?: "" else "",
                    timestamp = if (timeIdx >= 0) cursor.getLong(timeIdx) else 0L,
                    syncStatus = if (syncIdx >= 0) cursor.getString(syncIdx) ?: "SYNCED" else "SYNCED"
                )
            )
        }
        return list
    }
}
