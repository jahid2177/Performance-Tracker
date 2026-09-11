package com.performance.tracker.util

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.data.local.AppDatabase
import com.performance.tracker.data.local.CachedReportEntity
import com.performance.tracker.model.Performance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object OfflineSyncManager {

    private const val TAG = "OfflineSyncManager"
    private val isSyncing = AtomicBoolean(false)

    /**
     * Saves a newly created report to local Room database immediately.
     */
    fun saveReportLocally(
        context: Context,
        performance: Performance,
        isPendingSync: Boolean,
        onSaved: ((localId: Long) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val status = if (isPendingSync) "PENDING_SYNC" else "SYNCED"
                val entity = CachedReportEntity.fromPerformance(performance, status)
                val localId = db.reportDao().insert(entity)
                withContext(Dispatchers.Main) {
                    onSaved?.invoke(localId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving local report", e)
            }
        }
    }

    /**
     * Caches all downloaded Firestore reports into Room for offline viewing.
     */
    fun cacheFirestoreReports(context: Context, reports: List<Performance>) {
        if (reports.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val entities = reports.map { CachedReportEntity.fromPerformance(it, "SYNCED") }
                db.reportDao().clearSyncedReports()
                db.reportDao().insertAll(entities)
            } catch (e: Exception) {
                Log.e(TAG, "Error caching Firestore reports", e)
            }
        }
    }

    /**
     * Reads all locally cached reports from Room.
     */
    suspend fun getLocalReports(context: Context): List<Performance> {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                db.reportDao().getAllReports().map { it.toPerformance() }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching local reports", e)
                emptyList()
            }
        }
    }

    /**
     * Checks if there are pending offline reports and syncs them to Firestore.
     */
    fun syncPendingReports(context: Context, onComplete: ((syncedCount: Int) -> Unit)? = null) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            onComplete?.invoke(0)
            return
        }

        if (!isSyncing.compareAndSet(false, true)) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var syncedCount = 0
            try {
                val db = AppDatabase.getInstance(context)
                val pending = db.reportDao().getPendingSyncReports()

                if (pending.isNotEmpty()) {
                    val firestore = FirebaseFirestore.getInstance()
                    for (report in pending) {
                        try {
                            val performanceMap = hashMapOf(
                                "employeeId" to report.employeeId,
                                "employeeName" to report.employeeName,
                                "branch" to report.branch,
                                "zone" to report.zone,
                                "salesManager" to report.salesManager,
                                "month" to report.month,
                                "applicantName" to report.applicantName,
                                "accountNo" to report.accountNo,
                                "limit" to report.limit,
                                "timestamp" to report.timestamp
                            )

                            val docRef = if (report.firestoreId.isNotBlank()) {
                                firestore.collection("performance").document(report.firestoreId)
                            } else {
                                firestore.collection("performance").document()
                            }

                            docRef.set(performanceMap).addOnSuccessListener {
                                CoroutineScope(Dispatchers.IO).launch {
                                    db.reportDao().markAsSynced(report.localId, docRef.id)
                                }
                            }
                            syncedCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed syncing item ${report.localId}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during offline sync", e)
            } finally {
                isSyncing.set(false)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(syncedCount)
                }
            }
        }
    }
}
