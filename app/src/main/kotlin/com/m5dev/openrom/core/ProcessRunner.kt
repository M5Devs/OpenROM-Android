// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Result of a completed process. */
data class ProcessResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val timedOut: Boolean = false,
)

/**
 * Runs bundled native tools.
 *
 * Arguments are always supplied as a pre-split `List<String>`; nothing is ever
 * interpolated into a shell string, so paths containing spaces, quotes or `;`
 * cannot escape into a command.
 */
class ProcessRunner(
    /** Directory holding the extracted tools, injected as `LD_LIBRARY_PATH`. */
    private val binDir: File? = null,
) {

    /** Matches `(\d+)%` anywhere in a line, as the desktop tools emit it. */
    private val progressPattern = Regex("""(\d+(?:\.\d+)?)\s*%""")

    private companion object {
        /**
         * How long to wait for the output pumps after a process is killed. A
         * tool can leave a child holding its pipes open, so this must be finite.
         */
        const val PUMP_DRAIN_TIMEOUT_MS = 3_000L
    }

    /**
     * Runs [args] in [workDir], streaming output.
     *
     * @param successExitCodes exit codes the caller treats as success; the
     *   result carries the raw code either way.
     * @param timeoutSeconds hard kill after this many seconds; `0` disables.
     */
    suspend fun run(
        args: List<String>,
        workDir: File,
        onProgress: (Double) -> Unit = {},
        onLog: (String) -> Unit = {},
        successExitCodes: List<Int> = listOf(0),
        timeoutSeconds: Int = 0,
        extraEnv: Map<String, String> = emptyMap(),
    ): ProcessResult = withContext(Dispatchers.IO) {
        require(args.isNotEmpty()) { "argv must not be empty" }

        val builder = ProcessBuilder(args)
        if (workDir.exists()) builder.directory(workDir)

        val env = builder.environment()
        // Dynamic dependencies of the bundled tools resolve from the bins dir.
        if (binDir != null && binDir.exists()) {
            val existing = env["LD_LIBRARY_PATH"]
            env["LD_LIBRARY_PATH"] = if (existing.isNullOrEmpty()) {
                binDir.absolutePath
            } else {
                "${binDir.absolutePath}:$existing"
            }
        }
        env.putAll(extraEnv)

        val process = try {
            builder.start()
        } catch (e: Exception) {
            return@withContext ProcessResult(
                exitCode = -1,
                stdout = emptyList(),
                stderr = listOf("Failed to launch ${args.first()}: ${e.message}"),
            )
        }

        val stdout = java.util.Collections.synchronizedList(mutableListOf<String>())
        val stderr = java.util.Collections.synchronizedList(mutableListOf<String>())

        // The pumps run on daemon threads rather than coroutines: a thread
        // blocked in readLine() cannot be cancelled cooperatively, so awaiting a
        // coroutine would hang if the tool leaves a child holding the pipe open.
        val stdoutThread = pumpThread(process.inputStream) { line ->
            stdout.add(line)
            onLog(line)
            progressPattern.find(line)?.let { match ->
                match.groupValues[1].toDoubleOrNull()?.let { pct ->
                    onProgress((pct / 100.0).coerceIn(0.0, 1.0))
                }
            }
        }
        val stderrThread = pumpThread(process.errorStream) { line ->
            stderr.add(line)
            onLog(line)
        }

        val finished = if (timeoutSeconds > 0) {
            process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } else {
            process.waitFor()
            true
        }

        if (!finished) {
            onLog("  \u23F1 Timed out after ${timeoutSeconds}s; terminating.")
            // A tool may have spawned children that outlive the parent.
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            // Closing our ends unblocks the readers even if a grandchild still
            // holds the write end of the pipe.
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }

        stdoutThread.join(PUMP_DRAIN_TIMEOUT_MS)
        stderrThread.join(PUMP_DRAIN_TIMEOUT_MS)

        val code = try { process.exitValue() } catch (_: IllegalThreadStateException) { -1 }
        ProcessResult(code, stdout.toList(), stderr.toList(), timedOut = !finished)
    }

    /** Emits each output line of a process as a cold [Flow]. */
    fun stream(
        args: List<String>,
        workDir: File,
        timeoutSeconds: Int = 0,
    ): Flow<String> = flow {
        val builder = ProcessBuilder(args)
        if (workDir.exists()) builder.directory(workDir)
        binDir?.let { dir ->
            if (dir.exists()) builder.environment()["LD_LIBRARY_PATH"] = dir.absolutePath
        }
        val process = builder.start()
        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.useLines { lines -> lines.forEach { emit(it) } }
        } finally {
            if (timeoutSeconds > 0) process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Starts a daemon thread draining [stream] line by line. Daemon threads
     * cannot outlive the JVM, so a stuck reader never blocks app shutdown.
     */
    private fun pumpThread(stream: java.io.InputStream, onLine: (String) -> Unit): Thread =
        Thread({
            try {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trimEnd()
                        if (trimmed.isNotEmpty()) onLine(trimmed)
                    }
                }
            } catch (_: Exception) {
                // A killed process closes its pipes mid-read; that is expected.
            }
        }, "openrom-pump").apply {
            isDaemon = true
            start()
        }

    /** Parses the first `NN%` in [line] as a 0.0–1.0 fraction, or `null`. */
    fun parseProgress(line: String): Double? =
        progressPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
            (it / 100.0).coerceIn(0.0, 1.0)
        }
}
