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
import com.alirezaiyan.passki.internal.SignalRequests
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Behaves like a platform implementation: operations fail with [PasskeyError.NotSupported] when the matching
 * capability flag is off, signals validate and compare ids by their bytes, and the RP ID is checked against
 * [origin] (see [FakeAuthenticator]).
 *
 * Operations are safe to call from concurrent coroutines. The public mutable collections [savedPasswords] and
 * [receivedSignals] are not synchronised: mutate or inspect them from test code before or after calls,
 * not concurrently with them.
 *
 * @param origin the web origin written into clientDataJSON, e.g. `https://example.com`.
 * @param random source for credential ids; pass a seeded [Random] for deterministic ids.
 */
public class FakePasskeys(origin: String, random: Random = Random.Default) : Passkeys {
    /** The backing authenticator; inspect [FakeAuthenticator.storedCredentials] in assertions. */
    public val authenticator: FakeAuthenticator = FakeAuthenticator(origin, random)

    /**
     * Passkeys can be used at all ([PasskeyCapabilities.isSupported]). When false, every operation fails with
     * [PasskeyError.NotSupported] and [capabilities] reports every capability as false. Default true.
     */
    public var isSupported: Boolean = true

    /**
     * [SignInMode.ImmediateOnly] is available ([PasskeyCapabilities.immediateSignIn]). When false,
     * `signIn(mode = ImmediateOnly)` fails with [PasskeyError.NotSupported]. Default true.
     */
    public var immediateSignIn: Boolean = true

    /**
     * [signInOrPassword] is available ([PasskeyCapabilities.unifiedPasswordSignIn]). When false, it fails with
     * [PasskeyError.NotSupported]. Default true.
     */
    public var unifiedPasswordSignIn: Boolean = true

    /**
     * [upgrade] (conditional create) is available ([PasskeyCapabilities.conditionalCreate]). When false, it fails
     * with [PasskeyError.NotSupported]. Default true.
     */
    public var conditionalCreate: Boolean = true

    /**
     * [signals] are available ([PasskeyCapabilities.signals]). When false, every signal fails with
     * [PasskeyError.NotSupported] and is not recorded. Default true.
     */
    public var signalsSupported: Boolean = true

    /**
     * Username → password offered by [signInOrPassword] when no passkey matches.
     * Mutate from test code before/after calls, not concurrently with them.
     */
    public val savedPasswords: MutableMap<String, String> = linkedMapOf()

    /**
     * Every signal received through [signals], in call order (only signals that passed validation).
     * Inspect from test code before/after calls, not concurrently with them.
     */
    public val receivedSignals: MutableList<FakeSignal> = mutableListOf()

    private val lock = Mutex()
    private val queuedErrors = ArrayDeque<PasskeyError>()

    /**
     * The next operation fails with [error]; later ones behave normally. Calls queue up in order.
     * A queued error takes precedence over the capability flags.
     * Call from test code before/after operations, not concurrently with them.
     */
    public fun failNextWith(error: PasskeyError) {
        queuedErrors.addLast(error)
    }

    override suspend fun capabilities(): PasskeyCapabilities = PasskeyCapabilities(
        isSupported = isSupported,
        immediateSignIn = isSupported && immediateSignIn,
        unifiedPasswordSignIn = isSupported && unifiedPasswordSignIn,
        conditionalCreate = isSupported && conditionalCreate,
        signals = isSupported && signalsSupported,
    )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        result { parseRegistration(authenticator.register(optionsJson)) }

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        result(conditionalCreate, "conditionalCreate") {
            parseRegistration(authenticator.register(optionsJson, conditional = true))
        }

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        result(mode != SignInMode.ImmediateOnly || immediateSignIn, "immediateSignIn") {
            parseAssertion(authenticator.signIn(optionsJson))
        }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        result(unifiedPasswordSignIn, "unifiedPasswordSignIn") {
            try {
                SignInCredential.Passkey(parseAssertion(authenticator.signIn(optionsJson)))
            } catch (e: PasskeyException) {
                val password = savedPasswords.entries.firstOrNull()
                if (e.error != PasskeyError.NoCredentials || password == null) throw e
                SignInCredential.Password(password.key, password.value)
            }
        }

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> =
            result(signalsSupported, "signalsSupported") {
                SignalRequests.unknownCredential(rpId, credentialId)
                receivedSignals += FakeSignal.UnknownCredential(rpId, credentialId)
                authenticator.remove(rpId, credentialId)
            }

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = result(signalsSupported, "signalsSupported") {
            SignalRequests.acceptedCredentials(rpId, userId, credentialIds)
            receivedSignals += FakeSignal.AcceptedCredentials(rpId, userId, credentialIds)
            authenticator.retainOnly(rpId, userId, credentialIds)
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = result(signalsSupported, "signalsSupported") {
            SignalRequests.currentUserDetails(rpId, userId, name, displayName)
            receivedSignals += FakeSignal.CurrentUserDetails(rpId, userId, name, displayName)
            authenticator.updateUserName(rpId, userId, name)
        }
    }

    /**
     * Runs [block] under [lock]: a queued error wins, then `isSupported`, then the operation's own
     * capability ([supported], named [flag]); [PasskeyException]s become failures.
     */
    private suspend inline fun <T> result(
        supported: Boolean = true,
        flag: String = "isSupported",
        block: () -> T,
    ): PasskeyResult<T> = lock.withLock {
        queuedErrors.removeFirstOrNull()?.let { return PasskeyResult.Failure(it) }
        if (!isSupported) return PasskeyResult.Failure(notSupported("isSupported"))
        if (!supported) return PasskeyResult.Failure(notSupported(flag))
        try {
            PasskeyResult.Success(block())
        } catch (e: PasskeyException) {
            PasskeyResult.Failure(e.error)
        }
    }

    private fun notSupported(flag: String): PasskeyError.NotSupported =
        PasskeyError.NotSupported("Not supported on this device (FakePasskeys: $flag = false)")
}
