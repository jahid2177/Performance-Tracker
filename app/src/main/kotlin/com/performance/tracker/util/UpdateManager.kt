package com.performance.tracker.util

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.performance.tracker.R

object UpdateManager {

    private const val TAG = "UpdateManager"

    private const val KEY_LATEST_VERSION_CODE = "app_latest_version_code"
    private const val KEY_LATEST_VERSION_NAME = "app_latest_version_name"
    private const val KEY_UPDATE_TITLE = "app_update_title"
    private const val KEY_UPDATE_MESSAGE = "app_update_message"
    private const val KEY_UPDATE_URL = "app_update_url"
    private const val KEY_FORCE_UPDATE = "app_force_update"

    fun checkForAppUpdate(activity: Activity, onNoUpdate: (() -> Unit)? = null) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(60) // 1 minute for responsive checking
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            val defaults = mapOf<String, Any>(
                KEY_LATEST_VERSION_CODE to 1L,
                KEY_LATEST_VERSION_NAME to "1.0",
                KEY_UPDATE_TITLE to "App Update Available",
                KEY_UPDATE_MESSAGE to "A new version of Employee Performance Tracker is available with enhanced features and improvements.",
                KEY_UPDATE_URL to "https://play.google.com",
                KEY_FORCE_UPDATE to false
            )
            remoteConfig.setDefaultsAsync(defaults)

            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (!activity.isFinishing && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed)) {
                    if (task.isSuccessful) {
                        val currentVersionCode = getCurrentVersionCode(activity)
                        val latestVersionCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE)
                        val latestVersionName = remoteConfig.getString(KEY_LATEST_VERSION_NAME)
                        val title = remoteConfig.getString(KEY_UPDATE_TITLE).ifBlank { "Update Available" }
                        val message = remoteConfig.getString(KEY_UPDATE_MESSAGE).ifBlank {
                            "A new version ($latestVersionName) is ready for download."
                        }
                        val updateUrl = remoteConfig.getString(KEY_UPDATE_URL).ifBlank { "https://play.google.com" }
                        val isForceUpdate = remoteConfig.getBoolean(KEY_FORCE_UPDATE)

                        if (latestVersionCode > currentVersionCode) {
                            showUpdateDialog(activity, title, message, latestVersionName, updateUrl, isForceUpdate)
                        } else {
                            onNoUpdate?.invoke()
                        }
                    } else {
                        Log.w(TAG, "Remote config fetch failed: ${task.exception?.message}")
                        onNoUpdate?.invoke()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update", e)
            onNoUpdate?.invoke()
        }
    }

    private fun getCurrentVersionCode(activity: Activity): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    private fun showUpdateDialog(
        activity: Activity,
        title: String,
        message: String,
        versionName: String,
        updateUrl: String,
        isForceUpdate: Boolean
    ) {
        try {
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
            val tvTitle = dialogView.findViewById<TextView>(R.id.tvUpdateTitle)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tvUpdateMessage)
            val tvVersion = dialogView.findViewById<TextView>(R.id.tvUpdateVersion)
            val btnUpdate = dialogView.findViewById<Button>(R.id.btnUpdateNow)
            val btnLater = dialogView.findViewById<Button>(R.id.btnUpdateLater)

            tvTitle.text = title
            tvMessage.text = message
            tvVersion.text = "New Version: v$versionName"

            val dialog = AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(!isForceUpdate)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnUpdate.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening update url", e)
                }
                if (!isForceUpdate) {
                    dialog.dismiss()
                }
            }

            if (isForceUpdate) {
                btnLater.visibility = android.view.View.GONE
            } else {
                btnLater.setOnClickListener {
                    dialog.dismiss()
                }
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing update dialog", e)
        }
    }
}
