package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import kotlin.random.Random

/** A signal call recorded by [FakePasskeys.signals], in [FakePasskeys.receivedSignals]. */
public sealed interface FakeSignal {
    /** Recorded [PasskeySignals.unknownCredential] call. */
    public data class UnknownCredential(public val rpId: String, public val credentialId: String) : FakeSignal

    /** Recorded [PasskeySignals.acceptedCredentials] call. */
    public data class AcceptedCredentials(
        public val rpId: String,
        public val userId: String,
        public val credentialIds: List<String>,
    ) : FakeSignal

    /** Recorded [PasskeySignals.currentUserDetails] call. */
    public data class CurrentUserDetails(
        public val rpId: String,
        public val userId: String,
        public val name: String,
        public val displayName: String,
    ) : FakeSignal
}

/**
 * Drop-in [Passkeys] for unit tests: real WebAuthn responses, no device.
 *
 * @param origin the web origin written into clientDataJSON, e.g. `https://example.com`.
 * @param random source for credential ids; pass a seeded [Random] for deterministic ids.
 */
public class FakePasskeys(origin: String, random: Random = Random.Default) : Passkeys {
    /** The backing authenticator; inspect [FakeAuthenticator.storedCredentials] in assertions. */
    public val authenticator: FakeAuthenticator = FakeAuthenticator(origin, random)

    /** Value returned by [capabilities]; everything supported by default. */
    public var capabilitiesResult: PasskeyCapabilities = PasskeyCapabilities(
        isSupported = true,
        immediateSignIn = true,
        unifiedPasswordSignIn = true,
        conditionalCreate = true,
        signals = true,
    )

    /** Username → password offered by [signInOrPassword] when no passkey matches. */
    public val savedPasswords: MutableMap<String, String> = linkedMapOf()

    /** Every signal received through [signals], in call order. */
    public val receivedSignals: MutableList<FakeSignal> = mutableListOf()

    private val queuedErrors = ArrayDeque<PasskeyError>()

    /** The next operation fails with [error]; later ones behave normally. Calls queue up in order. */
    public fun failNextWith(error: PasskeyError) {
        queuedErrors.addLast(error)
    }

    override suspend fun capabilities(): PasskeyCapabilities = capabilitiesResult

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        result { parseRegistration(authenticator.register(optionsJson)) }

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> = register(optionsJson)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        result { parseAssertion(authenticator.signIn(optionsJson)) }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> = result {
        try {
            SignInCredential.Passkey(parseAssertion(authenticator.signIn(optionsJson)))
        } catch (e: PasskeyException) {
            val password = savedPasswords.entries.firstOrNull()
            if (e.error != PasskeyError.NoCredentials || password == null) throw e
            SignInCredential.Password(password.key, password.value)
        }
    }

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> = result {
            receivedSignals += FakeSignal.UnknownCredential(rpId, credentialId)
            authenticator.remove(credentialId)
        }

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = result {
            receivedSignals += FakeSignal.AcceptedCredentials(rpId, userId, credentialIds)
            authenticator.retainOnly(rpId, userId, credentialIds)
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = result {
            receivedSignals += FakeSignal.CurrentUserDetails(rpId, userId, name, displayName)
        }
    }

    private inline fun <T> result(block: () -> T): PasskeyResult<T> {
        queuedErrors.removeFirstOrNull()?.let { return PasskeyResult.Failure(it) }
        return try {
            PasskeyResult.Success(block())
        } catch (e: PasskeyException) {
            PasskeyResult.Failure(e.error)
        }
    }
}
