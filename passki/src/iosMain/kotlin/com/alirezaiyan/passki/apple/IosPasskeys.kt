package com.alirezaiyan.passki.apple

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
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.passkeyCatching
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationPasswordProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASPasswordCredential
import kotlinx.coroutines.delay
import platform.UIKit.UIWindow

internal const val AUTOFILL_RETRY_DELAY_MS: Long = 500

/** A 1001 (canceled) during autofill means the user dismissed the suggestion sheet; keep offering. */
internal fun isAutoFillDismissal(error: Throwable): Boolean = error is AppleAuthorizationException && error.code == 1001L

internal class IosPasskeys(private val anchor: () -> UIWindow? = ::keyWindow) : Passkeys {

    override suspend fun capabilities(): PasskeyCapabilities = PasskeyCapabilities(
        isSupported = iosAtLeast(16),
        immediateSignIn = iosAtLeast(16),
        unifiedPasswordSignIn = iosAtLeast(16),
        conditionalCreate = iosAtLeast(18),
        signals = iosAtLeast(26),
        autofill = iosAtLeast(16),
    )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
        val immediateOnly = mode == SignInMode.ImmediateOnly
        return passkeyCatching({ it.toPasskeyError(immediateOnly) }) {
            requireIos(16, "Passkeys")
            val options = WebAuthnOptions.parseRequest(optionsJson).model
            val authorization = if (mode == SignInMode.Autofill) {
                autoFill { AuthorizationSession.performAutoFill(listOf(AppleRequests.assertion(options)), anchor) }
            } else {
                AuthorizationSession.perform(
                    requests = listOf(AppleRequests.assertion(options)),
                    anchor = anchor,
                    preferImmediatelyAvailable = immediateOnly,
                )
            }
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialAssertion -> parseAssertion(AppleRequests.assertionJson(credential, options.extensions), WebAuthnOptions.requireRpId(options.rpId, "rpId"))
                else -> throw unexpected(credential)
            }
        }
    }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireIos(16, "Passkeys")
            val options = WebAuthnOptions.parseRequest(optionsJson).model
            val authorization = AuthorizationSession.perform(
                requests = listOf(AppleRequests.assertion(options), ASAuthorizationPasswordProvider().createRequest()),
                anchor = anchor,
                preferImmediatelyAvailable = false,
            )
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialAssertion ->
                    SignInCredential.Passkey(parseAssertion(AppleRequests.assertionJson(credential, options.extensions), WebAuthnOptions.requireRpId(options.rpId, "rpId")))
                is ASPasswordCredential -> SignInCredential.Password(credential.user, credential.password)
                else -> throw unexpected(credential)
            }
        }

    override val signals: PasskeySignals = IosSignals

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> =
        passkeyCatching({ it.toPasskeyError(conditional = conditional) }) {
            requireIos(16, "Passkeys")
            if (conditional) requireIos(18, "Conditional passkey creation (upgrade)")
            val options = WebAuthnOptions.parseCreation(optionsJson).model
            val authorization = AuthorizationSession.perform(
                requests = listOf(AppleRequests.registration(options, conditional)),
                anchor = anchor,
                preferImmediatelyAvailable = false,
            )
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialRegistration ->
                    parseRegistration(AppleRequests.registrationJson(credential), WebAuthnOptions.requireRpId(options.rp.id, "rp.id"))
                else -> throw unexpected(credential)
            }
        }

    /**
     * Autofill keeps offering passkeys until one is picked: a dismissal, or a modal request taking over (1001 not
     * caused by our own cancel), restarts the request after a short pause, so a provider that answers 1001
     * immediately can't spin the main thread. The restart waits until no modal request runs ([AuthorizationArbiter]).
     * Our own cancel (coroutine cancelled) ends the loop through [delay] / the cancelled continuation.
     */
    private suspend fun autoFill(run: suspend () -> ASAuthorization): ASAuthorization {
        while (true) {
            try {
                return run()
            } catch (e: AppleAuthorizationException) {
                if (!isAutoFillDismissal(e)) throw e
                delay(AUTOFILL_RETRY_DELAY_MS)
            }
        }
    }

    private fun unexpected(value: Any?): PasskeyException =
        PasskeyException(PasskeyError.Unknown("Unexpected credential type ${value?.let { it::class.simpleName }}.", null))
}
