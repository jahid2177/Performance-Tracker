package com.performance.tracker.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.data.local.AppDatabase
import com.performance.tracker.data.local.CachedReportEntity
import com.performance.tracker.model.Performance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object OfflineSyncManager {

    private const val TAG = "OfflineSyncManager"
    private val isSyncing = AtomicBoolean(false)

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Synced(val syncedCount: Int, val timestamp: Long = System.currentTimeMillis()) : SyncState()
        data class Error(val error: String) : SyncState()
    }

    private val _syncState = MutableLiveData<SyncState>(SyncState.Idle)
    val syncState: LiveData<SyncState> = _syncState

    /**
     * Shows a visual indicator (Toast / Banner) notifying the user of successful sync.
     */
    fun showSyncSuccessNotification(context: Context, count: Int = 0) {
        Handler(Looper.getMainLooper()).post {
            val message = if (count > 0) {
                "✓ Local database successfully synced with Firestore ($count records)"
            } else {
                "✓ Local Room database is in sync with Firestore"
            }
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

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
    fun cacheFirestoreReports(context: Context, reports: List<Performance>, notifyUser: Boolean = false) {
        if (reports.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val entities = reports.map { CachedReportEntity.fromPerformance(it, "SYNCED") }
                db.reportDao().clearSyncedReports()
                db.reportDao().insertAll(entities)
                _syncState.postValue(SyncState.Synced(reports.size))
                if (notifyUser) {
                    showSyncSuccessNotification(context, reports.size)
                }
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
    fun syncPendingReports(context: Context, showIndicator: Boolean = true, onComplete: ((syncedCount: Int) -> Unit)? = null) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            onComplete?.invoke(0)
            return
        }

        if (!isSyncing.compareAndSet(false, true)) {
            return
        }

        _syncState.postValue(SyncState.Syncing)

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

                            docRef.set(performanceMap).await()
                            db.reportDao().markAsSynced(report.localId, docRef.id)
                            syncedCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed syncing item ${report.localId}", e)
                        }
                    }
                }

                _syncState.postValue(SyncState.Synced(syncedCount))
                if (syncedCount > 0 && showIndicator) {
                    showSyncSuccessNotification(context, syncedCount)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during offline sync", e)
                _syncState.postValue(SyncState.Error(e.message ?: "Sync error"))
            } finally {
                isSyncing.set(false)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(syncedCount)
                }
            }
        }
    }

    /**
     * Performs a full bi-directional sync (pushes pending Room items & pulls latest Firestore items to Room).
     */
    fun performFullSync(context: Context, showIndicator: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            if (showIndicator) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Offline: Using local Room database", Toast.LENGTH_SHORT).show()
                }
            }
            onComplete?.invoke()
            return
        }

        syncPendingReports(context, showIndicator = false) { pendingSynced ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val snapshot = FirebaseFirestore.getInstance().collection("performance").get().await()
                    val remoteReports = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Performance::class.java)?.apply { id = doc.id }
                    }
                    val db = AppDatabase.getInstance(context)
                    val entities = remoteReports.map { CachedReportEntity.fromPerformance(it, "SYNCED") }
                    db.reportDao().clearSyncedReports()
                    db.reportDao().insertAll(entities)

                    _syncState.postValue(SyncState.Synced(remoteReports.size + pendingSynced))
                    if (showIndicator) {
                        showSyncSuccessNotification(context, if (pendingSynced > 0) pendingSynced else remoteReports.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing Room cache", e)
                } finally {
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke()
                    }
                }
            }
        }
    }
}
