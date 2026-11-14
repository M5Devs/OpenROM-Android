// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import android.content.Context
import android.net.Uri
import com.m5dev.openrom.plugin.PluginExecutor
import com.m5dev.openrom.plugin.PluginRegistry
import com.m5dev.openrom.storage.SafHelper
import com.m5dev.openrom.storage.UriResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Final outcome of a job, after any SAF write-back. */
sealed interface JobOutcome {
    data class Success(val outputDescription: String, val outputBytes: Long) : JobOutcome
    data class Failure(val message: String) : JobOutcome
    data object Cancelled : JobOutcome
}

/**
 * Runs a [ConversionJob] end to end.
 *
 * Responsibilities:
 *  * materialise `content://` inputs into the app cache so native tools can
 *    open them by path,
 *  * pick the built-in engine or an installed plugin for the route,
 *  * write the result back into the chosen SAF output folder.
 */
class ConversionCoordinator(
    private val context: Context,
    private val runner: ProcessRunner,
    private val binaryManager: BinaryManager,
    private val pluginExecutor: PluginExecutor,
) {

    private val engine = ConversionEngine(runner) { tool -> binaryManager.getExecutablePath(tool) }

    /**
     * Executes [job].
     *
     * [onProgress] receives 0.0–1.0; [onLog] receives each tool output line.
     * [isCancelled] is polled between stages so a cancel takes effect promptly.
     */
    suspend fun run(
        job: ConversionJob,
        onProgress: (Double) -> Unit,
        onLog: (String) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): JobOutcome = withContext(Dispatchers.IO) {
        job.status = "running"
        job.error = null
        job.progress = 0.0

        val from = FormatDetector.getExtension(job.filepath).let { ext ->
            FormatDetector.supportedInput()[".$ext"] ?: "UNKNOWN"
        }.uppercase()

        val to = job.targetFormat.uppercase()
        if (from == "UNKNOWN") {
            return@withContext fail(job, "Unrecognised input format for \"${job.filepath}\".")
        }

        // 1. Materialise the input when it is a SAF URI.
        onLog("> Preparing input\u2026")
        val inputFile = materializeInput(job)
            ?: return@withContext fail(job, "Could not read the selected input file.")
        if (isCancelled()) return@withContext JobOutcome.Cancelled

        // 2. Decide where the output goes.
        val outputName = FormatDetector.getOutputName(inputFile.name, to.lowercase().replace("/cue", ""))
        val outputFile = if (job.isUriOutput) {
            File(context.cacheDir, "output/$outputName").apply { parentFile?.mkdirs() }
        } else {
            File(job.outputDir, outputName)
        }

        // 3. Route: plugin override first, then the built-in engine.
        val coreRoutes = FormatDetector.builtinCommandTemplates.keys
        val pluginRoute = PluginRegistry.resolveRoute(from, to, coreRoutes)

        val outcome = if (pluginRoute != null) {
            onLog("> Running plugin \"${pluginRoute.pluginId}\"\u2026")
            val result = pluginExecutor.run(
                conversion = pluginRoute,
                job = job.copyForExecution(inputFile, outputFile),
                cuePath = inputFile.siblingCue(),
                onProgress = { onProgress(it.coerceIn(0.0, 1.0)) },
                onLog = onLog,
            )
            when (result) {
                is com.m5dev.openrom.plugin.PluginRunResult.Success ->
                    JobOutcome.Success(outputFile.name, outputFile.lengthOrZero())
                is com.m5dev.openrom.plugin.PluginRunResult.Failure ->
                    JobOutcome.Failure(result.error)
            }
        } else {
            onLog("> Running built-in converter\u2026")
            val result = engine.convert(
                from = from,
                to = to,
                job = job.copyForExecution(inputFile, outputFile),
                inputPath = inputFile.absolutePath,
                outputPath = outputFile.absolutePath,
                cuePath = inputFile.siblingCue(),
                onProgress = { onProgress(it.coerceIn(0.0, 1.0)) },
                onLog = onLog,
            )
            when (result) {
                is ConversionResult.Success ->
                    JobOutcome.Success(outputFile.name, outputFile.lengthOrZero())
                is ConversionResult.Failure -> JobOutcome.Failure(result.message)
            }
        }

        if (isCancelled()) return@withContext JobOutcome.Cancelled

        // 4. Copy a URI-targeted result into the chosen SAF folder.
        when (outcome) {
            is JobOutcome.Success -> {
                if (job.isUriOutput) {
                    onLog("> Saving to the selected folder\u2026")
                    val folderUri = Uri.parse(job.outputDir)
                    val saved = UriResolver.writeToSafFolder(
                        context, folderUri, outputFile.name, outputFile
                    )
                    if (saved == null) {
                        return@withContext fail(
                            job,
                            "Converted successfully but could not write to the output folder.",
                        )
                    }
                    onLog("> Saved ${outputFile.name}")
                }
                job.status = "done"
                job.progress = 1.0
                job.outputSizeBytes = outcome.outputBytes
                outcome
            }
            is JobOutcome.Failure -> fail(job, outcome.message)
            JobOutcome.Cancelled -> JobOutcome.Cancelled
        }
    }

    private fun fail(job: ConversionJob, message: String): JobOutcome {
        job.status = "error"
        job.error = message
        return JobOutcome.Failure(message)
    }

    /** Copies a SAF input into the cache; local paths are used as-is. */
    private suspend fun materializeInput(job: ConversionJob): File? {
        val path = job.filepath
        if (SafHelper.isDirectFile(path)) {
            val file = if (path.startsWith("file://")) {
                File(Uri.parse(path).path ?: return null)
            } else {
                File(path)
            }
            return file.takeIf { it.exists() }
        }
        if (SafHelper.requiresMaterialization(path)) {
            return UriResolver.uriToTempFile(context, Uri.parse(path))
        }
        return File(path).takeIf { it.exists() }
    }

    private fun File.siblingCue(): String? {
        val cue = File(parentFile, "${nameWithoutExtension}.cue")
        return if (cue.exists()) cue.absolutePath else null
    }

    private fun File.lengthOrZero(): Long = if (exists()) length() else 0L

    /**
     * A copy of [job] whose paths point at the concrete files the tools will
     * see, so plugin argument resolution gets real paths rather than URIs.
     */
    private fun ConversionJob.copyForExecution(input: File, output: File): ConversionJob = ConversionJob(
        filepath = input.absolutePath,
        outputDir = output.parentFile?.absolutePath ?: outputDir,
        targetFormat = targetFormat,
        compression = compression,
        verify = verify,
        forcePlatform = forcePlatform,
        media = media,
        audioCodec = audioCodec,
        status = status,
        progress = progress,
        logLines = logLines,
        error = error,
        originalSizeBytes = originalSizeBytes,
        outputSizeBytes = outputSizeBytes,
    )
}
