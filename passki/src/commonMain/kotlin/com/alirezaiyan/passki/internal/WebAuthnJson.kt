package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@InternalPasskiApi
public val PasskiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@InternalPasskiApi
@Serializable
public data class CreationOptionsJson(
    public val rp: RelyingPartyJson,
    public val user: UserJson,
    public val challenge: String,
    public val pubKeyCredParams: List<PubKeyCredParamJson> = emptyList(),
    public val timeout: Long? = null,
    public val excludeCredentials: List<CredentialDescriptorJson> = emptyList(),
    public val authenticatorSelection: AuthenticatorSelectionJson? = null,
    public val attestation: String? = null,
    public val extensions: JsonObject? = null,
)

@InternalPasskiApi
@Serializable
public data class RelyingPartyJson(public val id: String? = null, public val name: String)

@InternalPasskiApi
@Serializable
public data class UserJson(public val id: String, public val name: String, public val displayName: String = "")

@InternalPasskiApi
@Serializable
public data class PubKeyCredParamJson(public val type: String = "public-key", public val alg: Int)

@InternalPasskiApi
@Serializable
public data class CredentialDescriptorJson(
    public val id: String,
    public val type: String = "public-key",
    public val transports: List<String> = emptyList(),
)

@InternalPasskiApi
@Serializable
public data class AuthenticatorSelectionJson(
    public val authenticatorAttachment: String? = null,
    public val residentKey: String? = null,
    public val requireResidentKey: Boolean? = null,
    public val userVerification: String? = null,
)

@InternalPasskiApi
@Serializable
public data class RequestOptionsJson(
    public val challenge: String,
    public val timeout: Long? = null,
    public val rpId: String? = null,
    public val allowCredentials: List<CredentialDescriptorJson> = emptyList(),
    public val userVerification: String? = null,
    public val extensions: JsonObject? = null,
)

@InternalPasskiApi
@Serializable
public data class RegistrationResponseJson(
    public val id: String,
    public val rawId: String,
    public val type: String = "public-key",
    public val response: AttestationResponseJson,
    public val authenticatorAttachment: String? = null,
    public val clientExtensionResults: JsonObject = JsonObject(emptyMap()),
)

@InternalPasskiApi
@Serializable
public data class AttestationResponseJson(
    public val clientDataJSON: String,
    public val attestationObject: String,
    public val transports: List<String> = emptyList(),
)

@InternalPasskiApi
@Serializable
public data class AuthenticationResponseJson(
    public val id: String,
    public val rawId: String,
    public val type: String = "public-key",
    public val response: AssertionResponseJson,
    public val authenticatorAttachment: String? = null,
    public val clientExtensionResults: JsonObject = JsonObject(emptyMap()),
)

@InternalPasskiApi
@Serializable
public data class AssertionResponseJson(
    public val clientDataJSON: String,
    public val authenticatorData: String,
    public val signature: String,
    public val userHandle: String? = null,
)
