// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.ui.convert

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.m5dev.openrom.OpenRomApplication
import com.m5dev.openrom.R
import com.m5dev.openrom.core.BinaryManager
import com.m5dev.openrom.core.ConversionCoordinator
import com.m5dev.openrom.core.ConversionJob
import com.m5dev.openrom.core.FormatDetector
import com.m5dev.openrom.core.JobOutcome
import com.m5dev.openrom.core.ProcessRunner
import com.m5dev.openrom.databinding.SheetProgressBinding
import com.m5dev.openrom.plugin.PluginExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live conversion progress: ROMeo state, percentage, streaming log, elapsed
 * time and a cancel action. Owns the conversion coroutine so cancelling is
 * immediate.
 */
class ProgressSheet : BottomSheetDialogFragment() {

    private var _binding: SheetProgressBinding? = null
    private val binding get() = _binding!!

    private var job: ConversionJob? = null
    private var conversion: Job? = null
    private var elapsedJob: Job? = null

    private val cancelled = AtomicBoolean(false)
    private var startedAt = 0L
    private var finished = false

    private val logLines = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        val fileName = requireArguments().getString(ARG_FILE) ?: "input"
        val target = requireArguments().getString(ARG_TARGET) ?: ""
        binding.progressTitle.text = getString(R.string.converting_title, fileName, target)
        binding.actionPrimary.setOnClickListener { onPrimaryAction() }
        binding.actionSecondary.setOnClickListener { copyLog() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Starts the job. Safe to call once, immediately after [show]. */
    fun start(job: ConversionJob, runner: ProcessRunner, binaryManager: BinaryManager) {
        if (this.job != null) return
        this.job = job
        startedAt = System.currentTimeMillis()

        val app = requireActivity().application as OpenRomApplication
        val coordinator = ConversionCoordinator(
            context = app,
            runner = runner,
            binaryManager = binaryManager,
            pluginExecutor = PluginExecutor(app.pluginManager.pluginsDir, runner),
        )

        elapsedJob = lifecycleScope.launch {
            while (true) {
                delay(500)
                if (finished) break
                binding.progressElapsed.text =
                    getString(R.string.elapsed_label, formatElapsed())
            }
        }

        conversion = lifecycleScope.launch {
            val outcome = coordinator.run(
                job = job,
                onProgress = { value ->
                    binding.progressBar.setProgressCompat((value * 100).toInt(), true)
                    binding.progressPercent.text = "${(value * 100).toInt()}%"
                },
                onLog = { line -> appendLog(line) },
                isCancelled = { cancelled.get() },
            )
            finish(outcome)
        }
    }

    private fun appendLog(line: String) {
        logLines.add(line)
        // Keep the view bounded; the full log is retained for copying.
        val visible = logLines.takeLast(MAX_VISIBLE_LOG_LINES).joinToString("\n")
        binding.progressLog.text = visible
        binding.logScroll.post {
            binding.logScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun finish(outcome: JobOutcome) {
        finished = true
        binding.actionPrimary.isEnabled = true

        when (outcome) {
            is JobOutcome.Success -> {
                binding.progressRomeo.setImageResource(R.drawable.romeo_success)
                binding.progressBar.setProgressCompat(100, true)
                binding.progressPercent.text = "100%"
                binding.progressResult.isVisible = true
                binding.progressResult.text = getString(
                    R.string.output_size_label,
                    FormatDetector.humanBytes(outcome.outputBytes),
                )
                binding.actionPrimary.text = getString(R.string.close)
                binding.actionSecondary.isVisible = true
                binding.actionSecondary.text = getString(R.string.copy_log)
            }
            is JobOutcome.Failure -> {
                binding.progressRomeo.setImageResource(R.drawable.romeo_error)
                binding.progressResult.isVisible = true
                binding.progressResult.text = outcome.message
                binding.actionPrimary.text = getString(R.string.close)
                binding.actionSecondary.isVisible = true
                binding.actionSecondary.text = getString(R.string.copy_log)
                appendLog("! ${outcome.message}")
            }
            JobOutcome.Cancelled -> {
                appendLog("! Cancelled")
                dismissAllowingStateLoss()
            }
        }
    }

    private fun onPrimaryAction() {
        if (finished) {
            dismissAllowingStateLoss()
            return
        }
        // Cancel: flag the coordinator, then cancel the coroutine outright.
        cancelled.set(true)
        conversion?.cancel()
        appendLog("! Cancelling\u2026")
        binding.actionPrimary.isEnabled = false
        dismissAllowingStateLoss()
    }

    private fun copyLog() {
        val clipboard = requireContext()
            .getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            android.content.ClipData.newPlainText("OpenROM log", logLines.joinToString("\n"))
        )
        android.widget.Toast.makeText(
            requireContext(), R.string.log_copied, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun formatElapsed(): String {
        val total = (System.currentTimeMillis() - startedAt) / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }

    companion object {
        private const val ARG_FILE = "file"
        private const val ARG_TARGET = "target"
        private const val MAX_VISIBLE_LOG_LINES = 200

        fun newInstance(fileName: String, target: String): ProgressSheet =
            ProgressSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE, fileName)
                    putString(ARG_TARGET, target)
                }
            }
    }
}
