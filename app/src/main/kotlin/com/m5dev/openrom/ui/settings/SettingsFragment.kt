// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.m5dev.openrom.BuildConfig
import com.m5dev.openrom.OpenRomApplication
import com.m5dev.openrom.R
import com.m5dev.openrom.databinding.FragmentSettingsBinding
import com.m5dev.openrom.storage.SafHelper
import kotlinx.coroutines.launch

/** Output folder, binary-tool cache and about information. */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: OpenRomApplication
    private lateinit var saf: SafHelper

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.settingsOutputCard.setOnClickListener { pickOutputFolder() }
        binding.settingsClearBinaries.setOnClickListener { clearBinaryCache() }

        binding.settingsVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        renderOutputFolder()
        renderBinaryStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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
        }
    }

    private fun renderOutputFolder() {
        binding.settingsOutputValue.text =
            app.settings.outputFolderName ?: getString(R.string.output_not_set)
    }

    private fun renderBinaryStatus() {
        val total = app.binsManifest.toolNames().size
        val ready = app.lastBinaryResult?.ready?.size ?: 0
        binding.settingsBinariesStatus.text = getString(R.string.binaries_ready, ready, total)
    }

    private fun clearBinaryCache() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.binaryManager.clearCache()
            Toast.makeText(requireContext(), R.string.binary_cache_cleared, Toast.LENGTH_SHORT).show()
            app.prepareBinaries()
            renderBinaryStatus()
        }
    }
}
