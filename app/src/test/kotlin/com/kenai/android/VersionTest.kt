package com.kenai.android

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** This branch is Kenai 0.2 (KN-01..KN-03): the version the phone shows. 0.1.0 shipped from release/0.1. */
class VersionTest {
    @Test fun kenaiIsVersion0_2() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue("versionName is 0.2.0", Regex("""versionName\s*=\s*"0\.2\.0"""").containsMatchIn(gradle))
    }
}
