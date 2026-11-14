// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.storage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Reads metadata from SAF (`content://`) URIs.
 *
 * User-picked files are reached exclusively through the `ContentResolver`, so
 * no direct filesystem access is attempted and no storage permission is needed
 * on modern Android.
 */
object UriResolver {

    /** Display filename of [uri], or `null` when the provider reports none. */
    fun getFileName(context: Context, uri: Uri): String? {
        // Fast path: SAF document URIs encode the name as the last segment.
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            queryColumn(context, uri, OpenableColumns.DISPLAY_NAME)?.let { return it }
        }
        uri.lastPathSegment?.let { segment ->
            val decoded = runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }
                .getOrDefault(segment)
            val name = decoded.substringAfterLast('/')
            if (name.isNotBlank()) return name
        }
        return null
    }

    /** Size in bytes of [uri], or `0` when the provider does not report one. */
    fun getFileSize(context: Context, uri: Uri): Long {
        queryColumn(context, uri, OpenableColumns.SIZE)?.toLongOrNull()?.let { return it }
        // Fall back to opening the stream and measuring, which is exact but slow.
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                }
                total
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /** True when [uri] can be opened for writing. */
    fun isWritable(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wa")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * Copies [uri] into the app cache so a native tool can open it by path.
     * Returns `null` when the provider cannot be read.
     */
    suspend fun uriToTempFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val name = getFileName(context, uri) ?: "input.bin"
            val dir = File(context.cacheDir, "input").apply { mkdirs() }
            val target = File(dir, sanitize(name))
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                target
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes [source] into the SAF folder [folderUri] as [fileName].
     * Returns the created document URI, or `null` on failure.
     */
    suspend fun writeToSafFolder(
        context: Context,
        folderUri: Uri,
        fileName: String,
        source: File,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val docUri = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(context, folderUri)
                ?.createFile("application/octet-stream", fileName)
                ?.uri ?: return@withContext null

            context.contentResolver.openOutputStream(docUri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            docUri
        } catch (_: Exception) {
            null
        }
    }

    /** Writes raw [bytes] into the SAF folder [folderUri]. */
    suspend fun writeToSafFolder(
        context: Context,
        folderUri: Uri,
        fileName: String,
        bytes: ByteArray,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val docUri = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(context, folderUri)
                ?.createFile("application/octet-stream", fileName)
                ?.uri ?: return@withContext null
            context.contentResolver.openOutputStream(docUri)?.use { it.write(bytes) }
            docUri
        } catch (_: Exception) {
            null
        }
    }

    /** Copies a `content://` input into a real file, for tools needing a path. */
    suspend fun materialize(context: Context, source: File, target: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun queryColumn(context: Context, uri: Uri, column: String): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(column), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    /** Strips path separators so a provider-supplied name cannot escape cache. */
    internal fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\]"), "_").ifBlank { "input.bin" }

    /** Opens [uri] for reading, or `null`. */
    fun openInput(context: Context, uri: Uri): InputStream? =
        try { context.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
}
