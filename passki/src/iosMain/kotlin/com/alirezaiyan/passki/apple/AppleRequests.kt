package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.CreationOptionsJson
import com.alirezaiyan.passki.internal.CredentialDescriptorJson
import com.alirezaiyan.passki.internal.RequestOptionsJson
import com.alirezaiyan.passki.internal.WebAuthnResponses
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.AuthenticationServices.ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequestProtocol

/** Maps WebAuthn JSON options to AuthenticationServices requests and back. */
internal object AppleRequests {

    fun registration(
        options: CreationOptionsJson,
        conditional: Boolean,
    ): ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest {
        val rpId = options.rp.id ?: throw invalid("rp.id is required on iOS. Set the RP ID in your server's registration options.")
        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier = rpId)
        val request = provider.createCredentialRegistrationRequestWithChallenge(
            challenge = Base64Url.decode(options.challenge).toNSData(),
            name = options.user.name,
            userID = Base64Url.decode(options.user.id).toNSData(),
        )
        if (options.user.displayName.isNotEmpty()) request.displayName = options.user.displayName
        options.authenticatorSelection?.userVerification?.let { request.userVerificationPreference = it }
        options.attestation?.let { request.attestationPreference = it }
        if (options.excludeCredentials.isNotEmpty() && iosAtLeast(17, 4)) {
            // iOS 17.4 adds excludedCredentials through a protocol conformance declared in a class
            // extension, which Kotlin/Native does not attach to the request class: reach it via the protocol.
            (request as? ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequestProtocol)
                ?.excludedCredentials = options.excludeCredentials.map { it.toApple() }
        }
        // Only reached on iOS 18+: callers guard conditional creation with requireIos(18).
        if (conditional) {
            request.requestStyle =
                ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyleConditional
        }
        return request
    }

    fun assertion(options: RequestOptionsJson): ASAuthorizationPlatformPublicKeyCredentialAssertionRequest {
        val rpId = options.rpId ?: throw invalid("rpId is required on iOS. Set rpId in your server's authentication options.")
        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier = rpId)
        val request = provider.createCredentialAssertionRequestWithChallenge(Base64Url.decode(options.challenge).toNSData())
        if (options.allowCredentials.isNotEmpty()) {
            request.allowedCredentials = options.allowCredentials.map { it.toApple() }
        }
        options.userVerification?.let { request.userVerificationPreference = it }
        return request
    }

    fun registrationJson(credential: ASAuthorizationPlatformPublicKeyCredentialRegistration): String =
        WebAuthnResponses.registration(
            credentialId = credential.credentialID.toByteArray(),
            clientDataJson = credential.rawClientDataJSON.toByteArray(),
            attestationObject = credential.rawAttestationObject?.toByteArray() ?: throw missing("attestationObject"),
            transports = listOf("hybrid", "internal"),
            // `attachment` only exists on iOS 16.6+: guard the property read itself, not just the comparison.
            attachment = if (iosAtLeast(16, 6)) attachmentOf(credential.attachment) else "platform",
        )

    fun assertionJson(credential: ASAuthorizationPlatformPublicKeyCredentialAssertion): String =
        WebAuthnResponses.authentication(
            credentialId = credential.credentialID.toByteArray(),
            clientDataJson = credential.rawClientDataJSON.toByteArray(),
            authenticatorData = credential.rawAuthenticatorData?.toByteArray() ?: throw missing("authenticatorData"),
            signature = credential.signature?.toByteArray() ?: throw missing("signature"),
            userHandle = credential.userID?.toByteArray(),
            attachment = if (iosAtLeast(16, 6)) attachmentOf(credential.attachment) else "platform",
        )

    private fun attachmentOf(value: ASAuthorizationPublicKeyCredentialAttachment): String =
        if (value == ASAuthorizationPublicKeyCredentialAttachment.ASAuthorizationPublicKeyCredentialAttachmentCrossPlatform) {
            "cross-platform"
        } else {
            "platform"
        }

    private fun CredentialDescriptorJson.toApple() =
        ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = Base64Url.decode(id).toNSData())

    private fun invalid(message: String) = PasskeyException(PasskeyError.InvalidOptions(message))

    private fun missing(field: String) =
        PasskeyException(PasskeyError.Unknown("iOS returned no $field.", platformCode = null))
}
