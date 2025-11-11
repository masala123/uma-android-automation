package com.steve1316.uma_android_automation.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/**
 * Helper class to provide easy access to settings from SQLite database.
 * This class provides a centralized way to access settings throughout the app.
 */
object SettingsHelper {
    private const val TAG = "SettingsHelper"
    @SuppressLint("StaticFieldLeak")
    private var settingsManager: SQLiteSettingsManager? = null

    /**
     * Initialize the settings helper with a context.
     * This should be called once during app initialization.
     */
    fun initialize(context: Context) {
        settingsManager = SQLiteSettingsManager(context)
        if (settingsManager?.initialize() == true) {
            Log.d(TAG, "Settings helper initialized successfully.")
        } else {
            Log.w(TAG, "Failed to initialize settings helper.")
        }
    }

    /**
     * Get a boolean setting value.
     * Throws exception if setting doesn't exist.
     */
    fun getBooleanSetting(category: String, key: String): Boolean {
        return settingsManager?.getBooleanSetting(category, key) 
            ?: throw RuntimeException("Setting not found: $category.$key")
    }

    /**
     * Get an integer setting value.
     * Throws exception if setting doesn't exist.
     */
    fun getIntSetting(category: String, key: String): Int {
        return settingsManager?.getIntSetting(category, key)
            ?: throw RuntimeException("Setting not found: $category.$key")
    }

    /**
     * Get a double setting value.
     * Throws exception if setting doesn't exist.
     */
    fun getDoubleSetting(category: String, key: String): Double {
        return settingsManager?.getDoubleSetting(category, key)
            ?: throw RuntimeException("Setting not found: $category.$key")
    }

    /**
     * Get a string setting value.
     * Throws exception if setting doesn't exist.
     */
    fun getStringSetting(category: String, key: String): String {
        return settingsManager?.getStringSetting(category, key)
            ?: throw RuntimeException("Setting not found: $category.$key")
    }

    /**
     * Get a string array setting value.
     * Throws exception if setting doesn't exist.
     */
    fun getStringArraySetting(category: String, key: String): List<String> {
        return settingsManager?.getStringArraySetting(category, key)
            ?: throw RuntimeException("Setting not found: $category.$key")
    }

    /**
     * Check if the settings manager is available.
     */
    fun isAvailable(): Boolean {
        return settingsManager != null && settingsManager?.isDatabaseAvailable() == true
    }
}

