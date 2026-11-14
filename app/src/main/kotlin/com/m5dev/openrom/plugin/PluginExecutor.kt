// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

import com.m5dev.openrom.core.ConversionJob
import com.m5dev.openrom.core.ProcessRunner
import java.io.File

/** Outcome of running a plugin executable. */
sealed interface PluginRunResult {
    data class Success(val exitCode: Int) : PluginRunResult
    data class Failure(val error: String, val exitCode: Int? = null) : PluginRunResult
}

/**
 * Executes plugin binaries safely.
 *
 * Security properties, identical to the desktop executor:
 *  * **No shell.** Arguments are handed to [ProcessBuilder] as a pre-split
 *    array; nothing is interpolated into a command string.
 *  * **Containment.** The resolved executable must live inside the plugin's own
 *    directory, checked by real (symlink-resolved) path.
 *  * **Integrity.** The binary's SHA-256 is re-verified immediately before
 *    execution, on top of install-time verification.
 */
class PluginExecutor(
    private val pluginsDir: File,
    private val runner: ProcessRunner = ProcessRunner(),
) {

    /**
     * Resolves a [PluginArg] vector to concrete argv. Returns `null` when any
     * non-literal argument cannot be resolved, which callers treat as a hard
     * failure rather than passing a dangling value to the tool.
     */
    fun resolveArgs(
        manifest: PluginManifest,
        job: ConversionJob,
        executablePath: String,
        cuePath: String? = null,
        outputPath: String? = null,
        outputDir: String? = null,
    ): List<String>? {
        val input = job.filepath
        val resolvedOutput = outputPath
            ?: FormatOutputName.resolve(job)
        val resolvedDir = outputDir
            ?: File(resolvedOutput).parent
            ?: job.outputDir

        val values = buildMap {
            put(PluginArgKind.executable, executablePath)
            put(PluginArgKind.input, input)
            put(PluginArgKind.output, resolvedOutput)
            put(PluginArgKind.outputDir, resolvedDir)
            put(PluginArgKind.inputName, File(input).name)
            put(PluginArgKind.outputName, File(resolvedOutput).name)
            if (!cuePath.isNullOrEmpty()) put(PluginArgKind.cue, cuePath)
        }

        val argv = mutableListOf<String>()
        manifest.execution.args.forEach { arg ->
            // The `executable` token names the program itself; ProcessBuilder
            // already receives it separately, so it must not appear in argv.
            if (arg.kind == PluginArgKind.executable) return@forEach
            val value = arg.resolve(values) ?: return null
            argv.add(value)
        }
        return argv
    }

    /** Runs [conversion] for the given job. Never throws for tool failures. */
    suspend fun run(
        conversion: RegisteredConversion,
        job: ConversionJob,
        cuePath: String? = null,
        onProgress: (Double) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): PluginRunResult {
        val manifest = conversion.manifest

        if (manifest.execution.kind != PluginExecutionKind.process) {
            return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" uses an unsupported execution kind " +
                    "\"${manifest.execution.kind.name}\"."
            )
        }

        val env = manifest.execution.currentEnvironment
            ?: return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" has no build for platform \"${currentPlatformKey()}\"."
            )

        // Resolve the executable strictly inside the plugin directory.
        val pluginDir = File(pluginsDir, manifest.id).absoluteFile.normalize()
        val executable = joinUnder(pluginDir, env.executable)
            ?: return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" executable path \"${env.executable}\" escapes " +
                    "the plugin directory."
            )

        val exeFile = File(executable)
        if (!exeFile.exists()) {
            return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" executable not found: $executable"
            )
        }

        // Containment by real path, so a symlink pointing outside is refused.
        val realPluginDir = try { pluginDir.canonicalPath } catch (e: Exception) {
            return PluginRunResult.Failure("Plugin \"${manifest.id}\" could not be resolved: ${e.message}")
        }
        val realExecutable = try { exeFile.canonicalPath } catch (e: Exception) {
            return PluginRunResult.Failure("Plugin \"${manifest.id}\" could not be resolved: ${e.message}")
        }
        if (!isInside(realPluginDir, realExecutable)) {
            return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" executable path \"${env.executable}\" escapes " +
                    "the plugin directory."
            )
        }

        // Re-verify integrity right before execution.
        if (env.sha256.isNotEmpty()) {
            val actual = PluginInstaller.sha256Hex(exeFile)
            if (actual != env.sha256.trim().lowercase()) {
                return PluginRunResult.Failure(
                    "Plugin \"${manifest.id}\" failed its integrity check " +
                        "(expected ${env.sha256}, got $actual)."
                )
            }
        }

        exeFile.setExecutable(true, false)

        val argv = resolveArgs(manifest, job, executable, cuePath)
            ?: return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" arguments could not be resolved for this job."
            )

        onLog("  cmd: ${(listOf(File(executable).name) + argv).joinToString(" ")}")

        val result = runner.run(
            args = listOf(executable) + argv,
            workDir = pluginDir,
            onProgress = onProgress,
            onLog = onLog,
            successExitCodes = manifest.execution.successExitCodes,
            timeoutSeconds = manifest.execution.timeoutSeconds,
            extraEnv = manifest.execution.environment,
        )

        if (result.timedOut) {
            return PluginRunResult.Failure(
                "Plugin \"${manifest.id}\" timed out after " +
                    "${manifest.execution.timeoutSeconds}s.",
                exitCode = result.exitCode,
            )
        }

        if (manifest.execution.successExitCodes.contains(result.exitCode)) {
            onProgress(1.0)
            return PluginRunResult.Success(result.exitCode)
        }

        val details = result.stderr.lastOrNull { it.isNotBlank() }
            ?: result.stdout.lastOrNull { it.isNotBlank() }
            ?: "no output"
        return PluginRunResult.Failure(
            "Plugin \"${manifest.id}\" exited with code ${result.exitCode}: $details",
            exitCode = result.exitCode,
        )
    }

    /** Absolute path of [relative] under [root], or `null` if it escapes. */
    internal fun joinUnder(root: File, relative: String): String? {
        if (relative.isBlank()) return null
        if (relative.startsWith("/") || relative.startsWith("\\")) return null
        val resolved = File(root, relative).absoluteFile.normalize()
        val normalizedRoot = root.absoluteFile.normalize()
        if (resolved != normalizedRoot &&
            !resolved.path.startsWith(normalizedRoot.path + File.separator)
        ) {
            return null
        }
        return resolved.path
    }

    /** Whether the real path [child] lies strictly inside [root]. */
    internal fun isInside(root: String, child: String): Boolean {
        if (child == root) return true
        return child.startsWith(if (root.endsWith(File.separator)) root else root + File.separator)
    }
}

/** Derives the plugin output filename from a job's target format. */
internal object FormatOutputName {
    fun resolve(job: ConversionJob): String {
        val target = job.targetFormat.lowercase().replace("/cue", "")
        return com.m5dev.openrom.core.FormatDetector.getOutputName(job.filepath, target)
    }
}
