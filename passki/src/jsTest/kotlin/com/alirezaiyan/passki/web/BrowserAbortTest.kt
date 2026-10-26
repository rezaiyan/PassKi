package com.alirezaiyan.passki.web

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** In a real (headless) browser: cancelling the calling coroutine aborts the pending `navigator.credentials` call. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserAbortTest {
    @Test
    fun cancellingAPendingGetAbortsTheBrowserRequest() = runTest {
        js("globalThis.__passkiAborts = {}")
        // Headless Chrome shows no sheet, so a modal get stays pending until it is aborted.
        val call = launch(start = CoroutineStart.UNDISPATCHED) {
            BrowserWebAuthn.call("get", """{"challenge":"AAECAw","rpId":"localhost"}""", null)
        }
        val controllers = js("Object.values(globalThis.__passkiAborts)")
        assertEquals(1, controllers.length as Int, "the glue registered an AbortController for the request")
        val controller = controllers[0]
        assertTrue(controller.signal.aborted == false, "still pending before cancellation")

        call.cancel()
        runCurrent()

        assertTrue(controller.signal.aborted as Boolean, "cancelling the coroutine aborted the browser request")
    }
}
