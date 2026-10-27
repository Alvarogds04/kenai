package com.abito.android

import java.io.File
import org.junit.Assume.assumeTrue

/** Locates the desktop reference sources (windows/src/...) so tests can compare against them. */
object ReferenceFiles {
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "windows/src/core/pills.ts").exists()) return dir
            dir = dir.parentFile
        }
        // Abito's repository has no Abito desktop sources: tests that compare against them are skipped (not passed)
        // until they point at Abito's desktop (HU-049 T5).
        assumeTrue("Abito desktop sources (windows/src) are not in this repository", false)
        error("unreachable")
    }

    fun read(path: String): String = File(repoRoot(), path).readText()
    fun file(path: String): File = File(repoRoot(), path)
}
