package com.kenai.android.core

/**
 * What the agent is doing, in words a person reads. The desktop sends the last step as the agent
 * reported it, often a raw tool name ("ask_question", "Bash", "Read"). Known tools get a plain
 * sentence; an unknown one is only cleaned (no underscores, first letter capital); free text such
 * as "Running npm test" is left alone. Used by the Home card and the island, so both say the same.
 */
object ToolLabels {
    /** Tool names (reduced by [key]) by what they do; the words for each kind are in [Words]. */
    private val KINDS = mapOf(
        "askuserquestion" to ToolKind.ASK, "askquestion" to ToolKind.ASK,
        "bash" to ToolKind.RUN, "shell" to ToolKind.RUN, "runshellcommand" to ToolKind.RUN, "execcommand" to ToolKind.RUN,
        "read" to ToolKind.READ, "readfile" to ToolKind.READ, "readmanyfiles" to ToolKind.READ,
        "edit" to ToolKind.EDIT, "multiedit" to ToolKind.EDIT, "write" to ToolKind.EDIT, "writefile" to ToolKind.EDIT,
        "notebookedit" to ToolKind.EDIT, "applypatch" to ToolKind.EDIT,
        "grep" to ToolKind.SEARCH_FILES, "glob" to ToolKind.SEARCH_FILES, "ls" to ToolKind.LOOK,
        "websearch" to ToolKind.SEARCH_WEB, "googlewebsearch" to ToolKind.SEARCH_WEB, "webfetch" to ToolKind.READ_WEB,
        "task" to ToolKind.SUB_AGENT,
        "todowrite" to ToolKind.PLAN, "exitplanmode" to ToolKind.PLAN,
    )

    /**
     * What a request is for, as an action after "wants to" or "Allowed:" ("run a command", "edit files"); an unknown tool, or one
     * with no action of its own (planning), is cleaned and lower-cased.
     */
    fun action(tool: String, w: Words = Words.current()): String {
        val name = tool.trim()
        if (name.isEmpty()) return w.doSomething
        return KINDS[key(name)]?.let { w.toolAction(it) } ?: clean(name).lowercase()
    }

    private const val SEP = " · "

    /** "ask_question" and "AskQuestion" both reduce to "askquestion". */
    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun looksLikeAName(s: String) = s.isNotEmpty() && s.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == ':' }

    /**
     * The words for a step on a surface that is seen without a tap. The tool is named in plain words; of a shell
     * command only the program's name follows ([SafeText.program]); the file, the arguments and any free text the
     * agent wrote are never shown here. Free text that is not a tool name gives "" so the screen falls back to the
     * state's own word.
     */
    fun label(raw: String, w: Words = Words.current()): String {
        val text = raw.trim().removeSuffix("·").trim()
        if (text.isEmpty()) return ""
        // "Bash · npm test": the tool is named, the rest is what it does (and stays out of sight).
        val at = text.indexOf(SEP)
        val head = if (at > 0) text.substring(0, at).trim() else text
        val rest = if (at > 0) text.substring(at + SEP.length).trim() else ""
        if (!looksLikeAName(head)) return ""
        // An unknown word shows only if it can be a tool's name: a key or a token pasted in its place never does.
        val kind = KINDS[key(head)]
        if (kind == null && !plausibleToolName(head)) return ""
        val tool = kind?.let { w.toolLabel(it) } ?: clean(head)
        val program = if (rest.isNotEmpty()) SafeText.requestLabel(head, rest).substringAfter(SEP, "") else ""
        return if (program.isNotEmpty()) "$tool$SEP$program" else tool
    }

    private val DIGIT_RUN = Regex("[0-9]{3,}")

    private fun plausibleToolName(s: String): Boolean =
        s.length <= 40 && !DIGIT_RUN.containsMatchIn(s) && (s.length <= 24 || s.any { it == '_' || it == '-' || it == '.' || it == ':' })

    private val SEPARATORS = Regex("[_\\-.:]+")
    private val CAMEL = Regex("(?<=[a-z0-9])(?=[A-Z])")

    /** "mcp__github__list_issues" to "Mcp github list issues"; "FooBar" to "Foo bar". */
    fun clean(name: String): String =
        name.replace(SEPARATORS, " ").replace(CAMEL, " ").trim().lowercase().replaceFirstChar { it.uppercaseChar() }
}
