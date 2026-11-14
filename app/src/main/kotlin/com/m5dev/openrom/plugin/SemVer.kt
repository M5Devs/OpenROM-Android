// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.plugin

/**
 * Minimal SemVer used for plugin version and `minCoreVersion` gates.
 *
 * Comparison follows SemVer 2.0.0 precedence: major, then minor, then patch,
 * then pre-release. A version with a pre-release suffix sorts *before* the
 * same version without one (`1.0.0-alpha < 1.0.0`).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String = "",
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch
        if (preRelease == other.preRelease) return 0
        // A version with a pre-release tag has lower precedence.
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        return preRelease.compareTo(other.preRelease)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-$preRelease"

    companion object {
        private val PATTERN = Regex(
            """^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$"""
        )

        /** Parses a strict `x.y.z[-pre]` version, or returns `null`. */
        fun tryParse(value: String?): SemVer? {
            if (value.isNullOrBlank()) return null
            val match = PATTERN.find(value.trim()) ?: return null
            return SemVer(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                preRelease = match.groupValues[4],
            )
        }

        /**
         * Tolerant parse for `minCoreVersion`, which may carry a leading `v`
         * or omit trailing components (`"3.9"`).
         */
        fun tryParseLoose(value: String?): SemVer? {
            if (value.isNullOrBlank()) return null
            val cleaned = value.trim().removePrefix("v").removePrefix("V")
            tryParse(cleaned)?.let { return it }
            val parts = cleaned.split('.', '-')
            val nums = parts.take(3).mapNotNull { it.toIntOrNull() }
            if (nums.isEmpty()) return null
            return SemVer(
                major = nums.getOrElse(0) { 0 },
                minor = nums.getOrElse(1) { 0 },
                patch = nums.getOrElse(2) { 0 },
            )
        }
    }
}
