// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.ui.convert

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.m5dev.openrom.OpenRomApplication
import com.m5dev.openrom.R
import com.m5dev.openrom.core.ConversionJob
import com.m5dev.openrom.core.DetectedFormat
import com.m5dev.openrom.core.FormatDetector
import com.m5dev.openrom.databinding.FragmentConvertBinding
import com.m5dev.openrom.storage.SafHelper
import com.m5dev.openrom.storage.UriResolver
import com.m5dev.openrom.ui.UiFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main screen: pick a ROM, choose a target format and an output folder,
 * then start a conversion.
 */
class ConvertFragment : Fragment() {

    private var _binding: FragmentConvertBinding? = null
    private val binding get() = _binding!!

    private lateinit var saf: SafHelper
    private lateinit var app: OpenRomApplication

    /** The selected input, or `null` before a file is chosen. */
    private var selectedUri: Uri? = null
    private var selectedName: String? = null
    private var detected: DetectedFormat? = null
    private var selectedTarget: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = requireActivity().application as OpenRomApplication
        saf = SafHelper(this)
        saf.register()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConvertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pickCard.setOnClickListener { pickFile() }
        binding.outputCard.setOnClickListener { pickOutputFolder() }
        binding.clearFile.setOnClickListener { clearSelection() }
        binding.convertButton.setOnClickListener { startConversion() }

        restoreOutputFolder()

        // A ROM shared into the app arrives as a nav argument.
        arguments?.getString("incoming_uri")?.let { uriString ->
            loadUri(Uri.parse(uriString))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Input ──────────────────────────────────────────────────────────────

    private fun pickFile() {
        saf.launchFilePicker { uri ->
            if (uri != null) loadUri(uri)
        }
    }

    /** Resolves a URI's name, size and header, then renders the result. */
    private fun loadUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val name = UriResolver.getFileName(context, uri) ?: uri.lastPathSegment ?: "input"
            val size = UriResolver.getFileSize(context, uri)
            val header = withContext(Dispatchers.IO) {
                try {
                    UriResolver.openInput(context, uri)
                        ?.let { FormatDetector.readHeader(it) } ?: ByteArray(0)
                } catch (_: Exception) {
                    ByteArray(0)
                }
            }

            selectedUri = uri
            selectedName = name
            detected = FormatDetector.detectUri(uri.toString(), name, size, header)
            selectedTarget = null
            renderSelection()
        }
    }

    private fun clearSelection() {
        selectedUri = null
        selectedName = null
        detected = null
        selectedTarget = null
        renderSelection()
    }

    private fun renderSelection() {
        val info = detected
        val hasFile = info != null && !info.isUnknown

        binding.fileCard.isVisible = hasFile
        binding.convertToLabel.isVisible = hasFile
        binding.pickCard.isVisible = !hasFile

        if (!hasFile) {
            binding.romeo.setImageResource(R.drawable.romeo_idle)
            binding.targetChips.removeAllViews()
            binding.noTargets.isVisible = info != null
            binding.convertButton.isEnabled = false
            return
        }

        info!!
        binding.romeo.setImageResource(R.drawable.romeo_idle)
        binding.fileName.text = selectedName
        binding.fileMeta.text = getString(R.string.platform_label, info.platform) +
            "  \u00b7  " + info.sizeStr
        UiFormat.styleBadge(requireContext(), binding.fileFormatChip, info.format)

        buildTargetChips(info)
        updateConvertEnabled()
    }

    /**
     * Renders up to four target chips, plus a "More formats" chip that opens
     * the picker sheet when more are available. Plugin-registered targets are
     * included automatically because [FormatDetector] merges them.
     */
    private fun buildTargetChips(info: DetectedFormat) {
        val group = binding.targetChips
        group.removeAllViews()

        val targets = info.validTargets
        binding.noTargets.isVisible = targets.isEmpty()
        if (targets.isEmpty()) {
            binding.convertButton.isEnabled = false
            return
        }

        // Restore the last used target when it is still offered.
        val remembered = app.settings.lastTargetFormat
        if (selectedTarget == null && remembered != null && targets.contains(remembered)) {
            selectedTarget = remembered
        }
        if (selectedTarget == null || !targets.contains(selectedTarget)) {
            selectedTarget = targets.first()
        }

        val visible = targets.take(MAX_INLINE_CHIPS)
        visible.forEach { target ->
            group.addView(makeTargetChip(target, showMore = false))
        }

        if (targets.size > MAX_INLINE_CHIPS) {
            val more = Chip(requireContext()).apply {
                text = getString(R.string.more_formats)
                isCheckable = false
                setOnClickListener { openFormatPicker() }
            }
            group.addView(more)
        }
    }

    private fun makeTargetChip(target: String, showMore: Boolean): Chip {
        val context = requireContext()
        return Chip(context).apply {
            text = target
            isCheckable = true
            isChecked = target == selectedTarget
            setOnClickListener {
                selectedTarget = target
                app.settings.lastTargetFormat = target
                updateConvertEnabled()
            }
            if (!showMore) {
                val color = UiFormat.colorFor(target)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(color)
                chipStrokeWidth = 1.5f
            }
        }
    }

    private fun openFormatPicker() {
        val info = detected ?: return
        val sheet = FormatPickerSheet.newInstance(info.format, info.validTargets.toTypedArray())
        sheet.onSelected = { target ->
            selectedTarget = target
            app.settings.lastTargetFormat = target
            buildTargetChips(info)
            updateConvertEnabled()
        }
        sheet.show(parentFragmentManager, "format-picker")
    }

    // ── Output ─────────────────────────────────────────────────────────────

    private fun pickOutputFolder() {
        saf.launchFolderPicker { uri ->
            if (uri == null) return@launchFolderPicker
            val name = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(requireContext(), uri)
                ?.name
                ?: uri.lastPathSegment
                ?: uri.toString()
            app.settings.outputFolderUri = uri.toString()
            app.settings.outputFolderName = name
            renderOutputFolder()
            updateConvertEnabled()
        }
    }

    private fun restoreOutputFolder() {
        renderOutputFolder()
    }

    private fun renderOutputFolder() {
        val name = app.settings.outputFolderName
        binding.outputPath.text = name ?: getString(R.string.output_not_set)
    }

    private fun updateConvertEnabled() {
        val hasFile = detected?.let { !it.isUnknown } == true
        val hasTarget = !selectedTarget.isNullOrBlank()
        val hasOutput = app.settings.outputFolderUri != null
        binding.convertButton.isEnabled = hasFile && hasTarget && hasOutput
    }

    // ── Conversion ─────────────────────────────────────────────────────────

    private fun startConversion() {
        val uri = selectedUri ?: return
        val target = selectedTarget ?: return
        val outputFolder = app.settings.outputFolderUri ?: return
        val info = detected ?: return

        val job = ConversionJob(
            filepath = uri.toString(),
            outputDir = outputFolder,
            targetFormat = target,
            originalSizeBytes = info.sizeBytes,
        )

        val sheet = ProgressSheet.newInstance(selectedName ?: "input", target)
        sheet.show(parentFragmentManager, "progress")

        // The sheet owns the coroutine so its Cancel button can stop the job.
        sheet.start(
            job = job,
            runner = com.m5dev.openrom.core.ProcessRunner(java.io.File(app.filesDir, "bins")),
            binaryManager = app.binaryManager,
        )
    }

    private companion object {
        /** Above this count the remaining targets move into the picker sheet. */
        const val MAX_INLINE_CHIPS = 4
    }
}
