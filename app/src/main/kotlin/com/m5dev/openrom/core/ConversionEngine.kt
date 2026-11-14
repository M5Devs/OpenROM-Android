// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import java.io.File

/** Outcome of a built-in conversion. */
sealed interface ConversionResult {
    data class Success(val exitCode: Int) : ConversionResult
    data class Failure(val message: String, val exitCode: Int? = null) : ConversionResult
}

/**
 * Executes the built-in (non-plugin) conversion routes by turning a route's
 * command template into an argv vector and handing it to [ProcessRunner].
 *
 * Templates are *tokenised here into arguments* — they are never passed to a
 * shell — so a filename containing `;`, `$()` or quotes cannot inject a
 * command.
 */
class ConversionEngine(
    private val runner: ProcessRunner,
    private val toolPathProvider: (String) -> String?,
) {

    /**
     * Runs the built-in route `from -> to` for [job].
     *
     * [inputPath] and [outputPath] are absolute filesystem paths; SAF inputs
     * must already have been materialised to the cache by the caller.
     */
    suspend fun convert(
        from: String,
        to: String,
        job: ConversionJob,
        inputPath: String,
        outputPath: String,
        cuePath: String? = null,
        onProgress: (Double) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): ConversionResult {
        val key = "${from.uppercase()}->${to.uppercase()}"
        val template = FormatDetector.builtinCommandTemplates[key]
            ?: return ConversionResult.Failure("No built-in route for $key.")

        val toolName = template.substringBefore(' ')
        val toolPath = toolPathProvider(toolName)
            ?: return ConversionResult.Failure(
                "Required tool \"$toolName\" is not available on this device."
            )

        val cue = cuePath ?: generateCuePlaceholder(inputPath)
        val rendered = template
            .replace("{in}", inputPath)
            .replace("{out}", outputPath)
            .replace("{cue}", cue)
            .replace("{out_cue}", cue)

        // Drop the tool name; it becomes argv[0] via toolPath.
        val argv = tokenize(rendered).drop(1)
        val workDir = File(inputPath).parentFile ?: File(".")

        val result = runner.run(
            args = listOf(toolPath) + argv,
            workDir = workDir,
            onProgress = onProgress,
            onLog = onLog,
        )

        if (result.timedOut) return ConversionResult.Failure("Conversion timed out.")
        if (result.exitCode == 0) return ConversionResult.Success(result.exitCode)

        val details = result.stderr.lastOrNull { it.isNotBlank() }
            ?: result.stdout.lastOrNull { it.isNotBlank() }
            ?: "no output"
        return ConversionResult.Failure("$toolName exited with code ${result.exitCode}: $details",
            result.exitCode)
    }

    /** For `BIN -> CHD` the tool wants a cue sheet; derive the sibling one. */
    private fun generateCuePlaceholder(inputPath: String): String {
        val base = inputPath.substringBeforeLast('.', inputPath)
        val sibling = "$base.cue"
        return if (File(sibling).exists()) sibling else inputPath
    }

    /**
     * Splits a rendered template into argv elements, honouring single and
     * double quotes. Never evaluates a shell.
     */
    internal fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val buffer = StringBuilder()
        var inDouble = false
        var inSingle = false
        for (ch in command) {
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
}
