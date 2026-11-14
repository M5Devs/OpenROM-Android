// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom

import android.app.Application
import com.m5dev.openrom.core.BinaryManager
import com.m5dev.openrom.core.BinaryReadyResult
import com.m5dev.openrom.core.BinsManifest
import com.m5dev.openrom.plugin.PluginManager
import com.m5dev.openrom.plugin.SemVer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide wiring. Holds the binary and plugin managers, and kicks off
 * first-launch preparation of the native tools.
 */
class OpenRomApplication : Application() {

    /** Application-scoped coroutine scope; never cancelled while the app lives. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var binsManifest: BinsManifest
        private set

    lateinit var binaryManager: BinaryManager
        private set

    lateinit var pluginManager: PluginManager
        private set

    lateinit var settings: SettingsStore
        private set

    /** Last result of [BinaryManager.ensureReady], for the settings screen. */
    @Volatile
    var lastBinaryResult: BinaryReadyResult? = null
        private set

    override fun onCreate() {
        super.onCreate()

        settings = SettingsStore(this)
        binsManifest = BinsManifest.load(this, java.io.File(filesDir, "bins"))
        binaryManager = BinaryManager(this, binsManifest)
        pluginManager = PluginManager(this, SemVer.tryParseLoose(BuildConfig.VERSION_NAME)
            ?: SemVer(1, 0, 0))

        appScope.launch {
            prepareBinaries()
            pluginManager.loadInstalledPlugins()
        }
    }

    /** Extracts or downloads every tool and narrows the detector accordingly. */
    suspend fun prepareBinaries(): BinaryReadyResult {
        val result = binaryManager.ensureReady()
        lastBinaryResult = result
        return result
    }
}
