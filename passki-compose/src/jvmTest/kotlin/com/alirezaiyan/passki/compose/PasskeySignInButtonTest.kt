package com.alirezaiyan.passki.compose

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.getOrThrow
import com.alirezaiyan.passki.test.FakePasskeys
import com.alirezaiyan.passki.test.TestOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PasskeySignInButtonTest {
    private val rpId = "passki.example"
    private val fake = FakePasskeys(origin = "https://$rpId")

    private inner class GatedPasskeys : Passkeys by fake {
        val gate = CompletableDeferred<Unit>()
        var signInCalls = 0

        override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
            signInCalls++
            gate.await()
            return fake.signIn(optionsJson, mode)
        }
    }

    @Test
    fun buttonIsDisabledWithProgressWhileRunningThenReportsAssertion() = runComposeUiTest {
        val credentialId = runBlocking { fake.register(TestOptions.creation(rpId)).getOrThrow().credentialId }
        val passkeys = GatedPasskeys()
        var signedIn: PasskeyAssertion? = null
        setContent {
            val state = rememberPasskeySignIn(passkeys) { TestOptions.request(rpId) }
            PasskeySignInButton(state, onSignedIn = { signedIn = it })
        }
        val button = onNode(hasClickAction())
        onNodeWithText("Sign in with a passkey").assertExists()
        button.assertIsEnabled()

        button.performClick()
        waitForIdle()
        button.assertIsNotEnabled()
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true).assertExists()
        button.performClick()
        waitForIdle()
        assertEquals(1, passkeys.signInCalls)

        passkeys.gate.complete(Unit)
        waitUntil { signedIn != null }
        button.assertIsEnabled()
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true).assertDoesNotExist()
        assertEquals(credentialId, assertNotNull(signedIn).credentialId)
    }

    @Test
    fun cancelledIsNotReportedToOnErrorButOtherErrorsAre() = runComposeUiTest {
        val errors = mutableListOf<PasskeyError>()
        setContent {
            val state = rememberPasskeySignIn(fake) { TestOptions.request(rpId) }
            PasskeySignInButton(state, onSignedIn = {}, onError = { errors += it })
        }
        val button = onNode(hasClickAction())

        fake.failNextWith(PasskeyError.Cancelled)
        button.performClick()
        waitForIdle()
        assertTrue(errors.isEmpty())

        button.performClick()
        waitUntil { errors.isNotEmpty() }
        assertEquals(listOf<PasskeyError>(PasskeyError.NoCredentials), errors)
    }
}
