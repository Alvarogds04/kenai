package com.kenai.android

import com.kenai.android.link.Protocol
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone link's contract, docs/ANDROID_LINK.md, against the code that speaks it (Protocol.kt).
 *
 * The Koda desktop implements its side of the link from that document alone, so a capability, a message type or
 * the approval TTL that exists in the code but not in the document is a protocol the desktop cannot serve. These
 * checks are the guard: when Protocol.kt gains a capability or a message type, this test fails until the document
 * names it.
 */
class LinkDocTest {
    private val doc: String by lazy { repoFile("docs/ANDROID_LINK.md").readText() }

    /** Walks up from the test's working directory (the app module) to the repository root. */
    private fun repoFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, relative)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        throw AssertionError("$relative not found from ${File("").absolutePath}")
    }

    private fun names(word: String): Boolean =
        Regex("(?<![A-Za-z0-9_])${Regex.escape(word)}(?![A-Za-z0-9_])").containsMatchIn(doc)

    @Test fun theDocumentExistsAndSaysWhatItIs() {
        assertTrue("docs/ANDROID_LINK.md is empty", doc.length > 2_000)
        assertTrue("the document must name the Android app it is written from", doc.contains("Kenai"))
        assertTrue("the document must name the desktop that has to serve it", doc.contains("Koda"))
    }

    @Test fun itNamesEveryCapabilityConstantOfTheProtocol() {
        // Read from the code, not from a list here: a new CAP_* constant is a new capability the desktop must know.
        val caps = Protocol::class.java.declaredFields
            .filter { it.name.startsWith("CAP_") && it.type == String::class.java }
            .associate { it.name to it.get(null) as String }
        assertTrue("no capability constant found in Protocol.kt", caps.isNotEmpty())
        for ((name, value) in caps) {
            assertTrue("docs/ANDROID_LINK.md does not name $name", names(name))
            assertTrue("docs/ANDROID_LINK.md does not name the wire value of $name (\"$value\")", names(value))
        }
        for (cap in Protocol.CAPABILITIES) {
            assertTrue("docs/ANDROID_LINK.md does not list the capability \"$cap\"", names(cap))
        }
    }

    @Test fun itNamesEveryMessageTypeOfTheProtocol() {
        val fromPhone = listOf(
            "hello", "decision", "answer", "getDiff", "ping", "bye",
            "chatModels", "chatSend", "chatCancel", "chatReset",
        )
        val fromDesktop = listOf(
            "welcome", "sessions", "approval", "approvalResolved", "question", "diff", "usage", "services",
            "pong", "chatModels", "chatDelta", "chatDone", "chatError", "error",
        )
        for (t in fromPhone) assertTrue("docs/ANDROID_LINK.md does not document the phone's `$t`", names(t))
        for (t in fromDesktop) assertTrue("docs/ANDROID_LINK.md does not document the desktop's `$t`", names(t))
        // Both directions have a name that only appears once in each list: each must be under its own heading.
        assertTrue("the phone's messages need a heading", doc.contains("Phone → desktop"))
        assertTrue("the desktop's messages need a heading", doc.contains("Desktop → phone"))
    }

    @Test fun itNamesTheApprovalTtlAndItsValue() {
        assertTrue("docs/ANDROID_LINK.md does not name Protocol.APPROVAL_TTL_MS", doc.contains("APPROVAL_TTL_MS"))
        assertTrue(
            "docs/ANDROID_LINK.md does not state the value of APPROVAL_TTL_MS (120 s)",
            listOf("120 000", "120_000", "120000").any { doc.contains(it) },
        )
        assertTrue("the desktop dismisses an approval sooner; the document must say so", doc.contains("115"))
    }

    @Test fun itNamesTheLineLimitAndTheWireIdentifiers() {
        assertTrue("docs/ANDROID_LINK.md does not name Protocol.MAX_LINE_BYTES", doc.contains("MAX_LINE_BYTES"))
        assertTrue("docs/ANDROID_LINK.md does not state the 64 KiB line limit", doc.contains("64 KiB"))
        assertTrue("the pairing scheme must be the one in the code", doc.contains("abito://pair"))
        assertTrue("the announced mDNS service type must be the one in the code", doc.contains("_abito._tcp"))
    }
}
