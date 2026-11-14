// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import android.content.Context
import org.json.JSONObject

/** One Android tool entry from the bundled bins manifest. */
data class BinTool(
    val name: String,
    val version: String,
    val license: String,
    val upstreamRepo: String,
    /** Relative path inside the manifest, e.g. `android/arm64-v8a/chdman`. */
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val executable: Boolean,
) {
    /** Basename the tool is stored under inside the bins directory. */
    val fileName: String get() = relativePath.substringAfterLast('/')

    /** SourceForge download URL for this exact artifact. */
    val sourceForgeUrl: String
        get() = "$SOURCEFORGE_BASE/$relativePath"

    companion object {
        const val SOURCEFORGE_BASE =
            "https://downloads.sourceforge.net/project/openrom-binaries"
    }
}

/**
 * Parses the bundled `assets/manifest.json`.
 *
 * The file uses the same schema as the OpenROM-bins registry (`format_version`
 * + `tools`, each with a `platforms` map). Only `android/arm64-v8a` entries are
 * resolved: the manifest ships Android builds for exactly the tools the app
 * bundles, so any other platform key is ignored.
 *
 * The parser is pure (it takes the raw JSON text) so it is unit-testable
 * without an Android `Context`.
 */
class BinsManifest private constructor(
    val formatVersion: Int,
    val tools: Map<String, BinTool>,
    /** Directory tools are extracted into; `null` before installation. */
    private val binDir: java.io.File?,
) {

    /** Absolute path of [name] after extraction, or `null` when unknown. */
    fun getToolPath(name: String): String? {
        val tool = tools[name] ?: return null
        val dir = binDir ?: return null
        return java.io.File(dir, tool.fileName).absolutePath
    }

    fun getToolSha256(name: String): String? = tools[name]?.sha256

    fun getSourceForgeUrl(name: String): String? = tools[name]?.sourceForgeUrl

    fun getToolSize(name: String): Long = tools[name]?.sizeBytes ?: 0L

    fun toolNames(): List<String> = tools.keys.toList()

    /** Every tool that must be present for the built-in routes to work. */
    val requiredTools: Set<String> get() = tools.keys

    /** A copy bound to the directory tools will be extracted into. */
    fun withBinDir(dir: java.io.File): BinsManifest = BinsManifest(formatVersion, tools, dir)

    companion object {
        /** Platform key this build resolves; arm64-v8a is the only shipped ABI. */
        const val ANDROID_PLATFORM_KEY = "android/arm64-v8a"

        /** Parses manifest JSON text. Throws on malformed JSON. */
        fun parse(json: String, binDir: java.io.File? = null): BinsManifest {
            val root = JSONObject(json)
            val formatVersion = root.optInt("format_version", 1)
            val toolsJson = root.optJSONObject("tools") ?: JSONObject()

            val tools = LinkedHashMap<String, BinTool>()
            toolsJson.keys().forEach { name ->
                val toolJson = toolsJson.optJSONObject(name) ?: return@forEach
                val platforms = toolJson.optJSONObject("platforms") ?: return@forEach
                val android = platforms.optJSONObject(ANDROID_PLATFORM_KEY) ?: return@forEach

                val path = android.optString("path", "")
                val sha = android.optString("sha256", "")
                if (path.isBlank() || sha.isBlank()) return@forEach

                tools[name] = BinTool(
                    name = name,
                    version = toolJson.optString("version", ""),
                    license = toolJson.optString("license", ""),
                    upstreamRepo = toolJson.optString("upstream_repo", ""),
                    relativePath = path,
                    sha256 = sha,
                    sizeBytes = android.optLong("size", 0L),
                    executable = android.optBoolean("executable", true),
                )
            }
            return BinsManifest(formatVersion, tools, binDir)
        }

        /** Reads `assets/manifest.json` from the APK. */
        fun load(context: Context, binDir: java.io.File? = null): BinsManifest {
            val json = context.assets.open("manifest.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return parse(json, binDir)
        }
    }
}
