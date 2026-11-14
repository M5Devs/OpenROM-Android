// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

/**
 * A single conversion request and its live state.
 *
 * Port of the desktop `ConversionJob`. [filepath] and [outputDir] hold either
 * an absolute filesystem path or a `content://` URI string, because Android
 * user files are reached through the Storage Access Framework rather than
 * direct file access.
 */
data class ConversionJob(
    val filepath: String,
    val outputDir: String,
    var targetFormat: String,
    val compression: String = "",
    val verify: Boolean = false,
    val forcePlatform: String? = null,
    val media: String? = null,
    val audioCodec: String? = null,
    var status: String = "pending",
    var progress: Double = 0.0,
    val logLines: MutableList<String> = mutableListOf(),
    var error: String? = null,
    var originalSizeBytes: Long = 0,
    var outputSizeBytes: Long = 0,
) {
    val isUriInput: Boolean get() = filepath.startsWith("content://")
    val isUriOutput: Boolean get() = outputDir.startsWith("content://")

    fun appendLog(line: String) {
        logLines.add(line)
    }
}
