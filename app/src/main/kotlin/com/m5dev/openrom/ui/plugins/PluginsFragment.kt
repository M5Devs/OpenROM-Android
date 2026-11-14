// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.ui.plugins

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.m5dev.openrom.OpenRomApplication
import com.m5dev.openrom.R
import com.m5dev.openrom.databinding.FragmentPluginsBinding
import com.m5dev.openrom.databinding.ItemPluginBinding
import com.m5dev.openrom.plugin.InstalledPlugin
import com.m5dev.openrom.plugin.PluginInstallResult
import com.m5dev.openrom.plugin.StoreListing
import com.m5dev.openrom.storage.SafHelper
import com.m5dev.openrom.storage.UriResolver
import kotlinx.coroutines.launch

/**
 * Installed plugins, plus entry points to the store and to installing a local
 * `.orplug` archive.
 */
class PluginsFragment : Fragment() {

    private var _binding: FragmentPluginsBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: OpenRomApplication
    private lateinit var saf: SafHelper
    private lateinit var adapter: PluginAdapter

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
        _binding = FragmentPluginsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PluginAdapter { plugin -> confirmUninstall(plugin) }
        binding.pluginList.layoutManager = LinearLayoutManager(requireContext())
        binding.pluginList.adapter = adapter

        binding.browseStore.setOnClickListener { openStore() }
        binding.installFromFile.setOnClickListener { installFromFile() }

        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.pluginsLoading.isVisible = true
            val installed = app.pluginManager.loadInstalledPlugins()
            adapter.submit(installed)
            binding.pluginsLoading.isVisible = false
            binding.pluginsEmpty.isVisible = installed.isEmpty()
            binding.pluginList.isVisible = installed.isNotEmpty()
        }
    }

    /** Opens the storefront in a custom tab, never an in-app WebView. */
    private fun openStore() {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        try {
            intent.launchUrl(requireContext(), Uri.parse(app.pluginManager.storeUrl))
        } catch (_: Exception) {
            // No browser installed; nothing more we can do here.
        }
    }

    private fun installFromFile() {
        saf.launchFilePicker(arrayOf("application/zip", "application/octet-stream", "*/*")) { uri ->
            if (uri == null) return@launchFilePicker
            viewLifecycleOwner.lifecycleScope.launch {
                binding.pluginsLoading.isVisible = true
                val file = UriResolver.uriToTempFile(requireContext(), uri)
                val result = if (file != null) {
                    app.pluginManager.installFromFile(file)
                } else {
                    PluginInstallResult.Failure("Could not read the selected archive.")
                }
                binding.pluginsLoading.isVisible = false
                handleInstallResult(result)
                refresh()
            }
        }
    }

    /** Downloads and installs [listing] from the store, verifying its digest. */
    private fun installFromStore(listing: StoreListing) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.pluginsLoading.isVisible = true
            val result = app.pluginManager.installFromStore(listing)
            binding.pluginsLoading.isVisible = false
            handleInstallResult(result)
            refresh()
        }
    }

    private fun handleInstallResult(result: PluginInstallResult) {
        when (result) {
            is PluginInstallResult.Success -> android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.plugin_installed_fmt, result.manifest.name, result.manifest.version.toString()),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            is PluginInstallResult.Failure -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.conversion_failed)
                .setMessage(getString(R.string.plugin_install_failed, result.error))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun confirmUninstall(plugin: InstalledPlugin) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.uninstall_confirm, plugin.manifest.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.uninstall) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.pluginManager.uninstall(plugin.manifest.id)
                    refresh()
                }
            }
            .show()
    }

    private class PluginAdapter(
        private val onUninstall: (InstalledPlugin) -> Unit,
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<PluginAdapter.Holder>() {

        private val items = mutableListOf<InstalledPlugin>()

        fun submit(plugins: List<InstalledPlugin>) {
            items.clear()
            items.addAll(plugins)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemPluginBinding.inflate(LayoutInflater.from(parent.context), parent, false), onUninstall)

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        class Holder(
            private val binding: ItemPluginBinding,
            private val onUninstall: (InstalledPlugin) -> Unit,
        ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

            fun bind(plugin: InstalledPlugin) {
                val context = binding.root.context
                binding.pluginName.text = plugin.manifest.name
                binding.pluginMeta.text = context.getString(
                    R.string.plugin_installed_fmt,
                    plugin.manifest.publisher.name,
                    plugin.manifest.version.toString(),
                )
                binding.pluginBadge.text = context.getString(
                    if (plugin.verified) R.string.verified_badge else R.string.unverified_badge
                )

                val routes = plugin.manifest.routes.joinToString("  \u00b7  ") {
                    "${it.from} \u2192 ${it.to}"
                }
                binding.pluginRoutes.isVisible = routes.isNotEmpty()
                binding.pluginRoutes.text = routes

                binding.pluginUninstall.setOnClickListener { onUninstall(plugin) }
            }
        }
    }
}
