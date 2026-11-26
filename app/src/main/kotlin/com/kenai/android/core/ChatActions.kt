package com.kenai.android.core

/** What the per-message actions (copy, copy code, share) work on. Pure; the system clipboard and share sheet are the screen's. */
object ChatActions {
    /** The message as received (Markdown source), so pasting elsewhere keeps its structure. */
    fun copyText(m: ChatMessage): String = m.text

    /** Actions need finished text: nothing while the answer is still arriving or when there is nothing to copy. */
    fun available(m: ChatMessage): Boolean = m.status != ChatStatus.STREAMING && m.text.isNotBlank()

    /** The contents of each fenced code block, without fences or language tag; an unfinished fence runs to the end. */
    fun codeBlocks(text: String): List<String> =
        MarkdownLite.parse(text).filterIsInstance<MdBlock.Code>().map { it.text }

    /** Every code block of the message, separated by a blank line; empty when it has none. */
    fun allCode(text: String): String = codeBlocks(text).joinToString("\n\n")
}
