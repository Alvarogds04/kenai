package com.kenai.android.core

/**
 * A run of text with the few styles a chat answer needs. [link] is the target of a `[text](target)` link; it is
 * only ever an http or https address, and the app opens it only after a tap, showing the target first.
 */
data class MdSpan(val text: String, val bold: Boolean = false, val code: Boolean = false, val link: String? = null)

sealed interface MdBlock {
    data class Para(val spans: List<MdSpan>) : MdBlock
    /** [level] (1 to 6) is kept for a renderer that wants it; the chat window is small, so the screen draws every level as one bold line. */
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Bullet(val spans: List<MdSpan>, val depth: Int = 0) : MdBlock
    data class Numbered(val number: Int, val spans: List<MdSpan>, val depth: Int = 0) : MdBlock
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
    data class Code(val text: String) : MdBlock
}

/**
 * The Markdown the computer's chat shows: paragraphs, headings, bullet and numbered lists (nested by indent),
 * tables, **bold**, `inline code`, http(s) links and fenced code blocks. Images and HTML stay plain text, and a
 * link whose target is not http or https stays as the characters it is, so an answer can never make the app
 * open or load something by itself. Safe on half an answer: a fence, table or marker that has not closed yet is
 * shown as it is until the rest arrives.
 */
object MarkdownLite {
    const val MAX_DEPTH = 5
    private val NUMBERED = Regex("^(\\d{1,9})[.)] (.+)$")
    private val SEPARATOR_CELL = Regex("^:?-{1,}:?$")
    private val IMAGE = Regex("!\\[[^\\]\\n]{0,200}]\\((?:[^()\\s]|\\([^()\\s]*\\)){0,2000}\\)")
    private val LINK = Regex("\\[([^\\]\\n]{1,200})]\\(((?:[^()\\s]|\\([^()\\s]*\\)){1,2000})\\)")

    fun parse(text: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()
        fun flush() {
            if (para.isNotEmpty()) blocks.add(MdBlock.Para(inline(para.joinToString("\n")))); para.clear()
        }
        val lines = text.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            val depth = depthOf(line)
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val code = mutableListOf<String>()
                    i++
                    // An unfinished fence (the answer is still arriving) runs to the end.
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) code.add(lines[i++])
                    blocks.add(MdBlock.Code(code.joinToString("\n")))
                }
                line.isBlank() -> flush()
                isTableStart(lines, i) -> {
                    flush()
                    val header = cells(lines[i])
                    i += 2
                    val rows = mutableListOf<List<List<MdSpan>>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows.add(cells(lines[i++]))
                    i--
                    blocks.add(MdBlock.Table(header, rows))
                }
                isBullet(trimmed) -> { flush(); blocks.add(MdBlock.Bullet(inline(trimmed.substring(2).trimStart()), depth)) }
                NUMBERED.matches(trimmed) -> {
                    flush()
                    val m = NUMBERED.find(trimmed)!!
                    val n = m.groupValues[1].toInt() // 1 to 9 digits: always fits
                    blocks.add(MdBlock.Numbered(n, inline(m.groupValues[2].trim()), depth))
                }
                trimmed.startsWith("#") && trimmed.dropWhile { it == '#' }.startsWith(" ") -> {
                    flush()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                    blocks.add(MdBlock.Heading(level, inline(trimmed.dropWhile { it == '#' }.trim())))
                }
                else -> para.add(line)
            }
            i++
        }
        flush()
        return blocks
    }

    /** Two spaces (or a tab) of indent per level, so nested lists look nested and a runaway indent stops at [MAX_DEPTH]. */
    private fun depthOf(line: String): Int {
        var width = 0
        for (c in line) { if (c == ' ') width++ else if (c == '\t') width += 4 else break }
        return (width / 2).coerceAtMost(MAX_DEPTH)
    }

    private fun isBullet(t: String) = t.length > 2 && (t[0] == '-' || t[0] == '*' || t[0] == '•') && t[1] == ' '

    /** A table is a pipe row followed by a separator row of dashes; without the separator it is still a paragraph. */
    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size || !lines[i].contains('|')) return false
        val sep = rawCells(lines[i + 1])
        return sep.isNotEmpty() && lines[i + 1].contains('|') && sep.all { SEPARATOR_CELL.matches(it) } && rawCells(lines[i]).isNotEmpty()
    }

    private fun rawCells(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.dropLast(1)
        return t.split("|").map { it.trim() }
    }

    private fun cells(line: String): List<List<MdSpan>> = rawCells(line).map { inline(it) }

    /** **bold**, `code` and [text](http-address); a marker with no partner is left as the characters it is. */
    fun inline(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val plain = StringBuilder()
        fun emitPlain() { if (plain.isNotEmpty()) out.add(MdSpan(plain.toString())); plain.clear() }
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) { emitPlain(); out.add(MdSpan(text.substring(i + 2, end), bold = true)); i = end + 2; continue }
            }
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) { emitPlain(); out.add(MdSpan(text.substring(i + 1, end), code = true)); i = end + 1; continue }
            }
            if (text[i] == '!') {
                // An image is never loaded: it stays as written.
                val img = IMAGE.matchAt(text, i)
                if (img != null) { plain.append(img.value); i += img.value.length; continue }
            }
            if (text[i] == '[') {
                val m = LINK.matchAt(text, i)
                if (m != null && isWebAddress(m.groupValues[2])) {
                    emitPlain(); out.add(MdSpan(m.groupValues[1], link = m.groupValues[2])); i += m.value.length; continue
                }
            }
            plain.append(text[i]); i++
        }
        emitPlain()
        return out
    }

    /** The address with its scheme in lower case: Android matches schemes case-sensitively, so `HTTP://` would find no app. */
    fun normalizedAddress(target: String): String {
        val i = target.indexOf("://")
        return if (i > 0) target.substring(0, i).lowercase() + target.substring(i) else target
    }

    /** The only addresses a tap may open. */
    fun isWebAddress(target: String): Boolean {
        val t = target.lowercase()
        return (t.startsWith("http://") && t.length > 7) || (t.startsWith("https://") && t.length > 8)
    }
}

/** The codes the computer (or this app) gives for an answer that did not complete. */
object ChatReasons {
    val KNOWN = listOf(
        "off", "not_allowed", "busy", "rate", "too_long", "empty", "no_key", "unreachable", "auth", "provider",
        "canceled", "internal", "connection", "interrupted",
    )

    /** A code this version does not know is shown as a general problem. */
    fun key(code: String?): String = if (code != null && code in KNOWN) code else "internal"
}
