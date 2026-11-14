// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plugin manifest parsing and validation. */
class PluginManifestTest {

    private val minimal = """
        {
          "schemaVersion": 2,
          "id": "xiso-tools",
          "name": "XISO Tools",
          "version": "1.2.0",
          "minCoreVersion": "3.9.0",
          "description": "Xbox ISO conversion",
          "category": "Xbox",
          "publisher": { "id": "m5dev", "name": "M5 Dev", "verified": true },
          "routes": [ { "from": "CCI", "to": "ISO" } ],
          "extensions": [
            { "ext": ".cci", "format": "CCI", "platform": "Xbox 360", "color": "#4caf50" }
          ],
          "execution": {
            "environments": {
              "android_arm64": {
                "executable": "bin/xiso",
                "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
              }
            },
            "args": [
              { "kind": "executable" },
              { "kind": "literal", "value": "convert" },
              { "kind": "input" },
              { "kind": "output" }
            ],
            "successExitCodes": [0],
            "timeoutSeconds": 600
          }
        }
    """.trimIndent()

    @Test
    fun `parses a minimal manifest`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        assertEquals(2, manifest.schemaVersion)
        assertEquals("xiso-tools", manifest.id)
        assertEquals("XISO Tools", manifest.name)
        assertEquals(SemVer(1, 2, 0), manifest.version)
        assertEquals(SemVer(3, 9, 0), manifest.minCoreVersion)
        assertEquals("Xbox", manifest.category)
        assertTrue(manifest.publisher.verified)
        assertEquals(1, manifest.routes.size)
        assertEquals("CCI", manifest.routes.first().from)
        assertEquals("ISO", manifest.routes.first().to)
        assertEquals(1, manifest.extensions.size)
        assertEquals(".cci", manifest.extensions.first().ext)
    }

    @Test
    fun `parses the execution block`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        val execution = manifest.execution
        assertEquals(PluginExecutionKind.process, execution.kind)
        assertEquals(listOf(0), execution.successExitCodes)
        assertEquals(600, execution.timeoutSeconds)
        assertEquals(4, execution.args.size)
        assertEquals(PluginArgKind.executable, execution.args[0].kind)
        assertEquals(PluginArgKind.literal, execution.args[1].kind)
        assertEquals("convert", execution.args[1].value)
        assertEquals(PluginArgKind.input, execution.args[2].kind)
        assertEquals(PluginArgKind.output, execution.args[3].kind)
    }

    @Test
    fun `a valid manifest reports no errors`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        assertEquals(emptyList<String>(), manifest.validate())
    }

    @Test
    fun `rejects an id with invalid characters`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal)).copy(id = "Bad Id!")
        assertTrue(manifest.validate().any { it.contains("\"id\"") })
    }

    @Test
    fun `rejects an executable path that escapes the plugin`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        val broken = manifest.copy(
            execution = manifest.execution.copy(
                environments = mapOf(
                    "android_arm64" to PluginEnvironment("../../evil", "a".repeat(64)),
                ),
            ),
        )
        assertTrue(broken.validate().any { it.contains("relative path") })
    }

    @Test
    fun `rejects a malformed sha256`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        val broken = manifest.copy(
            execution = manifest.execution.copy(
                environments = mapOf(
                    "android_arm64" to PluginEnvironment("bin/xiso", "not-a-hash"),
                ),
            ),
        )
        assertTrue(broken.validate().any { it.contains("sha256") })
    }

    @Test
    fun `rejects a newer schema version`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal)).copy(schemaVersion = 99)
        assertTrue(manifest.validate().any { it.contains("schemaVersion") })
    }

    @Test
    fun `rejects an empty success exit code list`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        val broken = manifest.copy(
            execution = manifest.execution.copy(successExitCodes = emptyList()),
        )
        assertTrue(broken.validate().any { it.contains("successExitCodes") })
    }

    @Test
    fun `normalises a legacy v1 manifest`() {
        val legacy = """
            {
              "schemaVersion": 1,
              "id": "legacy",
              "name": "Legacy Plugin",
              "version": "0.9.0",
              "author": "M5 Dev",
              "binaries": {
                "android_arm64": {
                  "executable": "bin/tool",
                  "sha256": "${"a".repeat(64)}"
                }
              },
              "conversions": [
                { "from": "CCI", "to": "ISO", "command": "{bin} convert {in} {out}" }
              ]
            }
        """.trimIndent()
        val manifest = PluginManifest.fromJson(JSONObject(legacy))

        assertEquals(1, manifest.schemaVersion)
        assertEquals("legacy", manifest.id)
        // Legacy `author` becomes an unverified publisher.
        assertEquals("M5 Dev", manifest.publisher.name)
        assertFalse(manifest.publisher.verified)
        assertEquals("m5-dev", manifest.publisher.id)
        assertEquals(1, manifest.routes.size)
        assertEquals("CCI", manifest.routes.first().from)
        // The command template is decomposed into structured args.
        assertEquals(4, manifest.execution.args.size)
        assertEquals(PluginArgKind.executable, manifest.execution.args[0].kind)
        assertEquals(PluginArgKind.literal, manifest.execution.args[1].kind)
        assertEquals("convert", manifest.execution.args[1].value)
        assertEquals(PluginArgKind.input, manifest.execution.args[2].kind)
        assertEquals(PluginArgKind.output, manifest.execution.args[3].kind)
        // The executable token must not survive into argv.
        assertTrue(
            manifest.execution.args.drop(1).none { it.kind == PluginArgKind.executable },
        )
    }

    @Test
    fun `executable token must be the first argument`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        val broken = manifest.copy(
            execution = manifest.execution.copy(
                args = listOf(
                    PluginArg(PluginArgKind.input),
                    PluginArg(PluginArgKind.executable),
                ),
            ),
        )
        assertTrue(broken.validate().any { it.contains("first argument") })
    }

    @Test
    fun `manifest round-trips through json`() {
        val manifest = PluginManifest.fromJson(JSONObject(minimal))
        val reparsed = PluginManifest.fromJson(manifest.toJson())
        assertEquals(manifest.id, reparsed.id)
        assertEquals(manifest.version, reparsed.version)
        assertEquals(manifest.routes.size, reparsed.routes.size)
        assertEquals(manifest.execution.args.size, reparsed.execution.args.size)
    }

    @Test
    fun `semver comparison follows precedence rules`() {
        assertTrue(SemVer(1, 0, 0) < SemVer(1, 0, 1))
        assertTrue(SemVer(1, 2, 0) > SemVer(1, 1, 9))
        assertTrue(SemVer(2, 0, 0) > SemVer(1, 99, 99))
        // A pre-release sorts before the release.
        assertTrue(SemVer(1, 0, 0, "alpha") < SemVer(1, 0, 0))
    }
}
