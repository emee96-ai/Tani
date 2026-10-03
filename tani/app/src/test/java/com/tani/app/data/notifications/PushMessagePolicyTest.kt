package com.tani.app.data.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushMessagePolicyTest {
    private val owner = "a1000000-0000-4000-8000-000000000001"
    private val other = "a1000000-0000-4000-8000-000000000002"
    private val notification = "a2000000-0000-4000-8000-000000000001"

    @Test fun signedOutDeviceDropsMessages() {
        assertFalse(PushMessagePolicy.canDisplay(null, owner, notification))
    }
    @Test fun switchedAccountDropsQueuedMessage() {
        assertFalse(PushMessagePolicy.canDisplay(other, owner, notification))
    }
    @Test fun malformedMessageCannotOpenProtectedScreen() {
        assertFalse(PushMessagePolicy.canDisplay(owner, owner, "../../auth/reset"))
        assertFalse(PushMessagePolicy.canDisplay(owner, null, notification))
    }
    @Test fun currentOwnerCanReceiveIdentifierOnlyMessage() {
        assertTrue(PushMessagePolicy.canDisplay(owner, owner, notification))
    }
}
