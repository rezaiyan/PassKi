package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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
