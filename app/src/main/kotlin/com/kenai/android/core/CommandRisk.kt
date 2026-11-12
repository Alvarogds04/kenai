package com.kenai.android.core

/** What a command would destroy, by kind; the words are in [Words.risk]. */
enum class RiskKind { WIPES_DISK, DELETES_DATA, DELETES_FILES, REWRITES_HISTORY, DISCARDS_CHANGES, RUNS_DOWNLOADED_CODE, OPENS_PERMISSIONS, EMPTIES_FILE }

/**
 * The risk line on Home's request (kenai-0.1 S9, L21): a small local detector of destructive shell and SQL shapes. It names the
 * worst thing it recognises, or returns null. Null never means "safe": it only means none of these shapes is in the text. It sees
 * only the command the phone received (the computer cuts it at 500 characters), so what was cut cannot be named. Pure.
 */
object CommandRisk {
    /** A program at the start of a command: the beginning, after ; && || | ( or a sudo / xargs / exec in front. */
    private const val AT = """(?:^|[;&|(`]\s*|\bsudo\s+|\bxargs\s+|\bexec\s+|\bnohup\s+)"""

    private val RULES: List<Pair<RiskKind, Regex>> = listOf(
        RiskKind.WIPES_DISK to Regex("""${AT}(?:mkfs(?:\.\w+)?|wipefs|shred)\b|${AT}dd\s+[^;&|]*\bif=|>\s*/dev/(?:sd|nvme|hd|disk|mmcblk)"""),
        RiskKind.DELETES_DATA to Regex("""\b(?:drop\s+(?:table|database|schema|collection)|truncate\s+table|flushall|flushdb|dropdatabase\s*\()""", RegexOption.IGNORE_CASE),
        RiskKind.DELETES_FILES to Regex(
            """${AT}(?:rm|rmdir|unlink|srm)\s+\S|${AT}git\s+clean\s+-\w*f|${AT}find\s+[^;&|]*\s-delete\b|""" +
                """\bRemove-Item\b|${AT}(?:del|erase|rd)\s+/[sq]""",
            RegexOption.IGNORE_CASE,
        ),
        RiskKind.REWRITES_HISTORY to Regex("""${AT}git\s+push\b[^;&|]*(?:\s--force(?:-with-lease)?\b|\s-\w*f\b|\s\+\S)|${AT}git\s+filter-(?:branch|repo)\b"""),
        RiskKind.DISCARDS_CHANGES to Regex("""${AT}git\s+(?:reset\s+[^;&|]*--hard|checkout\s+(?:-f\s+|--\s+\.)|restore\s+\.|stash\s+(?:drop|clear))"""),
        RiskKind.RUNS_DOWNLOADED_CODE to Regex(
            """\b(?:curl|wget|iwr|Invoke-WebRequest)\b[^;&]*\|\s*(?:sudo\s+)?(?:ba|z|da|k)?sh\b|\biex\b|Invoke-Expression""",
            RegexOption.IGNORE_CASE,
        ),
        RiskKind.OPENS_PERMISSIONS to Regex("""${AT}chmod\s+(?:-\w+\s+)*(?:0?777|a\+rwx|o\+w)\b"""),
        RiskKind.EMPTIES_FILE to Regex("""(?:^|[;&|]\s*):?>\s*[^\s>&|]"""),
    )

    /** The worst recognised kind in [command], or null when none is recognised (never a claim that it is safe). */
    fun of(command: String): RiskKind? {
        val text = command.trim()
        if (text.isEmpty()) return null
        return RULES.firstOrNull { (_, re) -> re.containsMatchIn(text) }?.first
    }

    /** "Borra archivos" / "Deletes files", or null. */
    fun phrase(command: String, w: Words = Words.current()): String? = of(command)?.let { w.risk(it) }
}
