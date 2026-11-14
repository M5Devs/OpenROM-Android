// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Result of an installation attempt. */
sealed class PluginInstallResult {
    data class Success(val manifest: PluginManifest) : PluginInstallResult()
    data class Failure(val error: String) : PluginInstallResult()
}

/** Hard limits that blunt zip-bomb and resource-exhaustion archives. */
data class PluginArchiveLimits(
    val maxEntries: Int = 4096,
    val maxTotalBytes: Long = 512L * 1024 * 1024,
    val maxEntryBytes: Long = 256L * 1024 * 1024,
)

/** One entry of the archive's central directory. */
internal data class ZipEntryInfo(
    val name: String,
    val uncompressedSize: Long,
    val unixMode: Int,
    val isDirectory: Boolean,
) {
    /** True for a Unix symlink (mode bits 0xA000). */
    val isSymlink: Boolean get() = (unixMode and 0xF000) == 0xA000
}

/**
 * Installs `.orplug` archives safely and atomically.
 *
 * Guarantees, identical to the desktop installer:
 *  * **Zip-Slip protection** — every entry must resolve inside the staging
 *    directory; absolute paths, `..` and drive letters are rejected.
 *  * **Symlink rejection** — the archive's central directory is parsed so a
 *    Unix symlink entry can be refused before anything is written.
 *  * **Zip-bomb limits** — entry count, per-entry and total uncompressed sizes
 *    are checked against [limits] using the central directory's declared sizes.
 *  * **Checksum before extraction** — a trusted SHA-256 is verified against the
 *    raw archive bytes before a single entry is unpacked.
 *  * **Atomic swap with rollback** — extraction happens in a staging directory
 *    on the same filesystem, so the final rename is atomic. The previous
 *    version is moved to a backup first and restored if the swap fails.
 */
class PluginInstaller(
    private val pluginsDir: File,
    private val coreVersion: SemVer,
    private val limits: PluginArchiveLimits = PluginArchiveLimits(),
    private val log: (String) -> Unit = {},
) {
    private val stagingRoot: File get() = File(pluginsDir, ".staging")
    private val backupRoot: File get() = File(pluginsDir, ".backup")

    /** Installs a plugin from raw archive bytes. */
    fun installFromBytes(
        bytes: ByteArray,
        expectedSha256: String? = null,
        expectedId: String? = null,
        expectedVersion: SemVer? = null,
    ): PluginInstallResult {
        // 1. Verify the archive against the trusted checksum BEFORE unpacking.
        if (!expectedSha256.isNullOrBlank()) {
            val actual = sha256Hex(bytes)
            if (actual != expectedSha256.trim().lowercase()) {
                return PluginInstallResult.Failure(
                    "Archive checksum mismatch (expected ${expectedSha256.trim()}, got $actual)."
                )
            }
        }

        var staging: File? = null
        try {
            val entries = try {
                parseCentralDirectory(bytes)
            } catch (e: Exception) {
                return PluginInstallResult.Failure("Not a valid .orplug (ZIP) archive: ${e.message}")
            }

            val entryError = validateEntries(entries)
            if (entryError != null) return PluginInstallResult.Failure(entryError)

            stagingRoot.mkdirs()
            staging = File(stagingRoot, "stage-${System.currentTimeMillis()}").apply { mkdirs() }

            val extractError = extractSafely(bytes, staging)
            if (extractError != null) return PluginInstallResult.Failure(extractError)

            // 2. Load and validate the manifest.
            val manifestFile = File(staging, "plugin.json")
            if (!manifestFile.exists()) {
                return PluginInstallResult.Failure(
                    "Archive does not contain a \"plugin.json\" manifest."
                )
            }

            val manifest = try {
                PluginManifest.fromJson(JSONObject(manifestFile.readText(Charsets.UTF_8)))
            } catch (e: Exception) {
                return PluginInstallResult.Failure("Malformed \"plugin.json\": ${e.message}")
            }

            val errors = manifest.validate()
            if (errors.isNotEmpty()) {
                return PluginInstallResult.Failure(
                    "Manifest validation failed: ${errors.joinToString("; ")}"
                )
            }

            // 3. Registry pinning (optional).
            if (expectedId != null && expectedId != manifest.id) {
                return PluginInstallResult.Failure(
                    "Plugin id mismatch: registry advertised \"$expectedId\" but archive " +
                        "declares \"${manifest.id}\"."
                )
            }
            if (expectedVersion != null && expectedVersion != manifest.version) {
                return PluginInstallResult.Failure(
                    "Plugin version mismatch: registry advertised $expectedVersion but " +
                        "archive declares ${manifest.version}."
                )
            }

            // 4. SemVer compatibility gate.
            manifest.minCoreVersion?.let { minCore ->
                if (coreVersion < minCore) {
                    return PluginInstallResult.Failure(
                        "Plugin \"${manifest.id}\" requires OpenROM core >= $minCore " +
                            "(this build is $coreVersion)."
                    )
                }
            }

            // 5. Verify every declared binary that is present on disk.
            val binaryError = verifyBinaries(staging, manifest)
            if (binaryError != null) return PluginInstallResult.Failure(binaryError)

            // 6. The host platform must actually have a runnable binary.
            if (manifest.execution.kind == PluginExecutionKind.process &&
                !manifest.supportsHostPlatform
            ) {
                return PluginInstallResult.Failure(
                    "Plugin \"${manifest.id}\" has no build for platform " +
                        "\"${currentPlatformKey()}\"."
                )
            }

            makeExecutable(staging, manifest)

            // 7. Atomic swap with rollback.
            val swapError = commit(staging, manifest.id)
            if (swapError != null) return PluginInstallResult.Failure(swapError)

            log("[PLUGIN] Installed \"${manifest.name}\" (${manifest.id}) v${manifest.version}")
            return PluginInstallResult.Success(manifest)
        } catch (e: Exception) {
            return PluginInstallResult.Failure("Installation failed: ${e.message}")
        } finally {
            // Staging was either renamed away or is removed here.
            staging?.deleteRecursively()
            stagingRoot.listFiles()?.takeIf { it.isEmpty() }?.let { stagingRoot.delete() }
        }
    }

    /** Installs from a local `.orplug` file. */
    fun installFromArchive(
        archive: File,
        expectedSha256: String? = null,
        expectedId: String? = null,
        expectedVersion: SemVer? = null,
    ): PluginInstallResult {
        if (!archive.exists()) {
            return PluginInstallResult.Failure("Plugin archive not found: ${archive.path}")
        }
        return installFromBytes(
            archive.readBytes(),
            expectedSha256 = expectedSha256,
            expectedId = expectedId,
            expectedVersion = expectedVersion,
        )
    }

    /**
     * Parses the ZIP central directory, which carries the declared sizes and
     * Unix file modes the streaming reader cannot report.
     */
    internal fun parseCentralDirectory(bytes: ByteArray): List<ZipEntryInfo> {
        val eocd = findEndOfCentralDirectory(bytes)
            ?: throw IllegalArgumentException("end of central directory not found")
        val entryCount = u16(bytes, eocd + 10)
        var offset = u32(bytes, eocd + 16).toInt()

        val result = ArrayList<ZipEntryInfo>(entryCount)
        repeat(entryCount) {
            if (offset + 46 > bytes.size) throw IllegalArgumentException("truncated central directory")
            if (u32(bytes, offset).toInt() != 0x02014B50) {
                throw IllegalArgumentException("bad central directory signature")
            }
            val versionMadeBy = u16(bytes, offset + 4)
            val nameLen = u16(bytes, offset + 28)
            val extraLen = u16(bytes, offset + 30)
            val commentLen = u16(bytes, offset + 32)
            val externalAttrs = u32(bytes, offset + 38)
            val uncompressed = u32(bytes, offset + 24)
            val nameBytes = bytes.copyOfRange(offset + 46, offset + 46 + nameLen)
            val name = String(nameBytes, Charsets.UTF_8)

            // High 16 bits of the external attributes hold the Unix mode when
            // the archive was produced on a Unix-like host (version made by 3).
            val unixMode = if ((versionMadeBy shr 8) == 3) (externalAttrs shr 16).toInt() else 0

            result.add(
                ZipEntryInfo(
                    name = name,
                    uncompressedSize = uncompressed,
                    unixMode = unixMode,
                    isDirectory = name.endsWith("/"),
                )
            )
            offset += 46 + nameLen + extraLen + commentLen
        }
        return result
    }

    private fun validateEntries(entries: List<ZipEntryInfo>): String? {
        if (entries.isEmpty()) return "Archive is empty."
        if (entries.size > limits.maxEntries) {
            return "Archive contains too many entries (limit ${limits.maxEntries})."
        }

        var total = 0L
        val root = stagingRoot.absoluteFile.normalize()
        for (entry in entries) {
            if (entry.name.contains('\u0000')) {
                return "Archive contains an entry with an illegal null byte."
            }
            if (entry.isSymlink) {
                return "Archive contains a symbolic link (\"${entry.name}\"); refusing to install."
            }
            // Zip-Slip guard: the resolved path must stay inside staging.
            val resolved = File(stagingRoot, entry.name).absoluteFile.normalize()
            if (resolved != root && !resolved.path.startsWith(root.path + File.separator)) {
                return "Security error: entry escapes the plugin directory (\"${entry.name}\")."
            }
            if (!entry.isDirectory) {
                if (entry.uncompressedSize > limits.maxEntryBytes) {
                    return "Archive entry \"${entry.name}\" exceeds the per-file size limit."
                }
                total += entry.uncompressedSize
                if (total > limits.maxTotalBytes) {
                    return "Archive exceeds the total uncompressed size limit."
                }
            }
        }
        return null
    }

    /**
     * Streams entries into [staging], re-checking containment and the size caps
     * as it writes. Returns an error message, or `null` on success.
     */
    private fun extractSafely(bytes: ByteArray, staging: File): String? {
        val root = staging.absoluteFile.normalize()
        var count = 0
        var total = 0L

        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > limits.maxEntries) {
                    return "Archive contains too many entries (limit ${limits.maxEntries})."
                }
                val name = entry.name
                if (name.contains('\u0000')) {
                    return "Archive contains an entry with an illegal null byte."
                }

                val resolved = File(staging, name).absoluteFile.normalize()
                if (resolved != root && !resolved.path.startsWith(root.path + File.separator)) {
                    return "Security error: entry escapes the plugin directory (\"$name\")."
                }

                if (entry.isDirectory) {
                    resolved.mkdirs()
                } else {
                    resolved.parentFile?.mkdirs()
                    val written = copyCapped(zip, resolved, limits.maxEntryBytes)
                        ?: return "Archive entry \"$name\" exceeds the per-file size limit."
                    total += written
                    if (total > limits.maxTotalBytes) {
                        resolved.delete()
                        return "Archive exceeds the total uncompressed size limit."
                    }
                }
                zip.closeEntry()
            }
        }
        return if (count == 0) "Archive is empty." else null
    }

    /** Copies at most [cap] bytes; returns `null` if the stream exceeds it. */
    private fun copyCapped(zip: ZipInputStream, target: File, cap: Long): Long? {
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            while (true) {
                val read = zip.read(buffer)
                if (read <= 0) break
                written += read
                if (written > cap) return null
                out.write(buffer, 0, read)
            }
            return written
        }
    }

    /** Verifies SHA-256 of every declared binary that exists in [staging]. */
    private fun verifyBinaries(staging: File, manifest: PluginManifest): String? {
        val root = staging.absoluteFile.normalize()
        manifest.execution.environments.forEach { (key, env) ->
            val rel = env.executable
            if (rel.isBlank()) return@forEach
            val binFile = File(staging, rel).absoluteFile.normalize()
            if (!binFile.path.startsWith(root.path + File.separator)) {
                return "Plugin binary \"$rel\" escapes the plugin directory."
            }
            if (!binFile.exists()) {
                // A missing build for another platform is fine; a missing host
                // build is caught separately by supportsHostPlatform.
                if (key == currentPlatformKey()) {
                    return "Plugin is missing its \"$key\" executable (\"$rel\")."
                }
                return@forEach
            }
            if (env.sha256.isBlank()) return@forEach
            val actual = sha256Hex(binFile.readBytes())
            if (actual != env.sha256.trim().lowercase()) {
                return "SHA-256 mismatch for \"$rel\" (expected ${env.sha256.trim()}, got $actual)."
            }
        }
        return null
    }

    /** Atomically promotes [staging] to `<pluginsDir>/<id>`, keeping a backup. */
    private fun commit(staging: File, pluginId: String): String? {
        val target = File(pluginsDir, pluginId)
        backupRoot.mkdirs()
        val backup = File(backupRoot, "$pluginId-${System.currentTimeMillis()}")

        var movedOldToBackup = false
        try {
            if (target.exists()) {
                if (!target.renameTo(backup)) {
                    return "Could not stage the existing install for replacement."
                }
                movedOldToBackup = true
            }
        } catch (e: Exception) {
            return "Could not stage the existing install for replacement: ${e.message}"
        }

        if (!staging.renameTo(target)) {
            if (movedOldToBackup) {
                target.deleteRecursively()
                if (backup.renameTo(target)) {
                    log("[PLUGIN] Rolled back to previous version of \"$pluginId\".")
                } else {
                    return "Swap failed and rollback failed. Previous version preserved at ${backup.path}."
                }
            }
            return "Could not activate the new plugin version."
        }

        if (movedOldToBackup) {
            backup.deleteRecursively()
            // Drop the backup container too, so a successful install leaves no
            // stray directories next to the plugin.
            backupRoot.listFiles()?.takeIf { it.isEmpty() }?.let { backupRoot.delete() }
        }
        return null
    }

    private fun makeExecutable(staging: File, manifest: PluginManifest) {
        manifest.execution.environments.forEach { (_, env) ->
            if (env.executable.isBlank()) return@forEach
            val bin = File(staging, env.executable)
            if (bin.exists()) bin.setExecutable(true, false)
        }
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int? {
        // The EOCD record is at least 22 bytes and may carry a trailing comment.
        val minOffset = maxOf(0, bytes.size - 22 - 0xFFFF)
        for (i in bytes.size - 22 downTo minOffset) {
            if (u32(bytes, i).toInt() == 0x06054B50) return i
        }
        return null
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    companion object {
        /** Lowercase hex SHA-256 of [bytes]. */
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** Lowercase hex SHA-256 of a file, streamed so large files fit memory. */
        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /** Drains a stream into a byte array. */
        internal fun readAll(input: java.io.InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}
