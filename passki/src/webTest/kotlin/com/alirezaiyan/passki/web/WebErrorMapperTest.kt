package com.alirezaiyan.passki.web

import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebErrorMapperTest {
    @Test
    fun notAllowedIsCancelledForModalRequests() {
        assertEquals(PasskeyError.Cancelled, webPasskeyError("NotAllowedError", "dismissed", WebOperation.Register))
        assertEquals(PasskeyError.Cancelled, webPasskeyError("NotAllowedError", "dismissed", WebOperation.SignIn))
    }

    @Test
    fun notAllowedIsNoCredentialsForImmediateSignIn() {
        assertEquals(PasskeyError.NoCredentials, webPasskeyError("NotAllowedError", "", WebOperation.ImmediateSignIn))
    }

    @Test
    fun invalidStateIsAlreadyRegisteredOnlyWhenCreating() {
        assertEquals(PasskeyError.AlreadyRegistered, webPasskeyError("InvalidStateError", "", WebOperation.Register))
        val signIn = assertIs<PasskeyError.Unknown>(webPasskeyError("InvalidStateError", "x", WebOperation.SignIn))
        assertEquals("InvalidStateError", signIn.platformCode)
    }

    @Test
    fun securityErrorIsMisconfiguredWithRpIdHint() {
        val error = assertIs<PasskeyError.Misconfigured>(
            webPasskeyError("SecurityError", "The RP ID is invalid for this domain.", WebOperation.Register),
        )
        assertEquals("The RP ID is invalid for this domain.", error.message)
        assertEquals(RP_ID_HINT, error.fixHint)
    }

    @Test
    fun otherDomExceptions() {
        assertEquals(PasskeyError.Interrupted, webPasskeyError("AbortError", "", WebOperation.SignIn))
        assertEquals(PasskeyError.NotSupported("no"), webPasskeyError("NotSupportedError", "no", WebOperation.SignIn))
        assertEquals(PasskeyError.NotSupported("uv"), webPasskeyError("ConstraintError", "uv", WebOperation.Register))
        assertEquals(PasskeyError.InvalidOptions("bad"), webPasskeyError("TypeError", "bad", WebOperation.SignIn))
        assertEquals(PasskeyError.Unknown("odd", "WeirdError"), webPasskeyError("WeirdError", "odd", WebOperation.SignIn))
    }

    @Test
    fun typeErrorFromImmediateMediationIsNotSupported() {
        assertIs<PasskeyError.NotSupported>(webPasskeyError("TypeError", "bad enum", WebOperation.ImmediateSignIn))
    }

    @Test
    fun blankMessageFallsBackToName() {
        assertEquals(PasskeyError.NotSupported("NotSupportedError"), webPasskeyError("NotSupportedError", " ", WebOperation.SignIn))
    }

    @Test
    fun upgradeDeclinesAreNotSupported() {
        for (name in listOf("NotAllowedError", "NotSupportedError", "UnknownError")) {
            assertIs<PasskeyError.NotSupported>(webPasskeyError(name, "", WebOperation.Upgrade), name)
        }
        assertEquals(PasskeyError.AlreadyRegistered, webPasskeyError("InvalidStateError", "", WebOperation.Upgrade))
        assertIs<PasskeyError.Misconfigured>(webPasskeyError("SecurityError", "rp", WebOperation.Upgrade))
    }

    @Test
    fun signalNotAllowedIsNotSupported() {
        assertIs<PasskeyError.NotSupported>(webPasskeyError("NotAllowedError", "no", WebOperation.Signal))
    }
}
