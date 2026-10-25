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
import com.alirezaiyan.passki.internal.ExtensionsJson
import com.alirezaiyan.passki.internal.SignalRequests
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.passkeyCatching
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal const val AUTOFILL_RETRY_DELAY_MS: Long = 500
private const val MODAL_POLL_DELAY_MS: Long = 100

/** How long [Passkeys.upgrade] waits for the browser when the options set no `timeout`. */
internal const val UPGRADE_TIMEOUT_MS: Long = 10_000

/**
 * Passkeys through the browser's WebAuthn API (`navigator.credentials`). The browser checks that the RP ID matches
 * the page's origin, so unlike on Android and iOS the RP ID in the options is optional: it defaults to the page's
 * domain.
 */
internal class WebPasskeys : Passkeys {
    /** Modal requests in flight; an autofill request waits for them so it doesn't take the sheet away. */
    private var modalRequests = 0

    /** The pending autofill (conditional mediation) request, aborted while a modal request runs. */
    private var autofillHandle: JsAny? = null

    override suspend fun capabilities(): PasskeyCapabilities = WebCapabilities.parse(WebAuthnBridge.capabilities())

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, WebOperation.Register)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, WebOperation.Upgrade)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
        val operation = when (mode) {
            SignInMode.ImmediateOnly -> WebOperation.ImmediateSignIn
            SignInMode.Autofill -> WebOperation.Autofill
            else -> WebOperation.SignIn
        }
        return passkeyCatching(::unexpectedError) {
            val options = WebAuthnOptions.parseRequest(optionsJson)
            options.model.rpId?.let { WebAuthnOptions.requireRpId(it, "rpId") }
            ExtensionsJson.prfInput(options.model.extensions)
            ExtensionsJson.largeBlobAssertion(options.model.extensions)
            val outcome = when (operation) {
                WebOperation.Autofill -> {
                    requireCapability(capabilities().autofill, "Passkey autofill (conditional mediation)")
                    autofill(options.json)
                }
                WebOperation.ImmediateSignIn -> {
                    requireCapability(capabilities().immediateSignIn, "Immediate sign-in (mediation \"immediate\")")
                    modal { WebAuthnBridge.get(options.json, "immediate") }
                }
                else -> modal { WebAuthnBridge.get(options.json, "") }
            }
            parseAssertion(outcome.jsonOrThrow(operation))
        }
    }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        PasskeyResult.Failure(
            PasskeyError.NotSupported(
                "Browsers have no combined passkey and password sheet. Use signIn(), and let the browser's password " +
                    "manager fill your password form.",
            ),
        )

    override val signals: PasskeySignals = WebSignals

    private suspend fun createPasskey(optionsJson: String, operation: WebOperation): PasskeyResult<PasskeyRegistration> =
        passkeyCatching(::unexpectedError) {
            val options = WebAuthnOptions.parseCreation(optionsJson)
            options.model.rp.id?.let { WebAuthnOptions.requireRpId(it, "rp.id") }
            ExtensionsJson.prfInput(options.model.extensions)
            val outcome = if (operation == WebOperation.Upgrade) {
                requireCapability(capabilities().conditionalCreate, "Conditional passkey creation (upgrade)")
                // A browser that won't create a passkey may leave the request pending instead of rejecting it.
                withTimeoutOrNull(options.model.timeout ?: UPGRADE_TIMEOUT_MS) {
                    WebAuthnBridge.create(options.json, "conditional")
                } ?: BridgeOutcome.Failure("TimeoutError", "No passkey was created in time.")
            } else {
                modal { WebAuthnBridge.create(options.json, "") }
            }
            parseRegistration(outcome.jsonOrThrow(operation))
        }

    private suspend fun modal(request: suspend () -> BridgeOutcome): BridgeOutcome {
        modalRequests++
        try {
            // Browsers allow one pending request; take the sheet from autofill rather than fail. Autofill restarts after.
            WebAuthnBridge.abort(autofillHandle)
            return request()
        } finally {
            modalRequests--
        }
    }

    /**
     * Keeps offering passkeys in the username field until one is picked. An abort we didn't ask for (a modal request
     * took over, or the browser dropped the request) restarts it once no modal request runs. Cancelling the calling
     * coroutine aborts the request and ends the loop.
     */
    private suspend fun autofill(optionsJson: String): BridgeOutcome {
        while (true) {
            while (modalRequests > 0) delay(MODAL_POLL_DELAY_MS)
            val outcome = try {
                WebAuthnBridge.get(optionsJson, "conditional", onStart = { autofillHandle = it })
            } finally {
                autofillHandle = null
            }
            if (outcome !is BridgeOutcome.Failure || outcome.name != "AbortError") return outcome
            delay(AUTOFILL_RETRY_DELAY_MS)
        }
    }

    private fun requireCapability(available: Boolean, feature: String) {
        if (!available) throw PasskeyException(PasskeyError.NotSupported("$feature is not supported by this browser."))
    }
}

private object WebSignals : PasskeySignals {
    override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> =
        signal("signalUnknownCredential", rpId) { SignalRequests.unknownCredential(rpId, credentialId) }

    override suspend fun acceptedCredentials(rpId: String, userId: String, credentialIds: List<String>): PasskeyResult<Unit> =
        signal("signalAllAcceptedCredentials", rpId) { SignalRequests.acceptedCredentials(rpId, userId, credentialIds) }

    override suspend fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): PasskeyResult<Unit> =
        signal("signalCurrentUserDetails", rpId) { SignalRequests.currentUserDetails(rpId, userId, name, displayName) }

    private suspend fun signal(method: String, rpId: String, body: () -> String): PasskeyResult<Unit> =
        passkeyCatching(::unexpectedError) {
            WebAuthnOptions.requireRpId(rpId, "rpId")
            WebAuthnBridge.signal(method, body()).jsonOrThrow(WebOperation.Signal)
            Unit
        }
}

private fun BridgeOutcome.jsonOrThrow(operation: WebOperation): String = when (this) {
    is BridgeOutcome.Success -> json
    is BridgeOutcome.Failure -> throw PasskeyException(webPasskeyError(name, message, operation))
}

private fun unexpectedError(error: Throwable): PasskeyError =
    PasskeyError.Unknown(error.message ?: error::class.simpleName.orEmpty(), error::class.simpleName, error)
