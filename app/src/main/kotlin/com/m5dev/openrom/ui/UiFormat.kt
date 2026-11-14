// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.ui

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import com.m5dev.openrom.core.FormatDetector

/** Shared UI helpers for format badges and colour parsing. */
object UiFormat {

    /** Parses a `#rrggbb` string, falling back to the unknown-format colour. */
    fun parseColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: IllegalArgumentException) {
        Color.parseColor("#7a8a9a")
    }

    /** Badge colour for [format], from the merged format colour table. */
    fun colorFor(format: String): Int =
        parseColor(FormatDetector.getBadgeColor(format.uppercase()))

    /**
     * Styles [chip] as a format badge: the format's colour as a translucent
     * fill with matching text, which stays legible on the dark surface.
     */
    fun styleBadge(context: Context, chip: Chip, format: String) {
        val base = colorFor(format)
        chip.text = format.uppercase()
        chip.setTextColor(base)
        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(base, 48)
        )
        chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(base, 160)
        )
        chip.chipStrokeWidth = 1f
        chip.isCheckable = false
        chip.isClickable = false
        chip.setEnsureMinTouchTargetSize(false)
    }

    /** Applies a format colour to a plain [TextView] (used in list rows). */
    fun tintBadgeText(view: TextView, format: String) {
        view.text = format.uppercase()
        view.setTextColor(colorFor(format))
    }
}
