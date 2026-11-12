package com.kenai.android

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kenai's first release is 0.1 (the Koda desktop is 0.2): the version the phone shows. */
class VersionTest {
    @Test fun kenaiIsVersion0_1() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue("versionName is 0.1.0", Regex("""versionName\s*=\s*"0\.1\.0"""").containsMatchIn(gradle))
    }
}
