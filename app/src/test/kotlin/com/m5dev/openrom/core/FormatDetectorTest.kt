// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format detection, including the SAF and plugin-merged code paths. */
class FormatDetectorTest {

    @Test
    fun `detects a plain iso`() {
        val result = FormatDetector.detect("game.iso", 1_000_000)
        assertEquals("ISO", result.format)
        assertFalse(result.isUnknown)
        assertTrue(result.validTargets.contains("CHD"))
    }

    @Test
    fun `detects nkit iso before plain iso`() {
        val result = FormatDetector.detect("game.nkit.iso", 1_000_000)
        assertEquals("NKIT", result.format)
    }

    @Test
    fun `detects chd and its targets`() {
        val result = FormatDetector.detect("game.chd", 1_000_000)
        assertEquals("CHD", result.format)
        assertTrue(result.validTargets.contains("ISO"))
    }

    @Test
    fun `extension lookup is case insensitive`() {
        assertEquals("ISO", FormatDetector.detect("GAME.ISO", 1).format)
        assertEquals("CHD", FormatDetector.detect("Game.Chd", 1).format)
    }

    @Test
    fun `unknown extension is reported as unknown`() {
        val result = FormatDetector.detect("notes.txt", 10)
        assertTrue(result.isUnknown)
        assertTrue(result.validTargets.isEmpty())
    }

    @Test
    fun `detects a content uri by its display name`() {
        val uri = "content://com.android.providers.downloads/document/1234"
        val result = FormatDetector.detectUri(uri, "game.iso", 2048)
        assertEquals("ISO", result.format)
    }

    @Test
    fun `content uri falls back to the last path segment`() {
        val uri = "content://com.example.provider/roms%2Fgame.chd"
        val result = FormatDetector.detectUri(uri, null, 4096)
        assertEquals("CHD", result.format)
    }

    @Test
    fun `iso targets include only routes whose tools ship on android`() {
        // nodtool is absent on Android, so ISO -> RVZ must be hidden.
        val result = FormatDetector.detect("game.iso", 1_000_000)
        assertFalse(
            "ISO -> RVZ needs nodtool, which has no Android build",
            result.validTargets.contains("RVZ"),
        )
        assertTrue(result.validTargets.contains("CHD"))
        assertTrue(result.validTargets.contains("CSO"))
        assertTrue(result.validTargets.contains("ECM"))
    }

    @Test
    fun `every offered target maps to a runnable built-in route`() {
        val result = FormatDetector.detect("game.iso", 1_000_000)
        result.validTargets.forEach { target ->
            assertTrue(
                "target $target should be runnable on Android",
                FormatDetector.isRouteAvailable(result.format, target),
            )
        }
    }

    @Test
    fun `getOutputName swaps the extension`() {
        assertEquals("game.chd", FormatDetector.getOutputName("game.iso", "chd"))
        assertEquals("my rom.cso", FormatDetector.getOutputName("my rom.iso", "cso"))
    }

    @Test
    fun `humanBytes formats sizes readably`() {
        assertTrue(FormatDetector.humanBytes(1_500_000_000).endsWith("GB"))
        assertTrue(FormatDetector.humanBytes(2_000_000).endsWith("MB"))
    }

    @Test
    fun `format colours match the desktop palette`() {
        assertEquals("#e94560", FormatDetector.getBadgeColor("ISO").lowercase())
        assertEquals("#00bcd4", FormatDetector.getBadgeColor("CHD").lowercase())
        assertEquals("#4caf50", FormatDetector.getBadgeColor("CSO").lowercase())
        assertEquals("#3f51b5", FormatDetector.getBadgeColor("RVZ").lowercase())
    }

    @Test
    fun `plugin registered extension becomes detectable`() {
        val manifest = com.m5dev.openrom.plugin.PluginManifest(
            id = "test-plugin",
            name = "Test Plugin",
            version = com.m5dev.openrom.plugin.SemVer(1, 0, 0),
            publisher = com.m5dev.openrom.plugin.PluginPublisher("m5dev", "M5 Dev", true),
            extensions = listOf(
                com.m5dev.openrom.plugin.PluginExtension(".cci", "CCI", "Xbox 360", "#4caf50"),
            ),
        )
        com.m5dev.openrom.plugin.PluginRegistry.registerPlugin(manifest)
        try {
            assertEquals("CCI", FormatDetector.detect("game.cci", 100).format)
            assertEquals("#4caf50", FormatDetector.getBadgeColor("CCI").lowercase())
        } finally {
            com.m5dev.openrom.plugin.PluginRegistry.clear()
        }
    }

    @Test
    fun `plugin route is offered when the core has no route for the pair`() {
        val manifest = com.m5dev.openrom.plugin.PluginManifest(
            id = "xiso",
            name = "XISO",
            version = com.m5dev.openrom.plugin.SemVer(1, 0, 0),
            publisher = com.m5dev.openrom.plugin.PluginPublisher("m5dev", "M5 Dev", true),
            routes = listOf(com.m5dev.openrom.plugin.PluginRoute("CCI", "ISO")),
            extensions = listOf(
                com.m5dev.openrom.plugin.PluginExtension(".cci", "CCI", "Xbox 360", "#4caf50"),
            ),
        )
        com.m5dev.openrom.plugin.PluginRegistry.registerPlugin(manifest)
        try {
            val result = FormatDetector.detect("game.cci", 100)
            assertEquals("CCI", result.format)
            assertTrue(result.validTargets.contains("ISO"))
        } finally {
            com.m5dev.openrom.plugin.PluginRegistry.clear()
        }
    }

    @Test
    fun `plugin cannot shadow a built-in route without an override`() {
        val manifest = com.m5dev.openrom.plugin.PluginManifest(
            id = "hijack",
            name = "Hijack",
            version = com.m5dev.openrom.plugin.SemVer(1, 0, 0),
            publisher = com.m5dev.openrom.plugin.PluginPublisher("evil", "Evil", false),
            routes = listOf(
                com.m5dev.openrom.plugin.PluginRoute("ISO", "CHD", override = false),
            ),
        )
        com.m5dev.openrom.plugin.PluginRegistry.registerPlugin(manifest)
        try {
            val coreRoutes = FormatDetector.builtinCommandTemplates.keys
            val resolved = com.m5dev.openrom.plugin.PluginRegistry
                .resolveRoute("ISO", "CHD", coreRoutes)
            assertEquals(null, resolved)
        } finally {
            com.m5dev.openrom.plugin.PluginRegistry.clear()
        }
    }

    @Test
    fun `plugin may shadow a built-in route with an explicit override`() {
        val manifest = com.m5dev.openrom.plugin.PluginManifest(
            id = "better-chd",
            name = "Better CHD",
            version = com.m5dev.openrom.plugin.SemVer(1, 0, 0),
            publisher = com.m5dev.openrom.plugin.PluginPublisher("m5dev", "M5 Dev", true),
            routes = listOf(
                com.m5dev.openrom.plugin.PluginRoute("ISO", "CHD", override = true),
            ),
        )
        com.m5dev.openrom.plugin.PluginRegistry.registerPlugin(manifest)
        try {
            val coreRoutes = FormatDetector.builtinCommandTemplates.keys
            val resolved = com.m5dev.openrom.plugin.PluginRegistry
                .resolveRoute("ISO", "CHD", coreRoutes)
            assertNotNull(resolved)
            assertEquals("better-chd", resolved!!.pluginId)
        } finally {
            com.m5dev.openrom.plugin.PluginRegistry.clear()
        }
    }
}
