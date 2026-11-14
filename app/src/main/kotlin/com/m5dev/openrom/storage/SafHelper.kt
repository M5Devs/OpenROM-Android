// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * Storage Access Framework helpers.
 *
 * Android's pickers are launch-once-then-callback, so the launchers must be
 * registered during fragment creation. [register] does that; the `launch*`
 * methods then resolve the pending callback.
 */
class SafHelper(private val fragment: Fragment) {

    private var pendingFileCallback: ((Uri?) -> Unit)? = null
    private var pendingFolderCallback: ((Uri?) -> Unit)? = null

    private lateinit var filePicker: ActivityResultLauncher<Array<String>>
    private lateinit var folderPicker: ActivityResultLauncher<Uri?>

    /** Registers the picker launchers. Call from `Fragment.onCreate`. */
    fun register() {
        filePicker = fragment.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { persistPermission(fragment.requireContext(), it, write = false) }
            val callback = pendingFileCallback
            pendingFileCallback = null
            callback?.invoke(uri)
        }

        folderPicker = fragment.registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { persistPermission(fragment.requireContext(), it, write = true) }
            val callback = pendingFolderCallback
            pendingFolderCallback = null
            callback?.invoke(uri)
        }
    }

    /** Launches the file picker for a ROM input. */
    fun launchFilePicker(
        mimeTypes: Array<String> = arrayOf("*/*"),
        onResult: (Uri?) -> Unit,
    ) {
        pendingFileCallback = onResult
        filePicker.launch(mimeTypes)
    }

    /** Launches the folder picker for an output directory. */
    fun launchFolderPicker(onResult: (Uri?) -> Unit) {
        pendingFolderCallback = onResult
        folderPicker.launch(null)
    }

    companion object {
        /**
         * Takes a persistable read/write grant on [uri] so it survives a
         * process restart. Best-effort: providers may refuse, which only means
         * the grant does not outlive the session.
         */
        fun persistPermission(context: Context, uri: Uri, write: Boolean) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Non-persistable grant; the URI stays valid for this session.
            }
        }

        /** Releases a persisted grant, e.g. when the output folder changes. */
        fun releasePermission(context: Context, uri: Uri) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Never granted, or already released.
            }
        }

        /**
         * A `content://` URI string is not usable by a native tool; the caller
         * must copy it into app storage first. This reports whether that is
         * required for [uriString].
         */
        fun requiresMaterialization(uriString: String): Boolean =
            uriString.startsWith("content://")

        /** True when [uriString] points at a file the app can open directly. */
        fun isDirectFile(uriString: String): Boolean =
            uriString.startsWith("/") || uriString.startsWith("file://")
    }
}
