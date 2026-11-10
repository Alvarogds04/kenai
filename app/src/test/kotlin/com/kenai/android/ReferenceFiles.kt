package com.kenai.android

import java.io.File
import org.junit.Assume.assumeTrue

/**
 * Locates the Koda desktop sources so tests can compare the app against them. The desktop lives in its own
 * repository: set ABITO_DESKTOP_ROOT to its checkout, or keep it next to this one as ../../abito-public.
 * Without it the comparing tests are skipped, never passed.
 */
object ReferenceFiles {
    /** Where the desktop keeps the UI core shared with the app (pills, plan levels). */
    const val CORE = "apps/desktop-tauri/frontend/vendor/coucou/core"

    private fun desktopRoot(): File {
        val candidates = listOfNotNull(
            System.getenv("ABITO_DESKTOP_ROOT")?.let(::File),
            File(System.getProperty("user.dir")).absoluteFile.parentFile?.parentFile?.parentFile?.let { File(it, "abito-public") },
        )
        val root = candidates.firstOrNull { File(it, "$CORE/pills.ts").exists() }
        assumeTrue("Koda desktop sources not found (set ABITO_DESKTOP_ROOT)", root != null)
        return root!!
    }

    fun read(path: String): String = File(desktopRoot(), path).readText()
    fun file(path: String): File = File(desktopRoot(), path)
}
