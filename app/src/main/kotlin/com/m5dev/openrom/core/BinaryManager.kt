// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Which tools are usable after [BinaryManager.ensureReady]. */
data class BinaryReadyResult(
    /** Tool names verified present and checksum-clean. */
    val ready: List<String>,
    /** Tool names not yet downloaded. */
    val missing: List<String>,
    /** Tool names present but failing their SHA-256 check. */
    val failed: List<String>,
) {
    val allReady: Boolean get() = missing.isEmpty() && failed.isEmpty()
}

/**
 * Owns the bundled native tools.
 *
 * Tools ship inside the APK at `assets/bins/arm64-v8a/`. On first launch each
 * is copied into `filesDir/bins/`, made executable and checksum-verified; any
 * tool absent from the APK is downloaded from SourceForge instead. A binary
 * that already matches its published SHA-256 is never rewritten.
 *
 * Binaries live only in app-internal storage — never on external storage.
 */
class BinaryManager(
    private val context: Context,
    private val manifest: BinsManifest,
) {

    /** Where extracted tools live. */
    val binDir: File = File(context.filesDir, "bins")

    /** Emits human-readable progress lines for the UI. */
    var onLog: (String) -> Unit = {}

    /** Emits 0.0–1.0 as tools complete. */
    var onProgress: (Double) -> Unit = {}

    private val assetDir = "bins/arm64-v8a"

    /**
     * Ensures every manifest tool is present, executable and verified.
     *
     * Idempotent: tools that already pass their checksum are left untouched.
     */
    suspend fun ensureReady(): BinaryReadyResult = withContext(Dispatchers.IO) {
        binDir.mkdirs()

        val ready = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val failed = mutableListOf<String>()

        val names = manifest.toolNames()
        names.forEachIndexed { index, name ->
            val tool = manifest.tools.getValue(name)
            val target = File(binDir, tool.fileName)

            val status = ensureTool(tool, target)
            when (status) {
                ToolStatus.READY -> ready.add(name)
                ToolStatus.MISSING -> missing.add(name)
                ToolStatus.FAILED -> failed.add(name)
            }
            onProgress((index + 1).toDouble() / names.size.coerceAtLeast(1))
            if (status != ToolStatus.READY) onLog("  \u2717 $name: ${status.name.lowercase()}")
        }

        // Narrow the detector to what is genuinely runnable right now.
        FormatDetector.availableToolsProvider = { ready.toSet() }

        BinaryReadyResult(ready, missing, failed)
    }

    /** Absolute path of [tool] once it is verified, else `null`. */
    fun getExecutablePath(tool: String): String? {
        val entry = manifest.tools[tool] ?: return null
        val file = File(binDir, entry.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /** The verified path if the file's checksum matches, else `null`. */
    fun verifiedExecutablePath(tool: String): String? {
        val entry = manifest.tools[tool] ?: return null
        val file = File(binDir, entry.fileName)
        if (!file.exists()) return null
        return if (PluginInstallerSha.matches(file, entry.sha256)) file.absolutePath else null
    }

    /** True when [file] is present and matches [sha256]. */
    internal fun matchesSha256(file: File, sha256: String): Boolean =
        file.exists() && PluginInstallerSha.matches(file, sha256)

    /** Deletes every extracted tool so the next launch re-fetches them. */
    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        binDir.deleteRecursively().also { binDir.mkdirs() }
    }

    private fun ensureTool(tool: BinTool, target: File): ToolStatus {
        // Never rewrite a binary that already passes its checksum.
        if (target.exists() && matchesSha256(target, tool.sha256)) {
            target.setExecutable(true, false)
            onLog("  \u2713 ${tool.name} (cached)")
            return ToolStatus.READY
        }

        // Prefer the copy bundled in the APK.
        if (extractFromAssets(tool, target)) {
            return if (matchesSha256(target, tool.sha256)) {
                target.setExecutable(true, false)
                onLog("  \u2713 ${tool.name} (extracted)")
                ToolStatus.READY
            } else {
                target.delete()
                ToolStatus.FAILED
            }
        }

        // Otherwise fetch from SourceForge.
        return when (download(tool, target)) {
            ToolStatus.READY -> {
                target.setExecutable(true, false)
                onLog("  \u2713 ${tool.name} (downloaded)")
                ToolStatus.READY
            }
            else -> ToolStatus.MISSING
        }
    }

    /** Copies `assets/bins/arm64-v8a/<file>` into [target] when present. */
    private fun extractFromAssets(tool: BinTool, target: File): Boolean {
        return try {
            context.assets.open("$assetDir/${tool.fileName}").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            // Not bundled — fall through to the network path.
            false
        }
    }

    /** Downloads [tool] from SourceForge and verifies its checksum. */
    private fun download(tool: BinTool, target: File): ToolStatus {
        val temp = File(target.parentFile, "${tool.fileName}.part")
        return try {
            onLog("  \u2193 ${tool.name} (${FormatDetector.humanBytes(tool.sizeBytes)})")
            var url = URL(tool.sourceForgeUrl)
            var redirects = 0
            var connection: HttpURLConnection
            while (true) {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "OpenROM-Android")
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: return ToolStatus.FAILED
                    connection.disconnect()
                    if (++redirects > 5) return ToolStatus.FAILED
                    url = URL(url, location)
                    continue
                }
                if (code != 200) return ToolStatus.FAILED
                break
            }

            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()

            if (!matchesSha256(temp, tool.sha256)) {
                temp.delete()
                return ToolStatus.FAILED
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            ToolStatus.READY
        } catch (e: Exception) {
            temp.delete()
            onLog("  \u2717 ${tool.name}: ${e.message}")
            ToolStatus.FAILED
        }
    }

    private enum class ToolStatus { READY, MISSING, FAILED }
}

/** Small bridge so [BinaryManager] can hash without depending on the plugin package. */
internal object PluginInstallerSha {
    fun matches(file: File, sha256: String): Boolean =
        file.exists() && com.m5dev.openrom.plugin.PluginInstaller.sha256Hex(file)
            .equals(sha256.trim().lowercase(), ignoreCase = true)
}
