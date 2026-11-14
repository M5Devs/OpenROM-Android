// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An installed plugin together with its on-disk location. */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val directory: File,
) {
    val verified: Boolean get() = manifest.publisher.verified
}

/** One catalogue row from the OpenROM Extension Store. */
data class StoreListing(
    val id: String,
    val name: String,
    val version: String,
    val minCoreVersion: String?,
    val publisherName: String,
    val publisherVerified: Boolean,
    val category: String,
    val description: String,
    val routes: List<String>,
    val downloadUrl: String,
    val sha256: String,
    val downloads: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): StoreListing = StoreListing(
            id = json.optString("id"),
            name = json.optString("name"),
            version = json.optString("version"),
            minCoreVersion = json.optString("minCoreVersion").ifBlank { null },
            publisherName = json.optString("publisherName", json.optString("publisherId")),
            publisherVerified = json.optBoolean("publisherVerified", false),
            category = json.optString("category", "Utility"),
            description = json.optString("description"),
            routes = json.optJSONArray("routes")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).ifBlank { null } }
            } ?: emptyList(),
            downloadUrl = json.optString("downloadUrl", json.optString("download_url")),
            sha256 = json.optString("sha256"),
            downloads = json.optInt("downloads", 0),
        )
    }
}

/**
 * High-level facade over plugin discovery, installation, execution and the
 * store. Mirrors the desktop `PluginManager`.
 */
class PluginManager(
    private val context: Context,
    /** Running app version, used for the `minCoreVersion` gate. */
    private val coreVersion: SemVer,
) {

    /** `filesDir/plugins/` — never external storage. */
    val pluginsDir: File = File(context.filesDir, "plugins")

    private val installer = PluginInstaller(pluginsDir, coreVersion)

    /** Storefront catalogue endpoint. */
    val storeBaseUrl: String = "https://openrom-store.pages.dev"

    /** Opens the storefront web page (custom tab target). */
    val storeUrl: String get() = storeBaseUrl

    private val storeApiUrl: String get() = "$storeBaseUrl/api/plugins.json"

    /**
     * Scans [pluginsDir], validates each `plugin.json` and registers the valid
     * ones in [PluginRegistry]. Plugins requiring a newer core are skipped.
     */
    suspend fun loadInstalledPlugins(): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        PluginRegistry.clear()
        if (!pluginsDir.exists()) return@withContext emptyList()

        val installed = mutableListOf<InstalledPlugin>()
        pluginsDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                val manifestFile = File(dir, "plugin.json")
                if (!manifestFile.exists()) return@forEach

                val manifest = try {
                    PluginManifest.fromJson(JSONObject(manifestFile.readText(Charsets.UTF_8)))
                } catch (_: Exception) {
                    return@forEach
                }

                if (manifest.validate().isNotEmpty()) return@forEach

                manifest.minCoreVersion?.let { if (coreVersion < it) return@forEach }

                PluginRegistry.registerPlugin(manifest)
                installed.add(InstalledPlugin(manifest, dir))
            }
        installed
    }

    /** Installs a `.orplug` archive from raw bytes, verifying [expectedSha256]. */
    suspend fun installFromBytes(
        bytes: ByteArray,
        expectedSha256: String? = null,
        expectedId: String? = null,
        expectedVersion: SemVer? = null,
    ): PluginInstallResult = withContext(Dispatchers.IO) {
        val result = installer.installFromBytes(
            bytes,
            expectedSha256 = expectedSha256,
            expectedId = expectedId,
            expectedVersion = expectedVersion,
        )
        if (result is PluginInstallResult.Success) {
            PluginRegistry.registerPlugin(result.manifest)
        }
        result
    }

    /** Installs a local `.orplug` file. */
    suspend fun installFromFile(archive: File): PluginInstallResult =
        withContext(Dispatchers.IO) {
            val result = installer.installFromArchive(archive)
            if (result is PluginInstallResult.Success) {
                PluginRegistry.registerPlugin(result.manifest)
            }
            result
        }

    /**
     * Downloads a store listing and installs it. The listing's SHA-256 is the
     * root of trust: a mismatch aborts installation before extraction.
     */
    suspend fun installFromStore(
        listing: StoreListing,
        onLog: (String) -> Unit = {},
    ): PluginInstallResult = withContext(Dispatchers.IO) {
        if (listing.downloadUrl.isBlank() || listing.sha256.isBlank()) {
            return@withContext PluginInstallResult.Failure(
                "Store listing \"${listing.id}\" has no downloadable artifact."
            )
        }
        onLog("Downloading ${listing.name} v${listing.version}\u2026")
        val bytes = try {
            httpGetBytes(listing.downloadUrl)
        } catch (e: Exception) {
            return@withContext PluginInstallResult.Failure(
                "Download failed: ${e.message}"
            )
        } ?: return@withContext PluginInstallResult.Failure("Download failed for ${listing.name}.")

        onLog("Verifying checksum\u2026")
        val result = installer.installFromBytes(
            bytes,
            expectedSha256 = listing.sha256,
            expectedId = listing.id,
            expectedVersion = SemVer.tryParse(listing.version),
        )
        if (result is PluginInstallResult.Success) {
            PluginRegistry.registerPlugin(result.manifest)
        }
        result
    }

    /** Removes an installed plugin and unregisters its routes. */
    suspend fun uninstall(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(pluginsDir, pluginId)
        val deleted = !dir.exists() || dir.deleteRecursively()
        if (deleted) PluginRegistry.unregisterPlugin(pluginId)
        deleted
    }

    /** Fetches the live storefront catalogue. Returns an empty list offline. */
    suspend fun fetchStoreListings(): List<StoreListing> = withContext(Dispatchers.IO) {
        val body = try { httpGetString(storeApiUrl) } catch (_: Exception) { null }
            ?: return@withContext emptyList()
        val array: JSONArray = try {
            val parsed = JSONArray(body)
            parsed
        } catch (_: Exception) {
            // Some deployments wrap the array in an object.
            try { JSONObject(body).optJSONArray("plugins") ?: JSONArray() }
            catch (_: Exception) { JSONArray() }
        }
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { StoreListing.fromJson(it) }
        }
    }

    private fun httpGetBytes(url: String): ByteArray? {
        val connection = open(url)
        return try {
            if (connection.responseCode != 200) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetString(url: String): String? {
        val connection = open(url)
        return try {
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OpenROM-Android")
        }
}
