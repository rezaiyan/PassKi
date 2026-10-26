package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SignalRequestsTest {
    @Test
    fun unknownCredential() {
        assertEquals(
            """{"rpId":"example.com","credentialId":"AQID"}""",
            SignalRequests.unknownCredential("example.com", "AQID"),
        )
    }

    @Test
    fun acceptedCredentials() {
        assertEquals(
            """{"rpId":"example.com","userId":"AQID","allAcceptedCredentialIds":["BAUG"]}""",
            SignalRequests.acceptedCredentials("example.com", "AQID", listOf("BAUG")),
        )
    }

    @Test
    fun currentUserDetails() {
        assertEquals(
            """{"rpId":"example.com","userId":"AQID","name":"alice","displayName":"Alice"}""",
            SignalRequests.currentUserDetails("example.com", "AQID", "alice", "Alice"),
        )
    }

    @Test
    fun rejectsNonBase64Ids() {
        val e = assertFailsWith<PasskeyException> { SignalRequests.unknownCredential("example.com", "***") }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }

    @Test
    fun anEmailAsUserHandleNamesTheParameterAndSaysWhereTheHandleComesFrom() {
        val e = assertFailsWith<PasskeyException> {
            SignalRequests.acceptedCredentials("example.com", "pat@example.com", listOf("BAUG"))
        }
        val error = assertIs<PasskeyError.InvalidOptions>(e.error)
        assertTrue(error.message.startsWith("userHandle must be base64url"), error.message)
        assertTrue("user.id" in error.fixHint && "PasskeyAssertion.userHandle" in error.fixHint, error.fixHint)
        assertTrue("account id" in error.fixHint, error.fixHint)
    }

    @Test
    fun aBadCredentialIdNamesItsParameter() {
        val e = assertFailsWith<PasskeyException> { SignalRequests.acceptedCredentials("example.com", "AQID", listOf("***")) }
        val error = assertIs<PasskeyError.InvalidOptions>(e.error)
        assertTrue(error.message.startsWith("credentialIds must be base64url"), error.message)
        assertTrue("credentialId" in error.fixHint, error.fixHint)
    }

    @Test
    fun emitsCanonicalIds() {
        assertEquals(
            """{"rpId":"example.com","credentialId":"AQID_w"}""",
            SignalRequests.unknownCredential("example.com", "AQID/w=="),
        )
        assertEquals(
            """{"rpId":"example.com","userId":"AQID_w","allAcceptedCredentialIds":["AQID_w"]}""",
            SignalRequests.acceptedCredentials("example.com", "AQID/w==", listOf("AQID/w==")),
        )
        assertEquals(
            """{"rpId":"example.com","userId":"AQID_w","name":"a","displayName":"A"}""",
            SignalRequests.currentUserDetails("example.com", "AQID/w==", "a", "A"),
        )
    }
}
