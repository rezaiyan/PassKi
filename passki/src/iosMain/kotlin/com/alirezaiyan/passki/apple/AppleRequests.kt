package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.CreationOptionsJson
import com.alirezaiyan.passki.internal.CredentialDescriptorJson
import com.alirezaiyan.passki.internal.ExtensionsJson
import com.alirezaiyan.passki.internal.RequestOptionsJson
import com.alirezaiyan.passki.internal.WebAuthnResponses
import kotlinx.serialization.json.JsonObject
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInputValues
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFRegistrationInput
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
        // Extensions the OS can't handle are omitted silently, as browsers do (PRF: iOS 18, largeBlob: iOS 17).
        ExtensionsJson.prfInput(options.extensions)?.takeIf { iosAtLeast(18) }?.let { request.prf = prfRegistrationInput(it) }
        ExtensionsJson.largeBlobSupport(options.extensions)?.takeIf { iosAtLeast(17) }?.let { support ->
            request.largeBlob = ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput(
                supportRequirement = if (support == "required") {
                    ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementRequired
                } else {
                    ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementPreferred
                },
            )
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
        ExtensionsJson.prfInput(options.extensions)?.takeIf { iosAtLeast(18) }?.let { prf ->
            val perCredential = buildMap<Any?, Any?> {
                prf.evalByCredential.forEach { (id, values) -> put(Base64Url.decode(id).toNSData(), values.toApple()) }
            }
            if (prf.eval != null || perCredential.isNotEmpty()) {
                request.prf = ASAuthorizationPublicKeyCredentialPRFAssertionInput(
                    inputValues = prf.eval?.toApple(),
                    perCredentialInputValues = perCredential.takeIf { it.isNotEmpty() },
                )
            }
        }
        ExtensionsJson.largeBlobAssertion(options.extensions)?.takeIf { iosAtLeast(17) }?.let { largeBlob ->
            request.largeBlob = when (largeBlob) {
                ExtensionsJson.LargeBlobAssertion.Read -> ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput(
                    operation = ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationRead,
                )
                is ExtensionsJson.LargeBlobAssertion.Write -> ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput(
                    operation = ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationWrite,
                ).apply { dataToWrite = largeBlob.blob.toNSData() }
            }
        }
        return request
    }

    // AuthenticationServices does not expose the WebAuthn L3 publicKey, publicKeyAlgorithm or authenticatorData
    // response fields, so they are not emitted; servers read the credential public key from attestationObject.
    fun registrationJson(credential: ASAuthorizationPlatformPublicKeyCredentialRegistration): String {
        // `attachment` only exists on iOS 16.6+: guard the property read itself, not just the comparison.
        val attachment = if (iosAtLeast(16, 6)) attachmentOf(credential.attachment) else "platform"
        return WebAuthnResponses.registration(
            credentialId = credential.credentialID.toByteArray(),
            clientDataJson = credential.rawClientDataJSON.toByteArray(),
            attestationObject = credential.rawAttestationObject?.toByteArray() ?: throw missing("attestationObject"),
            // A cross-platform (security key / phone) credential was made over hybrid, not on this device.
            transports = if (attachment == "cross-platform") listOf("hybrid") else listOf("hybrid", "internal"),
            attachment = attachment,
            extensions = registrationExtensions(credential),
        )
    }

    private fun registrationExtensions(credential: ASAuthorizationPlatformPublicKeyCredentialRegistration) =
        ExtensionsJson.registrationOutputs(
            prfEnabled = if (iosAtLeast(18)) credential.prf?.isSupported else null,
            prf = if (iosAtLeast(18)) {
                credential.prf?.let { prf -> prf.first?.let { ExtensionsJson.PrfValues(it.toByteArray(), prf.second?.toByteArray()) } }
            } else {
                null
            },
            largeBlobSupported = if (iosAtLeast(17)) credential.largeBlob?.isSupported else null,
        )

    /** [requested] is the assertion's options `extensions`, so a largeBlob read and write report the right field. */
    fun assertionJson(credential: ASAuthorizationPlatformPublicKeyCredentialAssertion, requested: JsonObject?): String =
        WebAuthnResponses.authentication(
            credentialId = credential.credentialID.toByteArray(),
            clientDataJson = credential.rawClientDataJSON.toByteArray(),
            authenticatorData = credential.rawAuthenticatorData?.toByteArray() ?: throw missing("authenticatorData"),
            signature = credential.signature?.toByteArray() ?: throw missing("signature"),
            userHandle = credential.userID?.takeIf { it.length > 0uL }?.toByteArray(),
            attachment = if (iosAtLeast(16, 6)) attachmentOf(credential.attachment) else "platform",
            extensions = assertionExtensions(credential, requested),
        )

    private fun assertionExtensions(
        credential: ASAuthorizationPlatformPublicKeyCredentialAssertion,
        requested: JsonObject?,
    ): JsonObject {
        val prf = if (iosAtLeast(18)) {
            credential.prf?.let { ExtensionsJson.PrfValues(it.first.toByteArray(), it.second?.toByteArray()) }
        } else {
            null
        }
        val largeBlob = if (iosAtLeast(17)) credential.largeBlob else null
        val op = ExtensionsJson.largeBlobAssertion(requested)
        return ExtensionsJson.assertionOutputs(
            prf = prf,
            largeBlob = if (op == ExtensionsJson.LargeBlobAssertion.Read) largeBlob?.readData?.toByteArray() else null,
            largeBlobWritten = if (op is ExtensionsJson.LargeBlobAssertion.Write) largeBlob?.didWrite else null,
        )
    }

    /**
     * Salts given → evaluate at registration (iOS then also reports support); none → only check support.
     * Note: the request's `prf` getter does not hand `inputValues` back, so tests check this function instead.
     */
    fun prfRegistrationInput(prf: ExtensionsJson.PrfInput): ASAuthorizationPublicKeyCredentialPRFRegistrationInput =
        prf.eval?.let { ASAuthorizationPublicKeyCredentialPRFRegistrationInput(inputValues = it.toApple()) }
            ?: ASAuthorizationPublicKeyCredentialPRFRegistrationInput.checkForSupport()

    private fun ExtensionsJson.PrfValues.toApple() = ASAuthorizationPublicKeyCredentialPRFAssertionInputValues(
        saltInput1 = first.toNSData(),
        saltInput2 = second?.toNSData(),
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
