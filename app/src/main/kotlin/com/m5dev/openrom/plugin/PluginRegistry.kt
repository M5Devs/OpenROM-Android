// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

/** A plugin route resolved against the current host. */
data class RegisteredConversion(
    val pluginId: String,
    val from: String,
    val to: String,
    val manifest: PluginManifest,
    val executableRelPath: String,
    val override: Boolean = false,
) {
    val key: String get() = "$from->$to"
}

/**
 * In-memory registry of active plugins and the routes/formats they add.
 *
 * Precedence is enforced by [resolveRoute]: a plugin route is only used when
 * the plugin opted in with `override: true`, or when the core has no route for
 * that pair. A plugin can therefore never silently hijack a built-in flow.
 */
object PluginRegistry {

    private val activeList = mutableListOf<PluginManifest>()

    private val inputMap = mutableMapOf<String, String>()
    private val platformStore = mutableMapOf<String, String>()
    private val colorStore = mutableMapOf<String, String>()
    private val conversionTargets = mutableMapOf<String, MutableList<String>>()
    private val conversions = mutableMapOf<String, RegisteredConversion>()

    fun clear() {
        activeList.clear()
        inputMap.clear()
        platformStore.clear()
        colorStore.clear()
        conversionTargets.clear()
        conversions.clear()
    }

    /** Registers a validated manifest and all of its routes and formats. */
    fun registerPlugin(manifest: PluginManifest) {
        activeList.removeAll { it.id == manifest.id }
        activeList.add(manifest)

        val platformBin = manifest.execution.currentEnvironment

        manifest.extensions.forEach { ext ->
            val cleanExt = if (ext.ext.startsWith(".")) {
                ext.ext.lowercase()
            } else {
                ".${ext.ext.lowercase()}"
            }
            val fmtUpper = ext.format.uppercase()
            inputMap[cleanExt] = fmtUpper
            platformStore[fmtUpper] = ext.platform
            colorStore[fmtUpper] = ext.color
        }

        manifest.routes.forEach { route ->
            val fromUpper = route.from.uppercase()
            val toUpper = route.to.uppercase()
            val targets = conversionTargets.getOrPut(fromUpper) { mutableListOf() }
            if (!targets.contains(toUpper)) targets.add(toUpper)

            conversions["$fromUpper->$toUpper"] = RegisteredConversion(
                pluginId = manifest.id,
                from = fromUpper,
                to = toUpper,
                override = route.override,
                manifest = manifest,
                executableRelPath = platformBin?.executable ?: "",
            )
        }
    }

    fun unregisterPlugin(pluginId: String) {
        activeList.removeAll { it.id == pluginId }
        val remaining = activeList.toList()
        clear()
        remaining.forEach { registerPlugin(it) }
    }

    /** Plugin-registered extensions, merged over the built-ins by callers. */
    val pluginSupportedInput: Map<String, String> get() = inputMap.toMap()

    /** Format to platform, for formats a plugin introduced. */
    val pluginPlatformMap: Map<String, String> get() = platformStore.toMap()

    /** Format to badge colour, for formats a plugin introduced. */
    val pluginFormatColors: Map<String, String> get() = colorStore.toMap()

    /** `FROM` to the ordered list of `TO` formats plugins can produce. */
    val pluginConversionMap: Map<String, List<String>>
        get() = conversionTargets.mapValues { it.value.toList() }

    /** Active plugin manifests, most recently registered last. */
    val activePlugins: List<PluginManifest> get() = activeList.toList()

    fun pluginById(pluginId: String): PluginManifest? =
        activeList.firstOrNull { it.id == pluginId }

    /** Raw lookup with no precedence applied. Prefer [resolveRoute]. */
    fun getConversion(from: String, to: String): RegisteredConversion? =
        conversions["${from.uppercase()}->${to.uppercase()}"]

    /**
     * Resolves a route honouring core precedence. [coreRoutes] is the set of
     * `FROM->TO` keys the built-in engine handles.
     */
    fun resolveRoute(from: String, to: String, coreRoutes: Set<String>): RegisteredConversion? {
        val conversion = getConversion(from, to) ?: return null
        if (conversion.override) return conversion
        if (coreRoutes.contains(conversion.key)) return null
        return conversion
    }

    /** True when any plugin registered this route. */
    fun hasRoute(from: String, to: String): Boolean = getConversion(from, to) != null
}
