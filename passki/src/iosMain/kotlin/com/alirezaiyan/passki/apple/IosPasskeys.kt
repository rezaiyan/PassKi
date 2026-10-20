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
import platform.AuthenticationServices.ASAuthorizationPasswordProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASPasswordCredential
import platform.UIKit.UIWindow

internal class IosPasskeys(private val anchor: () -> UIWindow? = ::keyWindow) : Passkeys {

    override suspend fun capabilities(): PasskeyCapabilities = PasskeyCapabilities(
        isSupported = iosAtLeast(16),
        immediateSignIn = iosAtLeast(16),
        unifiedPasswordSignIn = iosAtLeast(16),
        conditionalCreate = iosAtLeast(18),
        signals = false,
    )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireIos(16, "Passkeys")
            val options = WebAuthnOptions.parseRequest(optionsJson).model
            val authorization = AuthorizationSession.perform(
                requests = listOf(AppleRequests.assertion(options)),
                anchor = anchor,
                preferImmediatelyAvailable = mode == SignInMode.ImmediateOnly,
            )
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialAssertion -> parseAssertion(AppleRequests.assertionJson(credential))
                else -> throw unexpected(credential)
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
                    SignInCredential.Passkey(parseAssertion(AppleRequests.assertionJson(credential)))
                is ASPasswordCredential -> SignInCredential.Password(credential.user, credential.password)
                else -> throw unexpected(credential)
            }
        }

    override val signals: PasskeySignals = object : PasskeySignals {
        private val failure = PasskeyResult.Failure(
            PasskeyError.NotSupported(
                "The Signal API on iOS (ASCredentialUpdater, iOS 26) is Swift-only and is not available yet.",
            ),
        )

        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> = failure

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = failure

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = failure
    }

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> =
        passkeyCatching(Throwable::toPasskeyError) {
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
                    parseRegistration(AppleRequests.registrationJson(credential))
                else -> throw unexpected(credential)
            }
        }

    private fun unexpected(value: Any?): PasskeyException =
        PasskeyException(PasskeyError.Unknown("Unexpected credential type ${value?.let { it::class.simpleName }}.", null))
}
