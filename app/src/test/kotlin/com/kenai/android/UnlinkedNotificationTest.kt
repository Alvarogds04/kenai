package com.kenai.android

import com.kenai.android.core.NotificationPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KN-04 S5 (kn-04-followup S2): «With no agent connected or the phone unlinked, nothing reaches that phone.»
 * One gate covers every notice (approval, status and the question notice, which is a status notice in the question state).
 */
class UnlinkedNotificationTest {
    @Test fun anUnlinkedPhonePostsNoNotification() {
        assertFalse(NotificationPlan.mayPost(paired = false, demo = false))
    }

    @Test fun aPairedPhoneStillPostsNotifications() {
        assertTrue(NotificationPlan.mayPost(paired = true, demo = false))
    }

    @Test fun theDemoStillPostsNotifications() {
        assertTrue(NotificationPlan.mayPost(paired = false, demo = true))
    }
}
