package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S5 (T8): Koda, not Abito's green clay character, everywhere the app draws its character: the live views (sheets, pairing,
 * chat, session, island, gallery), the widgets' still pictures, the picker previews and the launcher and status-bar icons.
 */
class KodaEverywhereTest {
    private val main = "src/main/kotlin/com/kenai/android/"
    private val res = "src/main/res/"
    private fun read(path: String) = File(path).readText()

    /** The clay body's gradient stops (light, middle, dark) and its eye ink, as the Abito desktop drew them. */
    private val clayColours = listOf("ACDD96", "8FCB78", "78B562", "0F1D0B")

    /** The dead clay model may still sit in the tree until it is deleted; nothing may use it. */
    private val clayFile = "ClayModel.kt"

    private val sources get() = File(main).walkTopDown().filter { it.extension == "kt" && it.name != clayFile }.toList()

    @Test fun noSourceDrawsTheClayBody() {
        for (f in sources) {
            val src = f.readText()
            for (hex in clayColours) assertFalse("${f.name} has the clay colour #$hex", src.contains(hex, ignoreCase = true))
            assertFalse("${f.name} uses the clay model", Regex("""\bClay\.""").containsMatchIn(src))
            assertFalse("${f.name} builds a clay outline", src.contains("geometry()"))
        }
    }

    @Test fun noResourceHasTheClayColours() {
        for (f in File(res).walkTopDown().filter { it.extension == "xml" }) {
            for (hex in clayColours) assertFalse("${f.path} has the clay colour #$hex", f.readText().contains(hex, ignoreCase = true))
        }
    }

    @Test fun theLiveCharacterIsKodaFromTheSpriteSheet() {
        val view = read(main + "character/CharacterView.kt")
        assertTrue("CharacterView draws the sprite sheet", view.contains("R.drawable.koda_chibi"))
        // Reduced motion holds the pose's first frame, as Home's KodaView does.
        assertTrue(view.contains("ANIMATOR_DURATION_SCALE"))
        val painter = read(main + "character/CharacterPainter.kt")
        assertTrue("whole pixels, no blur", painter.contains("FilterQuality.None"))
        assertTrue(painter.contains("pose.row * CELL"))
        assertTrue("the pose comes from the engine", painter.contains("engine.pose()"))
    }

    @Test fun theWidgetPictureIsKodaFromTheSpriteSheet() {
        val bitmap = read(main + "app/CharacterBitmap.kt")
        assertTrue(bitmap.contains("R.drawable.koda_chibi"))
        assertTrue(bitmap.contains("KodaPose.of(state)"))
        assertTrue("drawn by the shared Koda painter (nearest neighbour)", bitmap.contains("drawKoda("))
        assertFalse("no clay engine settles a widget picture", bitmap.contains("CharacterEngine"))
        assertFalse(bitmap.contains("drawCharacter("))
    }

    // ── Vector pictures generated from the sprite sheet ─────────────────────────────────────────────

    /** The sheet's ARGB at (x, y), read with the desktop JVM's ImageIO (the unit-test classpath only shows Android's API). */
    private val argbAt: (Int, Int) -> Int by lazy {
        val image = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, File(res + "drawable-nodpi/koda_chibi.png"))
        val get = image.javaClass.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val fn: (Int, Int) -> Int = { x, y -> get.invoke(image, x, y) as Int }
        fn
    }

    /** Every opaque pixel a vector names, as a map from (x, y) to its colour (RRGGBB), read from `M x,y h w v1 h-w z` runs. */
    private fun pixels(xml: String): Map<Pair<Int, Int>, String> {
        val out = HashMap<Pair<Int, Int>, String>()
        val path = Regex("""<path android:fillColor="#(?:FF)?([0-9A-F]{6})" android:pathData="([^"]*)"""")
        for (p in path.findAll(xml)) {
            for (run in Regex("""M(\d+),(\d+)h(\d+)v1h-\d+z""").findAll(p.groupValues[2])) {
                val (x, y, w) = run.destructured
                for (i in 0 until w.toInt()) out[(x.toInt() + i) to y.toInt()] = p.groupValues[1]
            }
        }
        return out
    }

    /** The opaque pixels of sheet cell ([row], [col]) in the same shape. */
    private fun cell(row: Int, col: Int): Map<Pair<Int, Int>, String> {
        val out = HashMap<Pair<Int, Int>, String>()
        for (y in 0 until 32) for (x in 0 until 32) {
            val argb = argbAt(col * 32 + x, row * 32 + y)
            if (argb ushr 24 != 0) out[x to y] = "%06X".format(argb and 0xFFFFFF)
        }
        return out
    }

    /** The picker's sample states and the Koda pose row each one must show (KodaPose.of of the matching BotState). */
    private val previewRows = mapOf("working" to 3, "waiting" to 4, "done" to 5, "idle" to 0)

    @Test fun thePickerPreviewsShowKodaInTheSampleState() {
        for ((state, row) in previewRows) {
            val face = read("${res}drawable/preview_face_$state.xml")
            assertEquals("$state is Koda's pose row $row, first frame", cell(row, 0), pixels(face))
        }
    }

    @Test fun theLauncherIconIsKodasFace() {
        val fg = read(res + "drawable/ic_launcher_fg.xml")
        val px = pixels(fg)
        assertTrue("a pixel face, not a disc", px.size > 200)
        val palette = cell(1, 0).values.toSet()
        assertTrue("only Koda's colours", px.values.all { it in palette })
        assertTrue("with Koda's blue eyes", px.values.contains("5F86AD"))
        assertTrue(read(res + "mipmap-anydpi-v26/ic_launcher.xml").contains("@drawable/ic_launcher_fg"))
    }

    @Test fun theStatusBarIconIsAOneColourCatHead() {
        val icon = read(res + "drawable/ic_stat_kenai.xml")
        val fills = Regex("""android:fillColor="([^"]+)"""").findAll(icon).map { it.groupValues[1] }.toSet()
        assertEquals("one colour, as Android requires", setOf("#FFFFFFFF"), fills)
        val px = pixels(icon).keys
        assertTrue(px.size > 150)
        // Two ears: the top row of the silhouette is two separate pieces.
        val top = px.minOf { it.second }
        val xs = px.filter { it.second == top }.map { it.first }.sorted()
        assertEquals("two ear tips", 2, 1 + xs.zipWithNext().count { (a, b) -> b - a > 1 })
    }
}
