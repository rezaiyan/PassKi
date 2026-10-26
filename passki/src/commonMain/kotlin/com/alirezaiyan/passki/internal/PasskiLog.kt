package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PassKi
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyErrorCategory
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskiLogLevel
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.SignalOutcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * PassKi's log calls. Every public operation goes through one of the call helpers below (the platform
 * implementations via [LoggingPasskeys], passki-test's FakePasskeys directly), so all of them log the same events.
 *
 * Nothing logged here may contain a challenge, user handle, options or response JSON, a password, PRF output or
 * large-blob data: log credential IDs with [shortId] and JSON with [lengthOf]. `PasskiLogRedactionTest` checks a
 * full debug trace for leaks.
 */
@InternalPasskiApi
public object PasskiLog {
    public const val TAG: String = "PassKi"

    public fun isLoggable(level: PasskiLogLevel): Boolean {
        val current = PassKi.logLevel
        return level != PasskiLogLevel.None && current != PasskiLogLevel.None && level.ordinal <= current.ordinal
    }

    /** Logs [message] at [level] if [PassKi.logLevel] includes it; [message] is only built then. */
    public fun log(level: PasskiLogLevel, throwable: Throwable? = null, message: () -> String) {
        if (isLoggable(level)) PassKi.logHandler.log(level, TAG, message(), throwable)
    }

    public suspend fun capabilities(block: suspend () -> PasskeyCapabilities): PasskeyCapabilities =
        block().also { capabilities -> log(PasskiLogLevel.Debug) { "capabilities: $capabilities" } }

    public suspend fun register(
        optionsJson: String,
        block: suspend () -> PasskeyResult<PasskeyRegistration>,
    ): PasskeyResult<PasskeyRegistration> = call("register", { optionsDetail(optionsJson) }, ::describe, block)

    public suspend fun upgrade(
        optionsJson: String,
        block: suspend () -> PasskeyResult<PasskeyRegistration>,
    ): PasskeyResult<PasskeyRegistration> =
        call("upgrade", { "needs=conditionalCreate, " + optionsDetail(optionsJson) }, ::describe, block)

    public suspend fun signIn(
        optionsJson: String,
        mode: SignInMode,
        block: suspend () -> PasskeyResult<PasskeyAssertion>,
    ): PasskeyResult<PasskeyAssertion> {
        val needs = when (mode) {
            SignInMode.Modal -> ""
            SignInMode.ImmediateOnly -> ", needs=immediateSignIn"
            SignInMode.Autofill -> ", needs=autofill"
        }
        return call("signIn", { "mode=$mode$needs, " + optionsDetail(optionsJson) }, ::describe, block)
    }

    public suspend fun signInOrPassword(
        optionsJson: String,
        block: suspend () -> PasskeyResult<SignInCredential>,
    ): PasskeyResult<SignInCredential> =
        call("signInOrPassword", { "needs=unifiedPasswordSignIn, " + optionsDetail(optionsJson) }, ::describe, block)

    public suspend fun unknownCredential(
        rpId: String,
        credentialId: String,
        block: suspend () -> PasskeyResult<SignalOutcome>,
    ): PasskeyResult<SignalOutcome> =
        call("signals.unknownCredential", { "rpId=$rpId, credential=${shortId(credentialId)}" }, ::describe, block)

    public suspend fun acceptedCredentials(
        rpId: String,
        credentialIds: List<String>,
        block: suspend () -> PasskeyResult<SignalOutcome>,
    ): PasskeyResult<SignalOutcome> =
        call("signals.acceptedCredentials", { "rpId=$rpId, credentials=${credentialIds.size}" }, ::describe, block)

    public suspend fun currentUserDetails(
        rpId: String,
        block: suspend () -> PasskeyResult<SignalOutcome>,
    ): PasskeyResult<SignalOutcome> = call("signals.currentUserDetails", { "rpId=$rpId" }, ::describe, block)

    public suspend fun syncAccount(
        rpId: String,
        credentialIds: List<String>,
        block: suspend () -> PasskeyResult<SignalOutcome>,
    ): PasskeyResult<SignalOutcome> =
        call("signals.syncAccount", { "rpId=$rpId, credentials=${credentialIds.size}" }, ::describe, block)

    /** The debug line for a platform error that [passkeyCatching] mapped to [mapped]. */
    public fun platformError(error: Throwable, mapped: PasskeyError) {
        log(PasskiLogLevel.Debug) {
            "platform error ${error::class.simpleName}: ${error.message?.withoutJsonInput()} -> " +
                mapped::class.simpleName
        }
    }

    /** The first 8 characters of a credential ID, enough to tell passkeys apart in a log. */
    public fun shortId(id: String): String = if (id.isEmpty()) "<empty>" else id.take(SHORT_ID_LENGTH) + "…"

    /** A JSON string as its length only. */
    public fun lengthOf(json: String): String = "<${json.length} chars>"

    /** Which level a failed call is logged at: setup problems are errors, the user's own choices only info. */
    public fun levelOf(error: PasskeyError): PasskiLogLevel = when (error.category) {
        // A double tap or the app going to the background: worth a warning, unlike the user closing the sheet.
        PasskeyErrorCategory.UserCancelled -> if (error == PasskeyError.Interrupted) PasskiLogLevel.Warn else PasskiLogLevel.Info
        PasskeyErrorCategory.NoCredential, PasskeyErrorCategory.AlreadyExists -> PasskiLogLevel.Info
        PasskeyErrorCategory.Unsupported -> PasskiLogLevel.Warn
        PasskeyErrorCategory.Setup, PasskeyErrorCategory.Unexpected -> PasskiLogLevel.Error
    }

    private suspend fun <T> call(
        operation: String,
        start: () -> String,
        describe: (T) -> String,
        block: suspend () -> PasskeyResult<T>,
    ): PasskeyResult<T> {
        log(PasskiLogLevel.Debug) { "$operation started: ${start()}" }
        val result = try {
            block()
        } catch (e: CancellationException) {
            log(PasskiLogLevel.Debug) { "$operation withdrawn: the calling coroutine was cancelled" }
            throw e
        }
        when (result) {
            is PasskeyResult.Success -> log(PasskiLogLevel.Info) { "$operation succeeded: ${describe(result.value)}" }
            is PasskeyResult.Failure -> {
                val error = result.error
                log(levelOf(error), (error as? PasskeyError.Unknown)?.cause) { "$operation failed: ${describe(error)}" }
            }
        }
        return result
    }

    private fun describe(error: PasskeyError): String = buildString {
        append(error::class.simpleName)
        error.checkId?.let { append(" [").append(it).append(']') }
        append(": ").append(error.message)
        (error as? PasskeyError.Unknown)?.platformCode?.let { append(" (platform code ").append(it).append(')') }
        error.fixHint?.let { append(" Fix: ").append(it) }
        error.docsUrl?.let { append(" Docs: ").append(it) }
    }

    private fun describe(registration: PasskeyRegistration): String =
        "credential=${shortId(registration.credentialId)}, json=${lengthOf(registration.json)}"

    private fun describe(assertion: PasskeyAssertion): String =
        "credential=${shortId(assertion.credentialId)}, json=${lengthOf(assertion.json)}"

    private fun describe(credential: SignInCredential): String = when (credential) {
        is SignInCredential.Passkey -> "passkey, " + describe(credential.assertion)
        is SignInCredential.Password -> "password for ${credential.id}"
    }

    private fun describe(outcome: SignalOutcome): String = when (outcome) {
        SignalOutcome.Applied -> "applied"
        is SignalOutcome.Skipped -> "skipped (${outcome.reason})"
    }

    /** RP ID, options length and requested extensions; never the options themselves. */
    private fun optionsDetail(optionsJson: String): String {
        val options = try {
            (PasskiJson.parseToJsonElement(optionsJson) as? JsonObject)?.let { it["publicKey"] as? JsonObject ?: it }
        } catch (e: IllegalArgumentException) {
            null
        }
        val rpId = (options?.get("rpId") as? JsonPrimitive)?.content
            ?: ((options?.get("rp") as? JsonObject)?.get("id") as? JsonPrimitive)?.content
        val extensions = (options?.get("extensions") as? JsonObject)?.keys.orEmpty()
        return "rpId=${rpId ?: "<none>"}, options=${lengthOf(optionsJson)}" +
            if (extensions.isEmpty()) "" else ", extensions=${extensions.joinToString(prefix = "[", postfix = "]")}"
    }

    private const val SHORT_ID_LENGTH = 8
}

/**
 * kotlinx.serialization error messages end with the JSON they failed on ("JSON input: ..."), which can hold a
 * challenge or user handle; error messages and logs keep only the part before it.
 */
internal fun String.withoutJsonInput(): String = substringBefore("\nJSON input:").substringBefore("JSON input:").trimEnd()
