package com.kenai.android.core

/**
 * Which screens hide from screenshots, screen recording and the recent-apps card (FLAG_SECURE): the ones that show the
 * pairing code or link, the chat with the user's AI, a request for permission (its sheet, and Home while it waits, since Home then
 * shows the exact command) or a question from an agent (its own screen, kenai-0.1 S9 T15).
 * Pure, so the list is a unit test; MainActivity sets or clears the flag from it as the screen changes.
 */
object SecureScreens {
    fun needed(
        screen: Screen,
        /** Not paired yet: Home is the pairing screen (link, scan, paste). */
        pairing: Boolean,
        approvalSheetShown: Boolean,
        /** "Pair with this computer?" after a scan or a link. */
        pairConfirmShown: Boolean,
        /** A request waits: Home shows its exact command in the request hero (kenai-0.1 S9, L21 «a»). */
        approvalPending: Boolean,
    ): Boolean = screen == Screen.CHAT || screen == Screen.SCAN || screen == Screen.QUESTION || (screen == Screen.HOME && (pairing || approvalPending)) ||
        approvalSheetShown || pairConfirmShown
}
