package com.alirezaiyan.passki

import com.alirezaiyan.passki.demo.DemoVerdict
import com.alirezaiyan.passki.demo.PasskiDemoServer
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignOutTest {
    @Test
    fun signOutClearsTheSessionAndTheUsername() {
        // A username left in the field makes Android's keyboard show it as a word instead of the passkey suggestions.
        val controller = DemoController(Passkeys(), PasskiDemoServer("http://127.0.0.1:9"), null, "test", TestScope())
        controller.username = "android-e2e"
        controller.session = Session(
            DemoVerdict(true, "C1", 0, true, userHandle = "H1", origin = "https://passki.alirezaiyan.com", rpId = "passki.alirezaiyan.com"),
            via = "Sign in",
        )

        controller.signOut()

        assertNull(controller.session)
        assertEquals("", controller.username)
    }
}
