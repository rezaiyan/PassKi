package com.alirezaiyan.passki.web

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
import com.alirezaiyan.passki.internal.PasskiJson
import com.alirezaiyan.passki.internal.SignalRequests
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.passkeyCatching
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/** What the page and browser offer, as reported by the JS glue's `capabilities` op. */
@Serializable
internal data class ClientCapabilities(
    val webauthn: Boolean = false,
    val secureContext: Boolean = false,
    val conditionalGet: Boolean = false,
    val conditionalCreate: Boolean = false,
    val immediateGet: Boolean = false,
    val signalUnknownCredential: Boolean = false,
    val signalAllAcceptedCredentials: Boolean = false,
    val signalCurrentUserDetails: Boolean = false,
) {
    /** WebAuthn only runs in a secure context: HTTPS, or http://localhost. */
    val supported: Boolean get() = webauthn && secureContext
}

/**
 * Passkeys in the browser (Kotlin/JS and Kotlin/Wasm) through `navigator.credentials`. The browser checks the RP ID
 * against the page's origin itself, so unlike the apps an options JSON without one is fine: it defaults to the page's
 * host.
 */
internal class WebPasskeys(private val browser: WebAuthnBridge = BrowserWebAuthn) : Passkeys {
    /** Set once the browser rejected mediation "immediate" although it reported support. */
    private var immediateRejected = false

    override suspend fun capabilities(): PasskeyCapabilities {
        val client = clientCapabilities()
        val supported = client.supported
        return PasskeyCapabilities(
            isSupported = supported,
            immediateSignIn = supported && client.immediateGet && !immediateRejected,
            unifiedPasswordSignIn = false,
            conditionalCreate = supported && client.conditionalCreate,
            signals = supported && client.signalUnknownCredential && client.signalAllAcceptedCredentials &&
                client.signalCurrentUserDetails,
            autofill = supported && client.conditionalGet,
        )
    }

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        create(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        create(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        passkeyCatching({ it.toPasskeyError(immediate = mode == SignInMode.ImmediateOnly) }) {
            val client = requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            options.model.rpId?.let { WebAuthnOptions.requireRpId(it, "rpId") }
            val mediation = when (mode) {
                SignInMode.Modal -> null
                SignInMode.ImmediateOnly ->
                    if (client.immediateGet && !immediateRejected) "immediate" else throw notSupported(NO_IMMEDIATE)
                SignInMode.Autofill ->
                    if (client.conditionalGet) "conditional" else throw notSupported("This browser has no passkey autofill (conditional mediation).")
            }
            val response = try {
                browser.call("get", options.json, mediation)
            } catch (e: WebAuthnException) {
                if (mediation != "immediate" || !e.isUnknownMediation()) throw e
                immediateRejected = true
                throw notSupported("$NO_IMMEDIATE ${e.message.orEmpty()}")
            }
            parseAssertion(response)
        }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        PasskeyResult.Failure(
            PasskeyError.NotSupported(
                "Browsers have no sheet that offers passkeys and saved passwords together. Use signIn and your password form.",
            ),
        )

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> =
            signal("signalUnknownCredential") { SignalRequests.unknownCredential(rpId, credentialId) }

        override suspend fun acceptedCredentials(rpId: String, userId: String, credentialIds: List<String>): PasskeyResult<Unit> =
            signal("signalAllAcceptedCredentials") { SignalRequests.acceptedCredentials(rpId, userId, credentialIds) }

        override suspend fun currentUserDetails(rpId: String, userId: String, name: String, displayName: String): PasskeyResult<Unit> =
            signal("signalCurrentUserDetails") { SignalRequests.currentUserDetails(rpId, userId, name, displayName) }
    }

    private suspend fun create(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> =
        passkeyCatching({ it.toPasskeyError(conditionalCreate = conditional) }) {
            val client = requireSupported()
            if (conditional && !client.conditionalCreate) {
                throw notSupported("This browser has no conditional create (passkey upgrade after a password sign-in).")
            }
            val options = WebAuthnOptions.parseCreation(optionsJson)
            options.model.rp.id?.let { WebAuthnOptions.requireRpId(it, "rp.id") }
            parseRegistration(browser.call("create", options.json, if (conditional) "conditional" else null))
        }

    private suspend fun signal(op: String, body: () -> String): PasskeyResult<Unit> =
        passkeyCatching({ it.toPasskeyError() }) {
            requireSupported()
            browser.call(op, body(), null)
            Unit
        }

    private suspend fun clientCapabilities(): ClientCapabilities = try {
        PasskiJson.decodeFromString(ClientCapabilities.serializer(), browser.call("capabilities", "{}", null))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ClientCapabilities()
    }

    private suspend fun requireSupported(): ClientCapabilities {
        val client = clientCapabilities()
        if (!client.webauthn) throw notSupported("This browser has no WebAuthn (PublicKeyCredential).")
        if (!client.secureContext) throw notSupported("Passkeys need a secure page: serve it over HTTPS, or from http://localhost.")
        return client
    }

    private fun notSupported(message: String) = PasskeyException(PasskeyError.NotSupported(message))

    private companion object {
        const val NO_IMMEDIATE = "This browser has no immediate sign-in (mediation \"immediate\")."
    }
}

/**
 * The browser doesn't know the `mediation` value: a TypeError while reading `CredentialRequestOptions.mediation`, e.g.
 * Chromium's "The provided value 'immediate' is not a valid enum value of type CredentialMediationRequirement".
 */
internal fun WebAuthnException.isUnknownMediation(): Boolean =
    name == "TypeError" && message.orEmpty().contains("mediation", ignoreCase = true)

/** Maps a rejected WebAuthn call; [conditionalCreate] and [immediate] change what `NotAllowedError` means. */
internal fun Throwable.toPasskeyError(conditionalCreate: Boolean = false, immediate: Boolean = false): PasskeyError {
    if (this !is WebAuthnException) return PasskeyError.Unknown(message ?: this::class.simpleName.orEmpty(), null, this)
    val detail = message.orEmpty()
    return when (name) {
        // The user dismissed the sheet, or it timed out. Browsers don't tell these apart, on purpose.
        "NotAllowedError" -> when {
            conditionalCreate -> PasskeyError.NotSupported("The browser declined to create a passkey silently: $detail")
            immediate -> PasskeyError.NoCredentials
            else -> PasskeyError.Cancelled
        }
        "InvalidStateError" -> PasskeyError.AlreadyRegistered
        "SecurityError" -> PasskeyError.Misconfigured(
            "The browser refused the RP ID for this page: $detail",
            "Use the page's host (or a registrable suffix of it) as the RP ID, and serve the page over HTTPS " +
                "(http://localhost works for development).",
        )
        "NotSupportedError" -> PasskeyError.NotSupported(detail)
        "AbortError" -> PasskeyError.Interrupted
        "TypeError", "SyntaxError", "DataError" -> PasskeyError.InvalidOptions("The browser rejected the options: $detail")
        else -> PasskeyError.Unknown(detail, name, this)
    }
}
