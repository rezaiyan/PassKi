package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.SignalOutcome
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** JSON bodies defined by the WebAuthn Signal API (signalUnknownCredential & co). */
@InternalPasskiApi
public object SignalRequests {
    public fun unknownCredential(rpId: String, credentialId: String): String {
        val credentialId = canonical(credentialId, "credentialId")
        return buildJsonObject {
            put("rpId", rpId)
            put("credentialId", credentialId)
        }.toString()
    }

    public fun acceptedCredentials(rpId: String, userHandle: String, credentialIds: List<String>): String {
        val userHandle = canonical(userHandle, "userHandle")
        val credentialIds = credentialIds.map { canonical(it, "credentialIds") }
        return buildJsonObject {
            put("rpId", rpId)
            put("userId", userHandle) // the Signal API's name for the user handle
            putJsonArray("allAcceptedCredentialIds") { credentialIds.forEach { add(it) } }
        }.toString()
    }

    public fun currentUserDetails(rpId: String, userHandle: String, name: String, displayName: String): String {
        val userHandle = canonical(userHandle, "userHandle")
        return buildJsonObject {
            put("rpId", rpId)
            put("userId", userHandle) // the Signal API's name for the user handle
            put("name", name)
            put("displayName", displayName)
        }.toString()
    }

    /**
     * The error for a signal argument that isn't base64url: the message names the public parameter ([field]), and
     * the fix hint says where that value comes from.
     */
    public fun invalidArgument(field: String, detail: String?): PasskeyError.InvalidOptions =
        PasskeyError.InvalidOptions(
            message = "$field must be base64url" + detail?.let { ": $it" }.orEmpty(),
            checkId = null,
            fixHint = if (field == "userHandle") USER_HANDLE_HINT else CREDENTIAL_ID_HINT,
        )

    private fun canonical(value: String, field: String): String {
        try {
            return Base64Url.encode(Base64Url.decode(value))
        } catch (e: IllegalArgumentException) {
            throw PasskeyException(invalidArgument(field, e.message))
        }
    }

    private const val USER_HANDLE_HINT: String =
        "Pass the WebAuthn user handle: the base64url user.id your server put in the creation options (also " +
            "returned as PasskeyAssertion.userHandle), never your account id or an email address."

    private const val CREDENTIAL_ID_HINT: String =
        "Pass credential IDs as the base64url ids PassKi returned (PasskeyRegistration.credentialId, " +
            "PasskeyAssertion.credentialId), as your server stored them."
}

/**
 * A platform signal result as a [SignalOutcome]: success is [SignalOutcome.Applied], and "this OS or provider can't
 * do it" ([PasskeyError.NotSupported]) becomes [SignalOutcome.Skipped] with its message. Other failures stay failures.
 */
@InternalPasskiApi
public fun PasskeyResult<Unit>.toSignalOutcome(): PasskeyResult<SignalOutcome> = when (this) {
    is PasskeyResult.Success -> PasskeyResult.Success(SignalOutcome.Applied)
    is PasskeyResult.Failure -> when (val e = error) {
        is PasskeyError.NotSupported -> PasskeyResult.Success(SignalOutcome.Skipped(e.message))
        else -> this
    }
}
