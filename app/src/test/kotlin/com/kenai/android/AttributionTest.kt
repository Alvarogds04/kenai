package com.kenai.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AttributionTest {
    @Test fun nameAndRepoMatchPermission() {
        assertEquals("Kenai for Android", Attribution.APP_NAME)
        assertEquals("https://github.com/Alvarogds04/abito", Attribution.REPO_URL)
    }
}
