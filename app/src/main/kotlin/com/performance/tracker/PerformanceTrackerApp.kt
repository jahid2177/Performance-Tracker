package com.performance.tracker

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class PerformanceTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            Log.d("PerformanceTrackerApp", "Firebase initialized with automatic offline persistence.")
        } catch (e: Exception) {
            Log.e("PerformanceTrackerApp", "Error initializing Firebase settings: ${e.message}")
        }
    }
}
