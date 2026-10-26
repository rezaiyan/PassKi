package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.SignalOutcome

/** Logs every call of the platform implementation [delegate] through [PasskiLog]. `Passkeys()` returns one. */
internal class LoggingPasskeys(val delegate: Passkeys) : Passkeys {
    override suspend fun capabilities(): PasskeyCapabilities = PasskiLog.capabilities { delegate.capabilities() }

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        PasskiLog.register(optionsJson) { delegate.register(optionsJson) }

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        PasskiLog.signIn(optionsJson, mode) { delegate.signIn(optionsJson, mode) }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        PasskiLog.signInOrPassword(optionsJson) { delegate.signInOrPassword(optionsJson) }

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        PasskiLog.upgrade(optionsJson) { delegate.upgrade(optionsJson) }

    override val signals: PasskeySignals = LoggingSignals(delegate.signals)

    override fun toString(): String = delegate.toString()
}

private class LoggingSignals(private val delegate: PasskeySignals) : PasskeySignals {
    override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<SignalOutcome> =
        PasskiLog.unknownCredential(rpId, credentialId) { delegate.unknownCredential(rpId, credentialId) }

    override suspend fun acceptedCredentials(
        rpId: String,
        userHandle: String,
        credentialIds: List<String>,
    ): PasskeyResult<SignalOutcome> = PasskiLog.acceptedCredentials(rpId, credentialIds) {
        delegate.acceptedCredentials(rpId, userHandle, credentialIds)
    }

    override suspend fun currentUserDetails(
        rpId: String,
        userHandle: String,
        name: String,
        displayName: String,
    ): PasskeyResult<SignalOutcome> = PasskiLog.currentUserDetails(rpId) {
        delegate.currentUserDetails(rpId, userHandle, name, displayName)
    }

    // One line for the whole sync: the delegate's own syncAccount calls its unlogged signals.
    override suspend fun syncAccount(
        rpId: String,
        userHandle: String,
        credentialIds: List<String>,
        name: String,
        displayName: String,
    ): PasskeyResult<SignalOutcome> = PasskiLog.syncAccount(rpId, credentialIds) {
        delegate.syncAccount(rpId, userHandle, credentialIds, name, displayName)
    }
}
