// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Installer hardening: zip-slip, zip bombs, checksum and atomic swap. */
class PluginInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun installer(pluginsDir: File, limits: PluginArchiveLimits = PluginArchiveLimits()) =
        PluginInstaller(pluginsDir, SemVer(1, 0, 0), limits)

    /** Builds a ZIP from [entries], preserving entry names verbatim. */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A minimal, valid plugin archive with a real (fake) executable payload. */
    private fun validArchive(
        id: String = "xiso-tools",
        binaryBytes: ByteArray = "#!/bin/sh\necho xiso\n".toByteArray(),
        binarySha: String? = null,
    ): ByteArray {
        val digest = binarySha ?: PluginInstaller.sha256Hex(binaryBytes)
        val manifest = """
            {
              "schemaVersion": 2,
              "id": "$id",
              "name": "XISO Tools",
              "version": "1.0.0",
              "publisher": { "id": "m5dev", "name": "M5 Dev", "verified": true },
              "routes": [ { "from": "CCI", "to": "ISO" } ],
              "extensions": [
                { "ext": ".cci", "format": "CCI", "platform": "Xbox 360", "color": "#4caf50" }
              ],
              "execution": {
                "environments": {
                  "android_arm64": { "executable": "bin/xiso", "sha256": "$digest" },
                  "android_x64": { "executable": "bin/xiso", "sha256": "$digest" }
                },
                "args": [
                  { "kind": "executable" },
                  { "kind": "input" },
                  { "kind": "output" }
                ]
              }
            }
        """.trimIndent()
        return zipOf(
            "plugin.json" to manifest.toByteArray(),
            "bin/xiso" to binaryBytes,
        )
    }

    @Test
    fun `installs a valid archive`() {
        val pluginsDir = temp.newFolder("plugins")
        val result = installer(pluginsDir).installFromBytes(validArchive())

        assertTrue(result is PluginInstallResult.Success)
        val installed = File(pluginsDir, "xiso-tools")
        assertTrue(installed.isDirectory)
        assertTrue(File(installed, "plugin.json").exists())
        assertTrue(File(installed, "bin/xiso").exists())
    }

    @Test
    fun `installed executable is marked executable`() {
        val pluginsDir = temp.newFolder("plugins")
        installer(pluginsDir).installFromBytes(validArchive())
        assertTrue(File(pluginsDir, "xiso-tools/bin/xiso").canExecute())
    }

    @Test
    fun `rejects a zip-slip entry`() {
        val pluginsDir = temp.newFolder("plugins")
        val evil = zipOf(
            "plugin.json" to "{}".toByteArray(),
            "../escaped.txt" to "pwned".toByteArray(),
        )
        val result = installer(pluginsDir).installFromBytes(evil)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("escapes"))
        assertFalse(File(pluginsDir.parentFile, "escaped.txt").exists())
    }

    @Test
    fun `rejects an absolute-path entry`() {
        val pluginsDir = temp.newFolder("plugins")
        val evil = zipOf("/tmp/absolute.txt" to "pwned".toByteArray())
        val result = installer(pluginsDir).installFromBytes(evil)
        assertTrue(result is PluginInstallResult.Failure)
    }

    @Test
    fun `rejects an archive with too many entries`() {
        val pluginsDir = temp.newFolder("plugins")
        val entries = (0 until 20).map { "file$it" to ByteArray(1) }.toTypedArray()
        val archive = zipOf(*entries)
        val result = installer(pluginsDir, PluginArchiveLimits(maxEntries = 8))
            .installFromBytes(archive)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("too many entries"))
    }

    @Test
    fun `rejects an entry larger than the per-file limit`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = zipOf("big.bin" to ByteArray(4096))
        val result = installer(pluginsDir, PluginArchiveLimits(maxEntryBytes = 1024))
            .installFromBytes(archive)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("per-file size limit"))
    }

    @Test
    fun `rejects an archive exceeding the total size limit`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = zipOf(
            "a.bin" to ByteArray(600),
            "b.bin" to ByteArray(600),
        )
        val result = installer(pluginsDir, PluginArchiveLimits(maxTotalBytes = 1000))
            .installFromBytes(archive)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("total uncompressed"))
    }

    @Test
    fun `rejects an archive whose checksum does not match the trusted digest`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = validArchive()
        val wrongDigest = "0".repeat(64)
        val result = installer(pluginsDir).installFromBytes(archive, expectedSha256 = wrongDigest)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("checksum mismatch"))
        // Nothing may be extracted when the archive checksum fails.
        assertFalse(File(pluginsDir, "xiso-tools").exists())
    }

    @Test
    fun `accepts an archive whose checksum matches the trusted digest`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = validArchive()
        val digest = PluginInstaller.sha256Hex(archive)
        val result = installer(pluginsDir).installFromBytes(archive, expectedSha256 = digest)
        assertTrue(result is PluginInstallResult.Success)
    }

    @Test
    fun `rejects a binary whose declared sha256 is wrong`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = validArchive(binarySha = "b".repeat(64))
        val result = installer(pluginsDir).installFromBytes(archive)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("SHA-256 mismatch"))
    }

    @Test
    fun `rejects an archive without a manifest`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = zipOf("bin/xiso" to ByteArray(16))
        val result = installer(pluginsDir).installFromBytes(archive)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("plugin.json"))
    }

    @Test
    fun `rejects a manifest that declares a different id than the registry pinned`() {
        val pluginsDir = temp.newFolder("plugins")
        val result = installer(pluginsDir)
            .installFromBytes(validArchive(), expectedId = "some-other-plugin")

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("id mismatch"))
    }

    @Test
    fun `rejects a plugin requiring a newer core version`() {
        val pluginsDir = temp.newFolder("plugins")
        val manifest = """
            {
              "schemaVersion": 2,
              "id": "needs-new-core",
              "name": "Needs New Core",
              "version": "1.0.0",
              "minCoreVersion": "9.0.0",
              "publisher": { "id": "m5dev", "name": "M5 Dev" },
              "execution": {
                "environments": {
                  "android_arm64": { "executable": "bin/t", "sha256": "${"a".repeat(64)}" }
                },
                "args": [ { "kind": "input" } ]
              }
            }
        """.trimIndent()
        val archive = zipOf("plugin.json" to manifest.toByteArray())
        val result = PluginInstaller(pluginsDir, SemVer(1, 0, 0)).installFromBytes(archive)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).error.contains("requires OpenROM core"))
    }

    @Test
    fun `replaces an existing install atomically`() {
        val pluginsDir = temp.newFolder("plugins")
        val engine = installer(pluginsDir)
        engine.installFromBytes(validArchive())

        // A second install must replace the first without leaving staging or
        // backup directories behind.
        val second = validArchive(binaryBytes = "#!/bin/sh\necho v2\n".toByteArray())
        val result = engine.installFromBytes(second)
        assertTrue(result is PluginInstallResult.Success)

        val installed = File(pluginsDir, "xiso-tools")
        assertTrue(installed.isDirectory)
        assertTrue(File(installed, "bin/xiso").readText().contains("v2"))
        assertFalse(File(pluginsDir, ".staging").exists())
        assertFalse(File(pluginsDir, ".backup").exists())
    }

    @Test
    fun `central directory reports declared sizes`() {
        val pluginsDir = temp.newFolder("plugins")
        val archive = zipOf("plugin.json" to "x".repeat(100).toByteArray())
        val entries = installer(pluginsDir).parseCentralDirectory(archive)
        assertEquals(1, entries.size)
        assertEquals("plugin.json", entries.first().name)
        assertEquals(100L, entries.first().uncompressedSize)
        assertNotNull(entries.first().name)
    }
}
