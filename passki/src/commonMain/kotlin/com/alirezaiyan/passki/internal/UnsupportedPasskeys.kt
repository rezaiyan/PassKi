package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.SignalOutcome

internal class UnsupportedPasskeys(platform: String) : Passkeys {
    private val failure = PasskeyResult.Failure(
        PasskeyError.NotSupported("PassKi does not support $platform yet. It is on the roadmap."),
    )

    override suspend fun capabilities(): PasskeyCapabilities =
        PasskeyCapabilities(
            isSupported = false,
            immediateSignIn = false,
            unifiedPasswordSignIn = false,
            conditionalCreate = false,
            signals = false,
            autofill = false,
        )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> = failure

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> = failure

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> = failure

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> = failure

    override val signals: PasskeySignals = object : PasskeySignals {
        private val skipped = PasskeyResult.Success(SignalOutcome.Skipped(failure.error.message))

        override suspend fun unknownCredential(rpId: String, credentialId: String) = skipped

        override suspend fun acceptedCredentials(rpId: String, userHandle: String, credentialIds: List<String>) = skipped

        override suspend fun currentUserDetails(rpId: String, userHandle: String, name: String, displayName: String) =
            skipped
    }
}
