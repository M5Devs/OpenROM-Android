// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Process execution: argument handling, streaming, progress and timeouts. */
class ProcessRunnerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val runner = ProcessRunner()

    @Test
    fun `runs a process and captures stdout`() = runBlocking {
        val result = runner.run(
            args = listOf("/bin/echo", "hello world"),
            workDir = temp.root,
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.any { it.contains("hello world") })
    }

    @Test
    fun `arguments with shell metacharacters are passed literally`() = runBlocking {
        // A filename containing a shell injection attempt must arrive intact:
        // argv is never routed through a shell.
        val nasty = "game;rm -rf /\$(whoami).iso"
        val result = runner.run(
            args = listOf("/bin/echo", nasty),
            workDir = temp.root,
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.any { it.contains(nasty) })
    }

    @Test
    fun `captures stderr and a non-zero exit code`() = runBlocking {
        val result = runner.run(
            args = listOf("/bin/sh", "-c", "echo boom >&2; exit 3"),
            workDir = temp.root,
        )
        assertEquals(3, result.exitCode)
        assertTrue(result.stderr.any { it.contains("boom") })
    }

    @Test
    fun `parses percentage progress from output`() = runBlocking {
        val seen = mutableListOf<Double>()
        val result = runner.run(
            args = listOf("/bin/sh", "-c", "echo '10%'; echo '50%'; echo '100%'"),
            workDir = temp.root,
            onProgress = { seen.add(it) },
        )
        assertEquals(0, result.exitCode)
        assertEquals(listOf(0.1, 0.5, 1.0), seen)
    }

    @Test
    fun `progress parser ignores lines without a percentage`() {
        assertNull(runner.parseProgress("Compressing tracks..."))
        assertEquals(0.42, runner.parseProgress("42% complete")!!, 1e-9)
        assertEquals(1.0, runner.parseProgress("100%")!!, 1e-9)
        // Values above 100 are clamped rather than overflowing the bar.
        assertEquals(1.0, runner.parseProgress("250%")!!, 1e-9)
    }

    @Test
    fun `streams output line by line`() = runBlocking {
        val lines = mutableListOf<String>()
        runner.stream(
            args = listOf("/bin/sh", "-c", "echo one; echo two; echo three"),
            workDir = temp.root,
        ).collect { lines.add(it) }
        assertEquals(listOf("one", "two", "three"), lines)
    }

    @Test
    fun `a hung process is killed when its timeout expires`() = runBlocking {
        val started = System.currentTimeMillis()
        val result = runner.run(
            args = listOf("/bin/sh", "-c", "sleep 30"),
            workDir = temp.root,
            timeoutSeconds = 1,
        )
        val elapsed = System.currentTimeMillis() - started
        assertTrue("expected a timeout", result.timedOut)
        assertTrue("should return promptly, took ${elapsed}ms", elapsed < 10_000)
    }

    @Test
    fun `reports a launch failure instead of throwing`() = runBlocking {
        val result = runner.run(
            args = listOf("/nonexistent/definitely-not-a-binary"),
            workDir = temp.root,
        )
        assertTrue(result.exitCode != 0)
        assertTrue(result.stderr.any { it.contains("Failed to launch") })
    }

    @Test
    fun `runs in the requested working directory`() = runBlocking {
        val workDir = temp.newFolder("work")
        File(workDir, "marker.txt").writeText("here")
        val result = runner.run(
            args = listOf("/bin/sh", "-c", "cat marker.txt"),
            workDir = workDir,
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.any { it.contains("here") })
    }

    @Test
    fun `injects LD_LIBRARY_PATH pointing at the bins directory`() = runBlocking {
        val bins = temp.newFolder("bins")
        val binRunner = ProcessRunner(bins)
        val result = binRunner.run(
            args = listOf("/bin/sh", "-c", "echo \$LD_LIBRARY_PATH"),
            workDir = temp.root,
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.any { it.contains(bins.absolutePath) })
    }

    @Test
    fun `a non-zero exit is not treated as success by the caller`() = runBlocking {
        val result = runner.run(
            args = listOf("/bin/sh", "-c", "exit 7"),
            workDir = temp.root,
            successExitCodes = listOf(0),
        )
        assertFalse(result.exitCode == 0)
        assertFalse(listOf(0).contains(result.exitCode))
    }
}
