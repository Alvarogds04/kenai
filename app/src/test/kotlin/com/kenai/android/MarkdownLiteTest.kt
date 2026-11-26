package com.kenai.android

import com.kenai.android.core.MarkdownLite
import com.kenai.android.core.MdBlock
import com.kenai.android.core.MdSpan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {
    private fun plain(vararg t: String) = t.map { MdSpan(it) }

    @Test fun paragraphsAreSeparatedByBlankLines() {
        val b = MarkdownLite.parse("First line\nstill first\n\nSecond")
        assertEquals(listOf(MdBlock.Para(plain("First line\nstill first")), MdBlock.Para(plain("Second"))), b)
    }

    @Test fun boldAndInlineCode() {
        assertEquals(
            listOf(MdSpan("Use "), MdSpan("soft reset", bold = true), MdSpan(" with "), MdSpan("git reset", code = true), MdSpan(".")),
            MarkdownLite.inline("Use **soft reset** with `git reset`."),
        )
    }

    @Test fun anUnfinishedMarkerStaysAsTheCharactersItIs() {
        // Half an answer: "**bol" has no partner yet.
        assertEquals(plain("a **bol"), MarkdownLite.inline("a **bol"))
        assertEquals(plain("a `co"), MarkdownLite.inline("a `co"))
        assertEquals(plain("****"), MarkdownLite.inline("****"))
        assertEquals(plain("``"), MarkdownLite.inline("``"))
    }

    @Test fun bulletsWithDashStarOrDot() {
        val b = MarkdownLite.parse("- one\n* two **b**\n• three\n-not a bullet")
        assertEquals(MdBlock.Bullet(plain("one")), b[0])
        assertEquals(MdBlock.Bullet(listOf(MdSpan("two "), MdSpan("b", bold = true))), b[1])
        assertEquals(MdBlock.Bullet(plain("three")), b[2])
        assertEquals(MdBlock.Para(plain("-not a bullet")), b[3])
    }

    @Test fun fencedCodeIsKeptAsItIsAndNothingInsideIsStyled() {
        val b = MarkdownLite.parse("Before\n```kotlin\nval a = **x**\n  indented\n```\nAfter")
        assertEquals(
            listOf(MdBlock.Para(plain("Before")), MdBlock.Code("val a = **x**\n  indented"), MdBlock.Para(plain("After"))),
            b,
        )
    }

    @Test fun anUnfinishedFenceRunsToTheEndWhileTheAnswerIsStillArriving() {
        assertEquals(listOf(MdBlock.Code("line1\nline2")), MarkdownLite.parse("```\nline1\nline2"))
    }

    @Test fun headingsKeepTheirLevel() {
        assertEquals(listOf(MdBlock.Heading(2, plain("Title"))), MarkdownLite.parse("## Title"))
        assertEquals(listOf(MdBlock.Heading(1, plain("T"))), MarkdownLite.parse("# T"))
        assertEquals(listOf(MdBlock.Para(plain("#hashtag"))), MarkdownLite.parse("#hashtag"))
    }

    @Test fun nestedBulletsAndNumberedListsFollowTheIndent() {
        val b = MarkdownLite.parse("- a\n  - b\n    - c\n1. one\n  2. two\n10) ten")
        assertEquals(MdBlock.Bullet(plain("a"), 0), b[0])
        assertEquals(MdBlock.Bullet(plain("b"), 1), b[1])
        assertEquals(MdBlock.Bullet(plain("c"), 2), b[2])
        assertEquals(MdBlock.Numbered(1, plain("one"), 0), b[3])
        assertEquals(MdBlock.Numbered(2, plain("two"), 1), b[4])
        assertEquals(MdBlock.Numbered(10, plain("ten"), 0), b[5])
        assertEquals(MarkdownLite.MAX_DEPTH, (MarkdownLite.parse(" ".repeat(60) + "- deep")[0] as MdBlock.Bullet).depth)
    }

    @Test fun aTableNeedsItsSeparatorRow() {
        val b = MarkdownLite.parse("| A | B |\n|---|:-:|\n| 1 | **2** |\n| 3 | 4 |\n\nafter")
        assertEquals(
            MdBlock.Table(
                listOf(plain("A"), plain("B")),
                listOf(listOf(plain("1"), listOf(MdSpan("2", bold = true))), listOf(plain("3"), plain("4"))),
            ),
            b[0],
        )
        assertEquals(MdBlock.Para(plain("after")), b[1])
        // Half a table (no separator yet) is still just text.
        assertEquals(listOf(MdBlock.Para(plain("| A | B |"))), MarkdownLite.parse("| A | B |"))
        assertEquals(listOf(MdBlock.Para(plain("a | b\nc"))), MarkdownLite.parse("a | b\nc"))
    }

    @Test fun onlyHttpAndHttpsLinksBecomeLinks() {
        assertEquals(
            listOf(MdSpan("see "), MdSpan("docs", link = "https://a.example/x"), MdSpan(" and "), MdSpan("old", link = "HTTP://b.example")),
            MarkdownLite.inline("see [docs](https://a.example/x) and [old](HTTP://b.example)"),
        )
        for (bad in listOf("[x](javascript:alert(1))", "[x](intent://a#Intent;end)", "[x](file:///etc/passwd)", "[x](https://)", "[x]()", "[x](/rel)")) {
            assertEquals(bad, plain(bad), MarkdownLite.inline(bad))
        }
        assertTrue(MarkdownLite.isWebAddress("https://a.b"))
        assertTrue(!MarkdownLite.isWebAddress("ftp://a.b"))
    }

    @Test fun parenthesisedAddressesKeepTheirParenthesesAndOpenWithALowerCaseScheme() {
        assertEquals(
            listOf(MdSpan("w", link = "https://en.wikipedia.org/wiki/Foo_(bar)"), MdSpan(".")),
            MarkdownLite.inline("[w](https://en.wikipedia.org/wiki/Foo_(bar))."),
        )
        assertEquals("http://B.example/X", MarkdownLite.normalizedAddress("HTTP://B.example/X"))
        assertEquals("https://a", MarkdownLite.normalizedAddress("https://a"))
    }

    @Test fun manyUnclosedBracketsStayFast() {
        val t0 = System.nanoTime()
        MarkdownLite.inline("[".repeat(100_000)); MarkdownLite.inline("![".repeat(50_000)); MarkdownLite.inline("[a](".repeat(25_000))
        assertTrue("took too long", (System.nanoTime() - t0) / 1_000_000 < 5_000)
    }

    @Test fun imagesAndHtmlStayPlainText() {
        val text = "![i](http://a/b.png) <script>alert(1)</script> <a href=\"http://x\">y</a>"
        assertEquals(listOf(MdBlock.Para(plain(text))), MarkdownLite.parse(text))
        // An image is not turned into a link even with the bang right before a valid link shape.
        assertEquals(plain("![i](https://a/b.png)"), MarkdownLite.inline("![i](https://a/b.png)"))
    }

    @Test fun windowsLineEndingsAndEmptyInput() {
        assertEquals(listOf(MdBlock.Para(plain("a\nb"))), MarkdownLite.parse("a\r\nb"))
        assertEquals(emptyList<MdBlock>(), MarkdownLite.parse(""))
        assertEquals(emptyList<MdBlock>(), MarkdownLite.parse("\n\n  \n"))
    }

    @Test fun neverThrowsOnOddInput() {
        for (s in listOf("```", "``` ```", "**", "`", "- ", "-", "#", "# ", "|", "|-", "||\n|-|", "[", "[](", "![", "1. ", "1.", "\u0000", "a".repeat(100_000))) MarkdownLite.parse(s)
        assertTrue(true)
    }
}
