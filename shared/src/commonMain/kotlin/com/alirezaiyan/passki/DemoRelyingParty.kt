package com.alirezaiyan.passki

import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * Client-side stand-in for a server, for the demo only. A real app gets these
 * options from its backend and verifies the responses there.
 */
object DemoRelyingParty {
    const val RP_ID = "alirezaiyan.com"
    const val USER_ID = "cGFzc2tpLWRlbW8" // base64url("passki-demo")

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val credentialIds = mutableListOf<String>()

    fun creationOptions(): String {
        val exclude = credentialIds.joinToString(",") { """{"type":"public-key","id":"$it"}""" }
        return """
            {"rp":{"id":"$RP_ID","name":"PassKi Demo"},
             "user":{"id":"$USER_ID","name":"demo@passki.dev","displayName":"PassKi Demo"},
             "challenge":"${challenge()}",
             "pubKeyCredParams":[{"type":"public-key","alg":-7},{"type":"public-key","alg":-257}],
             "excludeCredentials":[$exclude],
             "authenticatorSelection":{"residentKey":"required","userVerification":"preferred"},
             "attestation":"none","timeout":60000}
        """.trimIndent()
    }

    fun requestOptions(): String =
        """{"challenge":"${challenge()}","rpId":"$RP_ID","userVerification":"preferred","timeout":60000}"""

    private fun challenge(): String = base64Url.encode(Random.nextBytes(32))
}
