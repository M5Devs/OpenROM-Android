// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom.core

import com.m5dev.openrom.plugin.PluginArg
import com.m5dev.openrom.plugin.PluginArgKind
import com.m5dev.openrom.plugin.PluginRegistry
import java.io.File
import java.io.InputStream

/**
 * Format detection and the built-in conversion table.
 *
 * Port of the desktop `detector.dart`. The maps below are the source of truth
 * for format names and badge colours and must stay byte-identical to the
 * desktop repo; plugin-registered entries are merged on top at runtime.
 */
object FormatDetector {

    // ── Built-in tables (verbatim from detector.dart) ───────────────────────

    val builtinSupportedInput: Map<String, String> = mapOf(
        ".iso" to "ISO",
        ".bin" to "BIN",
        ".cue" to "CUE",
        ".gdi" to "GDI",
        ".cdi" to "CDI",
        ".img" to "IMG",
        ".ecm" to "ECM",
        ".chd" to "CHD",
        ".cso" to "CSO",
        ".zso" to "ZSO",
        ".rvz" to "RVZ",
        ".wia" to "WIA",
        ".wbfs" to "WBFS",
        ".wud" to "WUD",
        ".wux" to "WUX",
        ".wua" to "WUA",
        ".nkit.iso" to "NKIT",
        ".gcz" to "GCZ",
        ".cci" to "CCI",
        ".zar" to "ZAR",
    )

    val builtinPlatformMap: Map<String, String> = mapOf(
        "ISO" to "ISO Image",
        "BIN" to "CD Image",
        "CUE" to "CD Cue Sheet",
        "GDI" to "Dreamcast GDI",
        "CDI" to "Dreamcast CDI",
        "IMG" to "Disk Image",
        "ECM" to "ECM Compressed",
        "CHD" to "CHD Archive",
        "CSO" to "Compressed ISO",
        "ZSO" to "Compressed ISO",
        "XISO" to "Xbox ISO",
        "CCI" to "Xbox 360 CCI",
        "ZAR" to "Xbox 360 ZAR",
        "RVZ" to "GameCube / Wii",
        "WIA" to "GameCube / Wii",
        "WBFS" to "Wii",
        "GCZ" to "GameCube / Wii",
        "WUD" to "Wii U",
        "WUX" to "Wii U",
        "WUA" to "Wii U (ZArchive)",
        "NKIT" to "GameCube / Wii",
        "Saturn" to "Sega Saturn",
        "Sega CD" to "Sega CD / Mega-CD",
        "PC-Engine CD" to "PC-Engine CD / TurboGrafx-CD",
        "Neo Geo CD" to "Neo Geo CD",
    )

    val builtinFormatColors: Map<String, String> = mapOf(
        "ISO" to "#e94560",
        "BIN" to "#f9a825",
        "CUE" to "#f9a825",
        "GDI" to "#9c27b0",
        "CDI" to "#7b1fa2",
        "IMG" to "#673ab7",
        "CHD" to "#00bcd4",
        "CSO" to "#4caf50",
        "ZSO" to "#4caf50",
        "ECM" to "#ff9800",
        "XISO" to "#e91e63",
        "CCI" to "#4caf50",
        "ZAR" to "#388e3c",
        "RVZ" to "#3f51b5",
        "WIA" to "#5c6bc0",
        "WBFS" to "#7986cb",
        "GCZ" to "#9fa8da",
        "WUD" to "#e65100",
        "WUX" to "#ef6c00",
        "WUA" to "#e65100",
        "NKIT" to "#1a237e",
        "UNKNOWN" to "#7a8a9a",
        "Saturn" to "#1565c0",
        "Sega CD" to "#4a148c",
        "PC-Engine CD" to "#bf360c",
        "Neo Geo CD" to "#b71c1c",
    )

    /**
     * Routes handled natively by the bundled core utilities. XISO, Xbox 360
     * (CCI/ZAR), NKit and Wii U (WUD/WUX/WUA) routes were decoupled in v4.0.0
     * and are served by community `.orplug` extensions, so they are absent
     * here — exactly as in the desktop core.
     */
    val builtinConversionMap: Map<String, List<String>> = mapOf(
        "ISO" to listOf("CHD", "CSO", "ECM", "RVZ"),
        "BIN" to listOf("CHD", "ECM"),
        "CUE" to listOf("CHD"),
        "GDI" to listOf("CHD"),
        "CDI" to listOf("CHD"),
        "IMG" to listOf("CHD"),
        "CHD" to listOf("ISO", "BIN/CUE"),
        "CSO" to listOf("ISO"),
        "ZSO" to listOf("ISO"),
        "ECM" to listOf("ISO", "BIN"),
        "RVZ" to listOf("ISO"),
        "WIA" to listOf("ISO"),
        "WBFS" to listOf("ISO"),
        "GCZ" to listOf("ISO"),
    )

    val builtinCommandTemplates: Map<String, String> = mapOf(
        "ISO->CHD" to "chdman createdvd -i \"{in}\" -o \"{out}\"",
        "ISO->CSO" to "maxcso \"{in}\" -o \"{out}\"",
        "ISO->ECM" to "ecm \"{in}\" \"{out}\"",
        "ISO->RVZ" to "nodtool convert \"{in}\" \"{out}\"",
        "BIN->CHD" to "chdman createcd -i \"{cue}\" -o \"{out}\"",
        "BIN->ECM" to "ecm \"{in}\" \"{out}\"",
        "CUE->CHD" to "chdman createcd -i \"{in}\" -o \"{out}\"",
        "GDI->CHD" to "chdman createcd -i \"{in}\" -o \"{out}\"",
        "CDI->CHD" to "chdman createcd -i \"{in}\" -o \"{out}\"",
        "IMG->CHD" to "chdman createdvd -i \"{in}\" -o \"{out}\"",
        "CHD->ISO" to "chdman extractdvd -i \"{in}\" -o \"{out}\"",
        "CHD->BIN/CUE" to "chdman extractcd -i \"{in}\" -o \"{out_cue}\"",
        "CHD->BIN" to "chdman extractcd -i \"{in}\" -o \"{out_cue}\"",
        "CSO->ISO" to "maxcso --decompress \"{in}\" -o \"{out}\"",
        "ZSO->ISO" to "maxcso --decompress \"{in}\" -o \"{out}\"",
        "ECM->ISO" to "unecm \"{in}\" \"{out}\"",
        "ECM->BIN" to "unecm \"{in}\" \"{out}\"",
        "RVZ->ISO" to "nodtool convert \"{in}\" \"{out}\"",
        "WIA->ISO" to "nodtool convert \"{in}\" \"{out}\"",
        "WBFS->ISO" to "nodtool convert \"{in}\" \"{out}\"",
        "GCZ->ISO" to "nodtool convert \"{in}\" \"{out}\"",
    )

    // ── Android tool availability ──────────────────────────────────────────

    /**
     * Tools that publish an `android/arm64-v8a` build. `nodtool`,
     * `saturn-patcher` and `xgdtool` do not, so every route requiring them is
     * hidden rather than offered and then failing at run time.
     */
    val androidAvailableTools: Set<String> =
        setOf("chdman", "ecm", "unecm", "maxcso", "xdelta3")

    /**
     * Supplier of the tools currently verified on disk. [BinaryManager] narrows
     * this once it has checksummed the extracted binaries; until then the
     * optimistic Android set is used so the UI can render.
     */
    @Volatile
    var availableToolsProvider: () -> Set<String> = { androidAvailableTools }

    /** The tool a built-in route shells out to, derived from its template. */
    fun requiredToolForRoute(from: String, to: String): String? =
        builtinCommandTemplates["${from.uppercase()}->${to.uppercase()}"]
            ?.substringBefore(' ')
            ?.takeIf { it.isNotBlank() }

    /** True when every tool this route needs is verified on this device. */
    fun isRouteAvailable(from: String, to: String): Boolean {
        val tool = requiredToolForRoute(from, to) ?: return true
        return availableToolsProvider().contains(tool)
    }

    // ── Plugin-merged tables ───────────────────────────────────────────────

    /** Supported input extensions, with plugin-registered ones merged in. */
    fun supportedInput(): Map<String, String> {
        val merged = LinkedHashMap(builtinSupportedInput)
        merged.putAll(PluginRegistry.pluginSupportedInput)
        return merged
    }

    fun platformMap(): Map<String, String> {
        val merged = LinkedHashMap(builtinPlatformMap)
        merged.putAll(PluginRegistry.pluginPlatformMap)
        return merged
    }

    fun formatColors(): Map<String, String> {
        val merged = LinkedHashMap(builtinFormatColors)
        merged.putAll(PluginRegistry.pluginFormatColors)
        return merged
    }

    /** Conversion table with plugin routes appended (never duplicating). */
    fun conversionMap(): Map<String, List<String>> {
        val merged = LinkedHashMap<String, MutableList<String>>()
        builtinConversionMap.forEach { (k, v) -> merged[k] = v.toMutableList() }
        PluginRegistry.pluginConversionMap.forEach { (from, targets) ->
            val list = merged.getOrPut(from) { mutableListOf() }
            targets.forEach { if (!list.contains(it)) list.add(it) }
        }
        return merged
    }

    /**
     * Human-readable command previews. Built-in templates are shown verbatim;
     * plugin routes are rendered from their structured argument vector.
     */
    fun commandTemplates(): Map<String, String> {
        val merged = LinkedHashMap(builtinCommandTemplates)
        PluginRegistry.activePlugins.forEach { plugin ->
            plugin.routes.forEach { route ->
                val key = "${route.from.uppercase()}->${route.to.uppercase()}"
                merged[key] = renderArgsPreview(plugin.execution.args)
            }
        }
        return merged
    }

    /** Display-only rendering of a plugin's structured argv (never executed). */
    fun renderArgsPreview(args: List<PluginArg>): String = args.joinToString(" ") { arg ->
        when (arg.kind) {
            PluginArgKind.executable -> "\"{bin}\""
            PluginArgKind.input -> "\"{in}\""
            PluginArgKind.output -> "\"{out}\""
            PluginArgKind.outputDir -> "\"{outdir}\""
            PluginArgKind.cue -> "\"{cue}\""
            PluginArgKind.inputName -> "\"{in_name}\""
            PluginArgKind.outputName -> "\"{out_name}\""
            PluginArgKind.literal -> if (arg.value.contains(' ')) "\"${arg.value}\"" else arg.value
        }
    }

    // ── Name helpers ───────────────────────────────────────────────────────

    /**
     * Longest known extension match, so `.nkit.iso` wins over `.iso`. Returns
     * the extension without its leading dot, or the text after the last dot.
     */
    fun getExtension(filename: String): String {
        val nameLower = filename.lowercase()
        val allKnown = supportedInput().keys.sortedByDescending { it.length }
        for (extKey in allKnown) {
            if (nameLower.endsWith(extKey)) {
                return extKey.removePrefix(".")
            }
        }
        val parts = nameLower.split('.')
        return if (parts.size > 1) parts.last() else ""
    }

    fun getOutputName(inputFile: String, newExt: String): String {
        val cleanExt = newExt.removePrefix(".")
        val currentExt = getExtension(inputFile)
        val extWithDot = ".$currentExt"
        val base = if (inputFile.lowercase().endsWith(extWithDot.lowercase())) {
            inputFile.substring(0, inputFile.length - extWithDot.length)
        } else {
            val idx = inputFile.lastIndexOf('.')
            if (idx != -1) inputFile.substring(0, idx) else inputFile
        }
        if (base.lowercase().endsWith(".${cleanExt.lowercase()}")) return base
        return "$base.$cleanExt"
    }

    fun getBadgeColor(fmt: String): String =
        formatColors()[fmt.uppercase()] ?: formatColors()["UNKNOWN"] ?: "#7a8a9a"

    /**
     * Targets for [fmt], with routes whose tool is unavailable on this device
     * filtered out. Plugin routes are always offered once installed.
     */
    fun getValidTargets(fmt: String): List<String> {
        val from = fmt.uppercase()
        val targets = conversionMap()[from] ?: return emptyList()
        return targets.filter { target ->
            val isPluginRoute = PluginRegistry.hasRoute(from, target)
            isPluginRoute || isRouteAvailable(from, target)
        }
    }

    fun getCommandPreview(fmt: String, target: String, filename: String = "game.iso"): String {
        val key = "${fmt.uppercase()}->${target.uppercase()}"
        val template = commandTemplates()[key] ?: return "$fmt -> $target"
        val outName = getOutputName(filename, target.lowercase().replace("/cue", ""))
        val idx = filename.lastIndexOf('.')
        val base = if (idx != -1) filename.substring(0, idx) else filename
        val cueName = "$base.cue"
        return template
            .replace("{in}", filename)
            .replace("{out}", outName)
            .replace("{cue}", cueName)
            .replace("{out_cue}", cueName)
    }

    /**
     * Extracts a usable filename from a `content://` URI string. SAF document
     * URIs carry the display name as the last path segment, percent-encoded
     * (e.g. `primary%3AROMs%2Fgame.iso`).
     */
    fun extractNameFromUri(uriString: String): String? {
        if (!uriString.startsWith("content://")) return null
        val segment = uriString.substringAfterLast('/').substringBefore('?')
        if (segment.isBlank()) return null
        val decoded = runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }
            .getOrDefault(segment)
        val name = decoded.substringAfterLast('/').substringAfterLast('\\')
        return name.ifBlank { null }
    }

    // ── Magic bytes ────────────────────────────────────────────────────────

    private val chdMagic = "MComprHD".toByteArray(Charsets.US_ASCII) // MComprHD
    private val ps2Magic = "PLAYSTATION".toByteArray(Charsets.US_ASCII)
    private val ps1Magic = "PlayStation".toByteArray(Charsets.US_ASCII)
    private val ps1MagicUpper = "PLAYSTATION".toByteArray(Charsets.US_ASCII)
    private const val GC_MAGIC = 0xC2339F3D.toInt()
    private const val WII_MAGIC = 0x5D1C9EA3.toInt()
    private val saturnMagic1 = "SEGA SEGASATURN".toByteArray(Charsets.US_ASCII)
    private val saturnMagic2 = "SEGA SATURN ".toByteArray(Charsets.US_ASCII)
    private val dreamcastMagic = "SEGA SEGAKATANA".toByteArray(Charsets.US_ASCII)
    private val segacdMagic1 = "SEGADISCSYSTEM".toByteArray(Charsets.US_ASCII)
    private val segacdMagic2 = "SEGA_CD".toByteArray(Charsets.US_ASCII)
    private val pcecdMagic = "PC Engine CD-ROM SYSTEM".toByteArray(Charsets.US_ASCII)
    private val neogeocdMagic = "NEO-GEO CD".toByteArray(Charsets.US_ASCII)
    private val xboxMagic = "MICROSOFT*XBOX*MEDIA".toByteArray(Charsets.US_ASCII)
    private const val XBOX_OFFSET_1 = 0x10000
    private const val XBOX_OFFSET_2 = 0x2090000

    /** How many leading bytes are needed for every magic check. */
    const val HEADER_READ_SIZE = 0x210000

    private fun containsSublist(list: ByteArray, sublist: ByteArray): Boolean {
        if (sublist.isEmpty() || list.size < sublist.size) return false
        for (i in 0..(list.size - sublist.size)) {
            var match = true
            for (j in sublist.indices) {
                if (list[i + j] != sublist[j]) { match = false; break }
            }
            if (match) return true
        }
        return false
    }

    private fun sublistEquals(list: ByteArray, offset: Int, sublist: ByteArray): Boolean {
        if (offset < 0 || offset + sublist.size > list.size) return false
        for (i in sublist.indices) {
            if (list[offset + i] != sublist[i]) return false
        }
        return true
    }

    private fun headerHasXboxMagic(h: ByteArray) = sublistEquals(h, XBOX_OFFSET_1, xboxMagic)

    private fun headerHasPspMagic(h: ByteArray): Boolean {
        val slice = h.copyOfRange(0, minOf(h.size, 65536))
        return containsSublist(slice, "PSP_GAME".toByteArray(Charsets.US_ASCII)) ||
            containsSublist(slice, "UMD_DATA".toByteArray(Charsets.US_ASCII))
    }

    private fun headerHasGcMagic(h: ByteArray): Boolean =
        if (h.size >= 0x20) uint32be(h, 0x1C) == GC_MAGIC else false

    private fun headerHasWiiMagic(h: ByteArray): Boolean =
        if (h.size >= 0x1C) uint32be(h, 0x18) == WII_MAGIC else false

    private fun uint32be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun headerHasPs2Magic(h: ByteArray): Boolean {
        if (h.size < 0x8800) return false
        return containsSublist(h.copyOfRange(0x8000, 0x8800), ps2Magic)
    }

    private fun headerHasPs1Magic(h: ByteArray): Boolean {
        val slice = h.copyOfRange(0, minOf(h.size, 65536))
        return containsSublist(slice, ps1Magic) || containsSublist(slice, ps1MagicUpper)
    }

    private fun headerHasDreamcastMagic(h: ByteArray): Boolean =
        containsSublist(h.copyOfRange(0, minOf(h.size, 65536)), dreamcastMagic)

    private fun headerHasSaturnMagic(h: ByteArray): Boolean =
        sublistEquals(h, 0x10, saturnMagic1) || sublistEquals(h, 0x00, saturnMagic2)

    private fun headerHasSegacdMagic(h: ByteArray): Boolean =
        sublistEquals(h, 0x10, segacdMagic1) || sublistEquals(h, 0x00, segacdMagic2)

    private fun headerHasPcecdMagic(h: ByteArray): Boolean =
        containsSublist(h.copyOfRange(0, minOf(h.size, 2048)), pcecdMagic)

    private fun headerHasNeogeocdMagic(h: ByteArray): Boolean =
        containsSublist(h.copyOfRange(0, minOf(h.size, 0x800)), neogeocdMagic)

    /** Discriminates CD vs DVD CHD images from the header, falling back to size. */
    fun readChdType(raw: ByteArray, fileSize: Long): String {
        if (raw.size < 16 || !sublistEquals(raw, 0, chdMagic)) return chdTypeBySize(fileSize)
        val version = uint32be(raw, 12)
        if (version == 5 && raw.size >= 64) {
            val unitBytes = uint32be(raw, 60)
            return when (unitBytes) {
                2448 -> "cd"
                512, 2048, 4096 -> "dvd"
                else -> chdTypeBySize(fileSize)
            }
        }
        if (version == 4 && raw.size >= 108) {
            val flags = uint32be(raw, 16)
            return if ((flags and 0x2) != 0) "cd" else "dvd"
        }
        return chdTypeBySize(fileSize)
    }

    private fun chdTypeBySize(size: Long): String =
        if (size / (1024.0 * 1024.0) < 900) "cd" else "dvd"

    // ── Platform guessing ──────────────────────────────────────────────────

    private fun guessPlatform(name: String, fmt: String, size: Long, header: ByteArray): String {
        when (fmt) {
            "CCI", "ZAR" -> return "Xbox 360"
            "GDI", "CDI" -> return "Dreamcast"
            "CSO", "ZSO" -> return if (size < 2L * 1024 * 1024 * 1024) "PSP" else "PSP / PS2"
            "XISO" -> return "Xbox"
        }

        if (fmt == "ISO" || fmt == "BIN" || fmt == "IMG") {
            if ((fmt == "ISO" || fmt == "IMG") && headerHasXboxMagic(header)) return "Xbox"
            if ((fmt == "ISO" || fmt == "IMG") && headerHasPspMagic(header)) return "PSP"
            if ((fmt == "ISO" || fmt == "IMG") && headerHasGcMagic(header)) return "GameCube"
            if ((fmt == "ISO" || fmt == "IMG") && headerHasWiiMagic(header)) return "Wii"
            if (headerHasSaturnMagic(header)) return "Saturn"
            if (headerHasSegacdMagic(header)) return "Sega CD"
            if (headerHasPcecdMagic(header)) return "PC-Engine CD"
            if (headerHasNeogeocdMagic(header)) return "Neo Geo CD"
            if (headerHasPs2Magic(header)) return "PS2"
            if (headerHasPs1Magic(header)) return "PS1"
            return "UNKNOWN"
        }

        return platformMap()[fmt] ?: "ROM File"
    }

    /** Magic-based platform probe for a raw disc image, used for CUE sheets. */
    fun guessPlatformFromImageHeader(header: ByteArray): String? {
        if (header.isEmpty()) return null
        if (headerHasDreamcastMagic(header)) return "Dreamcast"
        if (headerHasSaturnMagic(header)) return "Saturn"
        if (headerHasSegacdMagic(header)) return "Sega CD"
        if (headerHasPcecdMagic(header)) return "PC-Engine CD"
        if (headerHasNeogeocdMagic(header)) return "Neo Geo CD"
        if (headerHasPs2Magic(header)) return "PS2"
        if (headerHasPs1Magic(header)) return "PS1"
        return null
    }

    /** Binary (1024-based) human size, e.g. `45.0 GB`. Sign is preserved. */
    fun humanBytes(size: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = kotlin.math.abs(size).toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) { value /= 1024; i++ }
        val text = "${"%.1f".format(java.util.Locale.US, value)} ${units[i]}"
        return if (size < 0) "-$text" else text
    }

    // ── Detection ──────────────────────────────────────────────────────────

    /**
     * Core detection over a display name, byte size and leading header bytes.
     * Pure and Android-free so it is directly unit-testable.
     */
    fun detect(
        name: String,
        sizeBytes: Long,
        header: ByteArray = ByteArray(0),
        chdTypeOverride: String? = null,
        pairedCue: String? = null,
        pairedBin: String? = null,
        platformOverride: String? = null,
    ): DetectedFormat {
        val rawExt = getExtension(name)
        val ext = if (rawExt.startsWith(".")) rawExt else ".$rawExt"
        val fmt = supportedInput()[ext] ?: "UNKNOWN"
        val chdType = if (fmt == "CHD") {
            chdTypeOverride ?: readChdType(header, sizeBytes)
        } else {
            null
        }
        val platform = platformOverride
            ?: if (fmt == "CUE" && pairedBin == null) "UNKNOWN"
            else guessPlatform(name, fmt, sizeBytes, header)

        return DetectedFormat(
            format = fmt,
            platform = platform,
            sizeBytes = sizeBytes,
            sizeStr = humanBytes(sizeBytes),
            needsEcmDecode = fmt == "ECM",
            pairedCue = if (fmt == "BIN") pairedCue else null,
            pairedBin = if (fmt == "CUE") pairedBin else null,
            chdType = chdType,
            validTargets = getValidTargets(fmt),
            badgeColor = getBadgeColor(fmt),
        )
    }

    /**
     * Reads up to [limit] bytes without consuming the whole stream. Uses a
     * bounded read loop because `InputStream.readNBytes` requires API 33.
     */
    fun readHeader(stream: InputStream, limit: Int = HEADER_READ_SIZE): ByteArray {
        return try {
            stream.use { input ->
                val buffer = ByteArray(limit)
                var total = 0
                while (total < limit) {
                    val read = input.read(buffer, total, limit - total)
                    if (read <= 0) break
                    total += read
                }
                if (total == limit) buffer else buffer.copyOf(total)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    /** Detects a local file, including its paired CUE/BIN and CHD subtype. */
    fun detectFile(file: File): DetectedFormat {
        if (!file.exists()) {
            return DetectedFormat.unknown(file.name, "File not found: ${file.path}")
        }
        val size = file.length()
        val header = readHeader(file.inputStream())
        return detect(
            name = file.name,
            sizeBytes = size,
            header = header,
            pairedCue = findPair(file.path, ".cue"),
            pairedBin = findPair(file.path, ".bin"),
            platformOverride = if (getExtension(file.name) == "cue") cuePlatform(file) else null,
        )
    }

    /**
     * Platform of a CUE sheet, read from its referenced BIN image: a cue sheet
     * carries no platform data of its own.
     */
    private fun cuePlatform(cueFile: File): String? {
        val image = cueReferencedImage(cueFile) ?: return null
        val header = readHeader(image.inputStream(), 0x10000)
        return guessPlatformFromImageHeader(header)
    }

    /**
     * Detects a `content://` (or `file://`) URI without assuming direct file
     * access. [name], [sizeBytes] and [header] come from the caller because
     * only a `ContentResolver` can read them.
     */
    fun detectUri(
        uriString: String,
        name: String?,
        sizeBytes: Long,
        header: ByteArray = ByteArray(0),
    ): DetectedFormat {
        val resolvedName = name ?: extractNameFromUri(uriString) ?: uriString
        return detect(resolvedName, sizeBytes, header)
    }

    /** Sibling file with [targetExt] next to [filepath], when it exists. */
    fun findPair(filepath: String, targetExt: String): String? {
        val idx = filepath.lastIndexOf('.')
        val base = if (idx != -1) filepath.substring(0, idx) else filepath
        val candidate = base + if (targetExt.startsWith(".")) targetExt else ".$targetExt"
        return if (File(candidate).exists()) candidate else null
    }

    /**
     * Reads the `FILE` entry from a CUE sheet and resolves the referenced image
     * relative to the CUE's directory. Quotes, spaces and apostrophes are
     * preserved. Returns `null` when nothing readable is referenced.
     */
    fun cueReferencedImage(cueFile: File): File? {
        val content = try {
            cueFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        } catch (_: Exception) {
            return null
        }
        val regex = Regex(
            """^\s*FILE\s+(.+?)\s+(BINARY|WAVE|MP3|AIFF|MOTOROLA)\s*$""",
            RegexOption.IGNORE_CASE,
        )
        for (rawLine in content.split(Regex("\r\n|\r|\n"))) {
            val match = regex.find(rawLine.trim()) ?: continue
            var name = match.groupValues[1].trim()
            if (name.length >= 2) {
                val first = name.first()
                val last = name.last()
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    name = name.substring(1, name.length - 1)
                }
            }
            if (name.isEmpty()) continue
            val resolved = File(cueFile.parentFile, name)
            if (resolved.exists()) return resolved
        }
        return null
    }
}

/** Result of a format probe; mirrors the desktop `detectFile` payload. */
data class DetectedFormat(
    val format: String,
    val platform: String,
    val sizeBytes: Long,
    val sizeStr: String,
    val needsEcmDecode: Boolean,
    val pairedCue: String?,
    val pairedBin: String?,
    val chdType: String?,
    val validTargets: List<String>,
    val badgeColor: String,
    val error: String? = null,
) {
    val isUnknown: Boolean get() = format == "UNKNOWN"

    companion object {
        fun unknown(name: String, error: String? = null) = DetectedFormat(
            format = "UNKNOWN",
            platform = "UNKNOWN",
            sizeBytes = 0,
            sizeStr = "0.0 B",
            needsEcmDecode = false,
            pairedCue = null,
            pairedBin = null,
            chdType = null,
            validTargets = emptyList(),
            badgeColor = FormatDetector.getBadgeColor("UNKNOWN"),
            error = error,
        )
    }
}
