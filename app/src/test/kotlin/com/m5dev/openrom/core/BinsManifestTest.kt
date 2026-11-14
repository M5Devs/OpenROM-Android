// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing of the bundled bins manifest. */
class BinsManifestTest {

    private val sample = """
        {
          "format_version": 1,
          "tools": {
            "chdman": {
              "version": "mame0289",
              "license": "GPL-2.0-or-later OR BSD-3-Clause",
              "upstream_repo": "https://github.com/mamedev/mame",
              "platforms": {
                "linux/x64": {
                  "path": "linux/x64/chdman",
                  "sha256": "aaaa",
                  "size": 100
                },
                "android/arm64-v8a": {
                  "path": "android/arm64-v8a/chdman",
                  "sha256": "a9e899dcfcb7c7657c5811b6fa68a4e5ed5ea0d748a06c1096d9cfe0f88fe571",
                  "size": 1773448,
                  "executable": true
                }
              }
            },
            "nodtool": {
              "version": "2.0.0",
              "platforms": {
                "linux/x64": {
                  "path": "linux/x64/nodtool",
                  "sha256": "bbbb",
                  "size": 200
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses format version`() {
        val manifest = BinsManifest.parse(sample)
        assertEquals(1, manifest.formatVersion)
    }

    @Test
    fun `resolves android arm64 entries`() {
        val manifest = BinsManifest.parse(sample)
        assertTrue(manifest.tools.containsKey("chdman"))
        val chdman = manifest.tools.getValue("chdman")
        assertEquals("android/arm64-v8a/chdman", chdman.relativePath)
        assertEquals("chdman", chdman.fileName)
        assertEquals(1773448L, chdman.sizeBytes)
    }

    @Test
    fun `tools without an android build are excluded`() {
        val manifest = BinsManifest.parse(sample)
        assertTrue(
            "nodtool ships no Android build and must not be resolvable",
            !manifest.tools.containsKey("nodtool"),
        )
        assertNull(manifest.getToolSha256("nodtool"))
    }

    @Test
    fun `exposes sha256 and SourceForge url`() {
        val manifest = BinsManifest.parse(sample)
        assertEquals(
            "a9e899dcfcb7c7657c5811b6fa68a4e5ed5ea0d748a06c1096d9cfe0f88fe571",
            manifest.getToolSha256("chdman"),
        )
        val url = manifest.getSourceForgeUrl("chdman")
        assertNotNull(url)
        assertTrue(url!!.endsWith("/android/arm64-v8a/chdman"))
        assertTrue(url.startsWith("https://"))
    }

    @Test
    fun `tool path resolves under the bins directory`() {
        val manifest = BinsManifest.parse(sample, java.io.File("/data/app/bins"))
        val path = manifest.getToolPath("chdman")
        assertEquals("/data/app/bins/chdman", path)
    }

    @Test
    fun `tool path is null without a bins directory`() {
        val manifest = BinsManifest.parse(sample)
        assertNull(manifest.getToolPath("chdman"))
    }

    @Test
    fun `the bundled asset parses and lists the android tools`() {
        // The real asset is copied into test resources so this validates the
        // shipped file rather than a synthetic sample.
        val json = javaClass.classLoader!!
            .getResourceAsStream("manifest.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val manifest = BinsManifest.parse(json)

        val expected = setOf("chdman", "ecm", "unecm", "maxcso", "xdelta3")
        assertEquals(expected, manifest.toolNames().toSet())

        // Every route that requires an unavailable tool must be filtered out.
        val forbidden = setOf("nodtool", "saturn-patcher", "xgdtool")
        manifest.toolNames().forEach { name ->
            assertTrue("$name should not be an Android tool", name !in forbidden)
        }
    }
}
