// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Small typed wrapper over [SharedPreferences] for user choices that must
 * survive a restart: the output folder and the last target format.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** SAF tree URI of the chosen output folder, or `null` when unset. */
    var outputFolderUri: String?
        get() = prefs.getString(KEY_OUTPUT_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_OUTPUT_FOLDER, value).apply()

    /** Display name of the chosen output folder, for the UI. */
    var outputFolderName: String?
        get() = prefs.getString(KEY_OUTPUT_FOLDER_NAME, null)
        set(value) = prefs.edit().putString(KEY_OUTPUT_FOLDER_NAME, value).apply()

    /** Target format chosen last, restored on the next launch. */
    var lastTargetFormat: String?
        get() = prefs.getString(KEY_LAST_FORMAT, null)
        set(value) = prefs.edit().putString(KEY_LAST_FORMAT, value).apply()

    /** True once the tool binaries have been prepared at least once. */
    var binariesPrepared: Boolean
        get() = prefs.getBoolean(KEY_BINARIES_PREPARED, false)
        set(value) = prefs.edit().putBoolean(KEY_BINARIES_PREPARED, value).apply()

    fun outputFolder(): Uri? = outputFolderUri?.let { Uri.parse(it) }

    fun clearOutputFolder() {
        prefs.edit()
            .remove(KEY_OUTPUT_FOLDER)
            .remove(KEY_OUTPUT_FOLDER_NAME)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "openrom_settings"
        const val KEY_OUTPUT_FOLDER = "output_folder_uri"
        const val KEY_OUTPUT_FOLDER_NAME = "output_folder_name"
        const val KEY_LAST_FORMAT = "last_target_format"
        const val KEY_BINARIES_PREPARED = "binaries_prepared"
    }
}
