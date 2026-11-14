// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

import org.json.JSONArray
import org.json.JSONObject

/** Current manifest schema version understood by this build. */
const val PLUGIN_SCHEMA_VERSION = 2

/** Strict, case-sensitive plugin identifier: lowercase alphanumerics, `_`, `-`. */
val PLUGIN_ID_PATTERN = Regex("^[a-z0-9_-]+$")

/** Tokens a plugin argument may reference; each maps to a runtime value. */
enum class PluginArgKind {
    executable, literal, input, output, outputDir, cue, inputName, outputName;

    companion object {
        fun fromName(name: String?): PluginArgKind? =
            entries.firstOrNull { it.name == name }
    }
}

/** How a plugin executable is invoked. */
enum class PluginExecutionKind {
    process, library;

    companion object {
        fun fromName(name: String?): PluginExecutionKind? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * One structured argument passed to the plugin process. Only `literal` carries
 * a value; every other kind resolves to a runtime path.
 */
data class PluginArg(val kind: PluginArgKind, val value: String = "") {

    /** Resolves to a concrete argv element, or `null` when unresolvable. */
    fun resolve(values: Map<PluginArgKind, String>): String? =
        if (kind == PluginArgKind.literal) value else values[kind]

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        if (kind == PluginArgKind.literal) put("value", value)
    }

    companion object {
        fun fromJson(json: JSONObject): PluginArg = PluginArg(
            kind = PluginArgKind.fromName(json.optString("kind")) ?: PluginArgKind.literal,
            value = json.optString("value", ""),
        )
    }
}

/** Binary descriptor for a single OS/arch environment. */
data class PluginEnvironment(val executable: String, val sha256: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("executable", executable)
        put("sha256", sha256)
    }

    companion object {
        fun fromJson(json: JSONObject): PluginEnvironment = PluginEnvironment(
            executable = json.optString("executable", ""),
            sha256 = json.optString("sha256", ""),
        )
    }
}

/** Execution descriptor: the structured replacement for command templates. */
data class PluginExecution(
    val kind: PluginExecutionKind = PluginExecutionKind.process,
    val environments: Map<String, PluginEnvironment> = emptyMap(),
    val args: List<PluginArg> = emptyList(),
    val successExitCodes: List<Int> = listOf(0),
    val timeoutSeconds: Int = 0,
    val environment: Map<String, String> = emptyMap(),
) {
    /** The binary descriptor for the current host, or `null` when absent. */
    val currentEnvironment: PluginEnvironment?
        get() = environments[currentPlatformKey()]

    val supportsCurrentPlatform: Boolean get() = currentEnvironment != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("environments", JSONObject().apply {
            environments.forEach { (k, v) -> put(k, v.toJson()) }
        })
        put("args", JSONArray().apply { args.forEach { put(it.toJson()) } })
        put("successExitCodes", JSONArray().apply { successExitCodes.forEach { put(it) } })
        if (timeoutSeconds > 0) put("timeoutSeconds", timeoutSeconds)
        if (environment.isNotEmpty()) {
            put("environment", JSONObject().apply {
                environment.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PluginExecution {
            val rawEnvs = json.optJSONObject("environments") ?: JSONObject()
            val environments = LinkedHashMap<String, PluginEnvironment>()
            rawEnvs.keys().forEach { key ->
                environments[key] = PluginEnvironment.fromJson(
                    rawEnvs.optJSONObject(key) ?: JSONObject()
                )
            }

            val rawArgs = json.optJSONArray("args") ?: JSONArray()
            val args = (0 until rawArgs.length()).mapNotNull { i ->
                rawArgs.optJSONObject(i)?.let { PluginArg.fromJson(it) }
            }

            val rawExit = json.optJSONArray("successExitCodes")
            val successExitCodes =
                if (rawExit == null || rawExit.length() == 0) listOf(0)
                else (0 until rawExit.length()).map { rawExit.optInt(it, 0) }

            val rawEnv = json.optJSONObject("environment") ?: JSONObject()
            val environment = LinkedHashMap<String, String>()
            rawEnv.keys().forEach { key -> environment[key] = rawEnv.optString(key) }

            return PluginExecution(
                kind = PluginExecutionKind.fromName(json.optString("kind", "process"))
                    ?: PluginExecutionKind.process,
                environments = environments,
                args = args,
                successExitCodes = successExitCodes,
                timeoutSeconds = json.optInt("timeoutSeconds", 0),
                environment = environment,
            )
        }
    }
}

/**
 * A conversion route the plugin advertises. [override] must be set explicitly
 * for a plugin to shadow a built-in core route.
 */
data class PluginRoute(
    val from: String,
    val to: String,
    val override: Boolean = false,
) {
    val key: String get() = "$from->$to"

    fun toJson(): JSONObject = JSONObject().apply {
        put("from", from)
        put("to", to)
        if (override) put("override", true)
    }

    companion object {
        fun fromJson(json: JSONObject): PluginRoute = PluginRoute(
            from = json.optString("from", "").uppercase(),
            to = json.optString("to", "").uppercase(),
            override = json.optBoolean("override", false),
        )
    }
}

/** Extension → format registration (drives detection and UI badges). */
data class PluginExtension(
    val ext: String,
    val format: String,
    val platform: String,
    val color: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ext", ext)
        put("format", format)
        put("platform", platform)
        put("color", color)
    }

    companion object {
        fun fromJson(json: JSONObject): PluginExtension = PluginExtension(
            ext = json.optString("ext", ""),
            format = json.optString("format", ""),
            platform = json.optString("platform", ""),
            color = json.optString("color", "#7a8a9a"),
        )
    }
}

/** Publisher / trust metadata; drives the "verified" badge in the store UI. */
data class PluginPublisher(
    val id: String,
    val name: String,
    val verified: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("verified", verified)
    }

    companion object {
        /** For legacy manifests that only carried a free-form author. */
        fun legacy(author: String): PluginPublisher = PluginPublisher(
            id = slugify(author),
            name = author.ifEmpty { "Unknown" },
            verified = false,
        )

        fun fromJson(json: JSONObject): PluginPublisher = PluginPublisher(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            verified = json.optBoolean("verified", false),
        )

        private fun slugify(value: String): String =
            value.lowercase()
                .replace(Regex("[^a-z0-9_-]+"), "-")
                .trim('-')
    }
}

/**
 * The complete, validated plugin manifest. The JSON schema is identical to the
 * desktop `plugin_manifest.dart`, including legacy v1 normalisation.
 */
data class PluginManifest(
    val schemaVersion: Int = PLUGIN_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val version: SemVer,
    val minCoreVersion: SemVer? = null,
    val description: String = "",
    val publisher: PluginPublisher,
    val routes: List<PluginRoute> = emptyList(),
    val execution: PluginExecution = PluginExecution(),
    val extensions: List<PluginExtension> = emptyList(),
    val category: String = "",
) {
    /** Publisher display name, kept for UI code predating the publisher model. */
    val author: String get() = publisher.name

    /** Legacy alias for [execution.environments]. */
    val binaries: Map<String, PluginEnvironment> get() = execution.environments

    /** Legacy alias for [routes]. */
    val conversions: List<PluginRoute> get() = routes

    val currentPlatformBinary: PluginEnvironment? get() = execution.currentEnvironment

    val supportsHostPlatform: Boolean get() = execution.supportsCurrentPlatform

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("id", id)
        put("name", name)
        put("version", version.toString())
        put("minCoreVersion", minCoreVersion?.toString() ?: "")
        put("description", description)
        put("publisher", publisher.toJson())
        put("routes", JSONArray().apply { routes.forEach { put(it.toJson()) } })
        put("execution", execution.toJson())
        put("extensions", JSONArray().apply { extensions.forEach { put(it.toJson()) } })
        if (category.isNotEmpty()) put("category", category)
    }

    /** Structural validation; empty list means the manifest is valid. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (schemaVersion > PLUGIN_SCHEMA_VERSION) {
            errors.add(
                "Plugin \"schemaVersion\" $schemaVersion is newer than this build " +
                    "supports ($PLUGIN_SCHEMA_VERSION)."
            )
        }

        if (id.isBlank()) {
            errors.add("Plugin \"id\" is required.")
        } else if (!PLUGIN_ID_PATTERN.matches(id)) {
            errors.add(
                "Plugin \"id\" must match ^[a-z0-9_-]+\$ (lowercase letters, digits, \"_\" or \"-\")."
            )
        }

        if (name.isBlank()) errors.add("Plugin \"name\" is required.")

        if (version.major == 0 && version.minor == 0 && version.patch == 0 &&
            version.toString() != "0.0.0"
        ) {
            errors.add("Plugin \"version\" must be a valid SemVer (e.g. \"1.2.0\").")
        }

        if (publisher.id.isBlank()) errors.add("Plugin \"publisher.id\" is required.")

        if (execution.kind == PluginExecutionKind.process) {
            if (execution.environments.isEmpty()) {
                errors.add(
                    "Plugin \"execution.environments\" must declare at least one platform binary."
                )
            }
            if (execution.args.isEmpty()) {
                errors.add("Plugin \"execution.args\" must contain at least one argument.")
            }
            if (execution.successExitCodes.isEmpty()) {
                errors.add("Plugin \"execution.successExitCodes\" must not be empty.")
            }
            execution.environments.forEach { (key, env) ->
                if (env.executable.isBlank()) {
                    errors.add("Plugin binary for \"$key\" is missing an \"executable\".")
                } else if (isUnsafeRelativePath(env.executable)) {
                    errors.add(
                        "Plugin binary for \"$key\" must be a relative path inside the " +
                            "plugin (got \"${env.executable}\")."
                    )
                }
                if (env.sha256.isBlank()) {
                    errors.add("Plugin binary for \"$key\" is missing a \"sha256\" checksum.")
                } else if (!isHex64(env.sha256)) {
                    errors.add(
                        "Plugin binary for \"$key\" has a malformed \"sha256\" (expected 64 hex chars)."
                    )
                }
            }
            execution.args.forEachIndexed { i, arg ->
                if (arg.kind == PluginArgKind.literal && arg.value.isEmpty()) {
                    errors.add("Plugin \"execution.args\" contains an empty literal.")
                }
                if (arg.kind == PluginArgKind.executable && i != 0) {
                    errors.add(
                        "Plugin \"execution.args\" may only use the \"executable\" token " +
                            "as its first argument."
                    )
                }
            }
        }

        routes.forEach { route ->
            if (route.from.isBlank() || route.to.isBlank()) {
                errors.add("Plugin route \"from\"/\"to\" must not be empty.")
            }
        }

        extensions.forEach { ext ->
            if (ext.ext.isBlank()) errors.add("Extension \"ext\" cannot be empty.")
            if (ext.format.isBlank()) errors.add("Extension \"format\" cannot be empty.")
        }

        return errors
    }

    companion object {
        /** Parses JSON, accepting both schema v2 and the legacy v1 layout. */
        fun fromJson(json: JSONObject): PluginManifest {
            val rawPublisher = json.optJSONObject("publisher")
            val publisher = if (rawPublisher != null) {
                PluginPublisher.fromJson(rawPublisher)
            } else {
                PluginPublisher.legacy(json.optString("author", ""))
            }

            val extensions = jsonArrayOfObjects(json, "extensions")
                .map { PluginExtension.fromJson(it) }

            val rawRoutes = json.optJSONArray("routes")
            val rawExecution = json.optJSONObject("execution")

            if (rawRoutes != null || rawExecution != null) {
                return PluginManifest(
                    schemaVersion = json.optInt("schemaVersion", PLUGIN_SCHEMA_VERSION),
                    id = json.optString("id", ""),
                    name = json.optString("name", ""),
                    version = SemVer.tryParse(json.optString("version", "")) ?: SemVer(0, 0, 0),
                    minCoreVersion = SemVer.tryParseLoose(json.optString("minCoreVersion")),
                    description = json.optString("description", ""),
                    publisher = publisher,
                    routes = jsonArrayOfObjects(json, "routes").map { PluginRoute.fromJson(it) },
                    execution = rawExecution?.let { PluginExecution.fromJson(it) }
                        ?: PluginExecution(),
                    extensions = extensions,
                    category = json.optString("category", ""),
                )
            }

            return fromLegacy(json, publisher, extensions)
        }

        /** Normalises a legacy v1 manifest into the current model. */
        private fun fromLegacy(
            json: JSONObject,
            publisher: PluginPublisher,
            extensions: List<PluginExtension>,
        ): PluginManifest {
            val rawBinaries = json.optJSONObject("binaries") ?: JSONObject()
            val environments = LinkedHashMap<String, PluginEnvironment>()
            rawBinaries.keys().forEach { key ->
                environments[key] = PluginEnvironment.fromJson(
                    rawBinaries.optJSONObject(key) ?: JSONObject()
                )
            }

            val rawConversions = jsonArrayOfObjects(json, "conversions")
            val routes = rawConversions.map { PluginRoute.fromJson(it) }

            val template = rawConversions
                .map { it.optString("command", "") }
                .firstOrNull { it.isNotEmpty() } ?: ""

            val legacyArgs = mutableListOf<PluginArg>()
            val seenKinds = mutableSetOf<PluginArgKind>()
            if (template.isNotEmpty()) {
                tokenizeLegacyTemplate(template).forEach { token ->
                    val arg = legacyTokenToArg(token)
                    if (arg.kind != PluginArgKind.literal && !seenKinds.add(arg.kind)) {
                        return@forEach
                    }
                    legacyArgs.add(arg)
                }
            }

            return PluginManifest(
                schemaVersion = 1,
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                version = SemVer.tryParse(json.optString("version", "")) ?: SemVer(0, 0, 0),
                minCoreVersion = SemVer.tryParseLoose(json.optString("minCoreVersion")),
                description = json.optString("description", ""),
                publisher = publisher,
                routes = routes,
                execution = PluginExecution(environments = environments, args = legacyArgs),
                extensions = extensions,
                category = json.optString("category", ""),
            )
        }

        /**
         * Splits a legacy command template on whitespace, honouring quotes.
         * Used only for migration, never for execution.
         */
        internal fun tokenizeLegacyTemplate(template: String): List<String> {
            val tokens = mutableListOf<String>()
            val buffer = StringBuilder()
            var inDouble = false
            var inSingle = false
            for (ch in template) {
                when {
                    ch == '"' && !inSingle -> inDouble = !inDouble
                    ch == '\'' && !inDouble -> inSingle = !inSingle
                    ch == ' ' && !inDouble && !inSingle -> {
                        if (buffer.isNotEmpty()) {
                            tokens.add(buffer.toString())
                            buffer.clear()
                        }
                    }
                    else -> buffer.append(ch)
                }
            }
            if (buffer.isNotEmpty()) tokens.add(buffer.toString())
            return tokens
        }

        private fun legacyTokenToArg(token: String): PluginArg = when (token) {
            "{bin}" -> PluginArg(PluginArgKind.executable)
            "{in}" -> PluginArg(PluginArgKind.input)
            "{out}" -> PluginArg(PluginArgKind.output)
            "{outdir}" -> PluginArg(PluginArgKind.outputDir)
            "{cue}", "{out_cue}" -> PluginArg(PluginArgKind.cue)
            else -> PluginArg(PluginArgKind.literal, token)
        }

        private fun jsonArrayOfObjects(json: JSONObject, key: String): List<JSONObject> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }

        /** True when [path] would escape the plugin directory. */
        internal fun isUnsafeRelativePath(path: String): Boolean {
            if (path.isBlank()) return true
            if (path.startsWith("/") || path.startsWith("\\")) return true
            if (Regex("^[a-zA-Z]:").containsMatchIn(path)) return true // Windows drive
            return path.split(Regex("[\\\\/]+")).contains("..")
        }

        internal fun isHex64(value: String): Boolean =
            Regex("^[0-9a-fA-F]{64}$").matches(value.trim())
    }
}

/**
 * Platform key used to select a plugin binary. Android ships arm64-v8a only,
 * so this resolves to `android_arm64` in practice.
 */
fun currentPlatformKey(): String {
    val abi = try {
        android.os.Build.SUPPORTED_ABIS?.firstOrNull()?.lowercase() ?: ""
    } catch (_: Exception) {
        // Outside a real device (unit tests) the ABI list is unavailable.
        ""
    }
    return if (abi.contains("arm64") || abi.contains("aarch64")) {
        "android_arm64"
    } else {
        "android_x64"
    }
}
