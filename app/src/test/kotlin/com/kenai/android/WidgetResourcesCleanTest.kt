package com.kenai.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Node

/**
 * KN-13 review finding: a stray literal `None` (a generation artefact) sat as a text node inside two preview layouts. A layout and a
 * drawable of the widgets hold only elements and attributes; any non-blank character data is a defect aapt2 or lint may flag.
 */
class WidgetResourcesCleanTest {
    private val res = File("src/main/res")

    private fun strayText(file: File): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val found = mutableListOf<String>()
        fun walk(n: Node) {
            if ((n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) && n.nodeValue.isNotBlank()) found += n.nodeValue.trim()
            var c = n.firstChild
            while (c != null) { walk(c); c = c.nextSibling }
        }
        walk(doc.documentElement)
        return found
    }

    private fun widgetFiles(dir: String, prefix: String) =
        (File(res, dir).listFiles() ?: emptyArray()).filter { it.name.startsWith(prefix) && it.name.endsWith(".xml") }

    @Test fun noWidgetLayoutHasCharacterDataOutsideAttributes() {
        val files = widgetFiles("layout", "widget_")
        assertTrue("the widget layouts were found", files.size >= 10)
        for (f in files) assertEquals("${f.name} has stray text", emptyList<String>(), strayText(f))
    }

    @Test fun noWidgetDrawableHasCharacterDataOutsideAttributes() {
        val files = widgetFiles("drawable", "widget_") + widgetFiles("drawable", "preview_")
        assertTrue("the widget drawables were found", files.size >= 10)
        for (f in files) assertEquals("${f.name} has stray text", emptyList<String>(), strayText(f))
    }

    @Test fun noWidgetProviderHasCharacterDataOutsideAttributes() {
        for (f in (File(res, "xml").listFiles() ?: emptyArray()).filter { it.name.startsWith("kenai_") && it.name.endsWith("_info.xml") }) {
            assertEquals("${f.name} has stray text", emptyList<String>(), strayText(f))
        }
    }
}
