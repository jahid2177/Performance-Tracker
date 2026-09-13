package com.performance.tracker.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase private constructor(context: Context) {

    private val dbHelper = DatabaseHelper(context.applicationContext)
    private val reportDaoInstance: ReportDao = ReportDaoImpl(dbHelper)

    fun reportDao(): ReportDao = reportDaoInstance

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cached_reports (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT,
                    firestoreId TEXT,
                    employeeId TEXT,
                    employeeName TEXT,
                    branch TEXT,
                    zone TEXT,
                    salesManager TEXT,
                    month TEXT,
                    applicantName TEXT,
                    accountNo TEXT,
                    limitVal TEXT,
                    timestamp INTEGER,
                    syncStatus TEXT
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS cached_reports")
            onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "performance_tracker.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

