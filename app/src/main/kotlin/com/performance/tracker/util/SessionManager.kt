package com.performance.tracker.util

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "performance_tracker_pref"
    
    // Active session keys (cleared on logout)
    private const val KEY_USER_ID = "logged_in_user_id"
    private const val KEY_USER_ROLE = "logged_in_user_role"
    private const val KEY_USER_NAME = "logged_in_user_name"

    // Persistent login credentials (preserved across logouts if remember is enabled)
    private const val KEY_SAVED_ID = "saved_login_id"
    private const val KEY_SAVED_PASSWORD = "saved_login_password"
    private const val KEY_REMEMBER_ME = "saved_remember_me"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_login_enabled"
    private const val KEY_USER_EMAIL = "logged_in_user_email"

    fun saveUser(context: Context, id: String, role: String, name: String, email: String = "") {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_ID, id)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserEmail(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun setUserEmail(context: Context, email: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getUserId(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_ID, "") ?: ""
    }

    fun getUserRole(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_ROLE, "USER") ?: "USER"
    }

    fun getUserName(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_NAME, "") ?: ""
    }

    // Save ID and Password for Login Screen
    fun saveCredentials(context: Context, id: String, pass: String, remember: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (remember) {
            prefs.edit()
                .putString(KEY_SAVED_ID, id)
                .putString(KEY_SAVED_PASSWORD, pass)
                .putBoolean(KEY_REMEMBER_ME, true)
                .apply()
        } else {
            prefs.edit()
                .remove(KEY_SAVED_ID)
                .remove(KEY_SAVED_PASSWORD)
                .putBoolean(KEY_REMEMBER_ME, false)
                .apply()
        }
    }

    fun getSavedId(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_SAVED_ID, "") ?: ""
    }

    fun getSavedPassword(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_SAVED_PASSWORD, "") ?: ""
    }

    fun isRememberMe(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_REMEMBER_ME, true)
    }

    fun clearCredentials(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_SAVED_ID)
            .remove(KEY_SAVED_PASSWORD)
            .putBoolean(KEY_REMEMBER_ME, false)
            .apply()
    }

    // Clears active session on logout but retains saved credentials if remember me was enabled
    fun clear(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_NAME)
            .apply()
    }
}
