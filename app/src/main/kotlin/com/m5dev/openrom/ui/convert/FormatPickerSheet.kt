// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.ui.convert

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.m5dev.openrom.R
import com.m5dev.openrom.core.FormatDetector
import com.m5dev.openrom.databinding.ItemFormatHeaderBinding
import com.m5dev.openrom.databinding.ItemFormatOptionBinding
import com.m5dev.openrom.databinding.SheetFormatPickerBinding
import com.m5dev.openrom.plugin.PluginRegistry
import com.m5dev.openrom.ui.UiFormat

/**
 * Scrollable list of every conversion target, grouped into built-in routes and
 * plugin-provided routes. Shown when the inline chip row cannot fit them all.
 */
class FormatPickerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFormatPickerBinding? = null
    private val binding get() = _binding!!

    /** Invoked with the chosen target format. */
    var onSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetFormatPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val from = requireArguments().getString(ARG_FROM) ?: ""
        val targets = requireArguments().getStringArrayList(ARG_TARGETS) ?: arrayListOf()
        val fileName = requireArguments().getString(ARG_FILE) ?: "game.iso"

        binding.formatList.layoutManager = LinearLayoutManager(requireContext())
        binding.formatList.adapter = FormatAdapter(from, targets, fileName) { target ->
            onSelected?.invoke(target)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_FROM = "from"
        const val ARG_TARGETS = "targets"
        const val ARG_FILE = "file"

        fun newInstance(from: String, targets: Array<String>, fileName: String = "game.iso") =
            FormatPickerSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_FROM, from)
                    putStringArrayList(ARG_TARGETS, ArrayList(targets.toList()))
                    putString(ARG_FILE, fileName)
                }
            }
    }
    /** Row model: either a group header or a selectable target. */
    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Option(val target: String, val preview: String) : Row()
    }

    private class FormatAdapter(
        from: String,
        targets: List<String>,
        fileName: String,
        private val onClick: (String) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows: List<Row> = buildRows(from, targets, fileName)

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_OPTION

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderHolder(ItemFormatHeaderBinding.inflate(inflater, parent, false))
            } else {
                OptionHolder(ItemFormatOptionBinding.inflate(inflater, parent, false), onClick)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).bind(row.title)
                is Row.Option -> (holder as OptionHolder).bind(row)
            }
        }

        override fun getItemCount(): Int = rows.size

        private companion object {
            const val TYPE_HEADER = 0
            const val TYPE_OPTION = 1

            /**
             * Splits targets into built-in and plugin groups, preserving the
             * order the detector reported.
             */
            fun buildRows(from: String, targets: List<String>, fileName: String): List<Row> {
                val pluginTargets = targets.filter { PluginRegistry.hasRoute(from, it) }
                val builtinTargets = targets.filterNot { PluginRegistry.hasRoute(from, it) }

                val rows = mutableListOf<Row>()
                if (builtinTargets.isNotEmpty()) {
                    rows.add(Row.Header("builtin"))
                    builtinTargets.forEach { target ->
                        rows.add(Row.Option(target, FormatDetector.getCommandPreview(from, target, fileName)))
                    }
                }
                if (pluginTargets.isNotEmpty()) {
                    rows.add(Row.Header("plugins"))
                    pluginTargets.forEach { target ->
                        val preview = PluginRegistry.getConversion(from, target)
                            ?.manifest?.name ?: FormatDetector.getCommandPreview(from, target, fileName)
                        rows.add(Row.Option(target, preview))
                    }
                }
                return rows
            }
        }
    }

    private class HeaderHolder(private val binding: ItemFormatHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerText.setText(
                if (title == "plugins") R.string.group_plugins else R.string.group_builtin
            )
        }
    }

    private class OptionHolder(
        private val binding: ItemFormatOptionBinding,
        private val onClick: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: Row.Option) {
            UiFormat.styleBadge(binding.root.context, binding.optionChip, row.target)
            binding.optionPreview.text = row.preview
            binding.root.setOnClickListener { onClick(row.target) }
        }
    }
}
