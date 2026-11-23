package com.hhst.youtubelite.downloader.io

/** Display names for Downloads/Puretube: keeps Unicode, strips OS-illegal chars, caps length, disambiguates collisions. */
object DownloadFileNames {
    private const val MAX_BASE = 180

    /**
     * Android caps a file name at 255 UTF-8 bytes, and the name on disk also carries the
     * extension, a " (n)" suffix and a pending marker. A 180-character cap alone let a
     * Japanese or Korean title (3 bytes a character) pass 255 bytes, and the download
     * then failed with "File name too long".
     */
    private const val MAX_BASE_BYTES = 180
    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun sanitize(raw: String, extension: String, existing: Set<String> = emptySet()): String {
        val ext = extension.trim().trimStart('.').lowercase()
        val extSuffix = if (ext.isEmpty()) "" else ".$ext"
        var base = raw.substringBeforeLast('.', raw)
            .replace(ILLEGAL, "_")
            .trim()
            .trim('.')
        if (base.isEmpty() || base.all { it == '_' || it == '.' || it == ' ' }) {
            base = "download"
        }
        if (base.length > MAX_BASE || utf8Length(base) > MAX_BASE_BYTES) {
            base = fitBytes(base, MAX_BASE, MAX_BASE_BYTES).trimEnd('.', ' ')
        }
        val candidate = base + extSuffix
        if (candidate.lowercase() !in existing.map { it.lowercase() }.toSet()) return candidate
        var n = 1
        while (n < 10_000) {
            val numbered = "$base ($n)$extSuffix"
            if (numbered.lowercase() !in existing.map { it.lowercase() }.toSet()) return numbered
            n++
        }
        return "$base (${System.nanoTime()})$extSuffix"
    }

    /** Longest prefix within both caps that never splits a character (emoji included). */
    private fun fitBytes(text: String, maxChars: Int, maxBytes: Int): String {
        var end = 0
        var bytes = 0
        while (end < text.length) {
            val cp = text.codePointAt(end)
            val width = Character.charCount(cp)
            val size = utf8Length(String(Character.toChars(cp)))
            if (end + width > maxChars || bytes + size > maxBytes) break
            end += width
            bytes += size
        }
        return text.substring(0, end)
    }

    private fun utf8Length(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    fun extensionOf(name: String, fallback: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.ifBlank { fallback }
    }
}
