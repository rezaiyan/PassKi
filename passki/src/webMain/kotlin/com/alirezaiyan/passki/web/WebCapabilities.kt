package com.alirezaiyan.passki.web

import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.internal.PasskiJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Reads the snapshot the bridge takes of the browser: `PublicKeyCredential.getClientCapabilities()` (in `client`) where
 * the browser has it, else the older `isConditionalMediationAvailable()` and the presence of the Signal API methods.
 */
internal object WebCapabilities {
    fun parse(json: String): PasskeyCapabilities {
        val root = runCatching { PasskiJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val client = root["client"] as? JsonObject ?: JsonObject(emptyMap())
        val supported = root.flag("supported") == true
        fun available(clientKey: String, fallback: Boolean): Boolean = supported && (client.flag(clientKey) ?: fallback)
        val signalMethods = listOf("signalUnknownCredential", "signalAllAcceptedCredentials", "signalCurrentUserDetails")
        return PasskeyCapabilities(
            isSupported = supported,
            // Probed rather than read from getClientCapabilities(): some Chromium builds announce immediateGet but
            // reject mediation "immediate".
            immediateSignIn = supported && root.flag("immediateMediation") == true,
            // Browsers have no single sheet for passkeys and saved passwords that returns both to the page.
            unifiedPasswordSignIn = false,
            // Conditional create is new; only getClientCapabilities() announces it.
            conditionalCreate = available("conditionalCreate", fallback = false),
            signals = supported && signalMethods.all { root.flag(it) == true },
            autofill = available("conditionalGet", fallback = root.flag("conditionalMediation") == true),
        )
    }

    private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
