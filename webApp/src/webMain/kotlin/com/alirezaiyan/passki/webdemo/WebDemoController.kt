package com.alirezaiyan.passki.webdemo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alirezaiyan.passki.Activity
import com.alirezaiyan.passki.Outcome
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyExtensions
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fixed PRF salt: the same passkey must return the same output for it on every run. */
private val PRF_SALT = "passki-web-demo-prf-salt-v1".encodeToByteArray()

/** The web sample's flows: PassKi in the browser against [LocalRelyingParty]. Each flow logs one [Activity]. */
class WebDemoController(
    val passkeys: Passkeys,
    val rp: LocalRelyingParty,
    private val scope: CoroutineScope,
) {
    var activities by mutableStateOf(listOf<Activity>())
        private set
    var running by mutableStateOf<String?>(null)
        private set
    var capabilities by mutableStateOf<PasskeyCapabilities?>(null)
        private set
    var username by mutableStateOf("")

    /** Who signed in last, from the user handle; null before anyone did. */
    var signedInAs by mutableStateOf<String?>(null)
        private set

    private val prfByCredential = mutableMapOf<String, String>()
    private var seq = 0

    fun latest(label: String): Activity? = activities.firstOrNull { it.label == label }

    fun clearActivity() {
        activities = emptyList()
    }

    fun signOut() {
        signedInAs = null
    }

    fun run(label: String, action: suspend () -> String) {
        if (running != null) return
        scope.launch {
            running = label
            val entry = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "$label ✗ [${e::class.simpleName}] ${e.message}"
            } finally {
                running = null
            }
            val outcome = when {
                "✗" in entry.lineSequence().first() -> Outcome.Failed
                "✓" in entry -> Outcome.Ok
                else -> Outcome.Info
            }
            activities = listOf(Activity(++seq, label, outcome, entry)) + activities
            println("PASSKI_WEB_DEMO $entry")
        }
    }

    suspend fun loadCapabilities() {
        capabilities = passkeys.capabilities()
    }

    suspend fun register(): String =
        when (val result = passkeys.register(PasskeyExtensions.withPrf(rp.registrationOptions(username)))) {
            is PasskeyResult.Failure -> failure("Create a passkey", result.error)
            is PasskeyResult.Success -> {
                val report = rp.readRegistration(result.value.json)
                val mark = if (report.ok) "✓" else "✗"
                "Create a passkey $mark for ${username.trim().ifEmpty { "demo user" }}, prfEnabled=${result.value.prfEnabled}\n" +
                    "$report\n\nresponse JSON (POST this to your server):\n${result.value.json}"
            }
        }

    /** Creates a passkey for the same user again: `excludeCredentials` now names the first one. */
    suspend fun registerAgain(): String {
        if (rp.credentials.none { it.username == username.trim().ifEmpty { "demo user" } }) {
            return "Create it again: create a passkey for this username first"
        }
        return when (val result = passkeys.register(rp.registrationOptions(username))) {
            is PasskeyResult.Failure if result.error == PasskeyError.AlreadyRegistered ->
                "Create it again ✓ AlreadyRegistered: the browser found the passkey from excludeCredentials"
            is PasskeyResult.Failure -> failure("Create it again", result.error)
            is PasskeyResult.Success -> {
                rp.readRegistration(result.value.json)
                "Create it again: the authenticator made a second passkey (it ignored excludeCredentials)"
            }
        }
    }

    suspend fun signIn(mode: SignInMode = SignInMode.Modal, label: String = "Sign in"): String =
        when (val result = passkeys.signIn(rp.authenticationOptions(), mode)) {
            is PasskeyResult.Failure -> failure(label, result.error)
            is PasskeyResult.Success -> {
                val report = rp.readAssertion(result.value.json)
                val who = result.value.userHandle?.let(rp::decodeName) ?: "unknown user"
                if (report.ok) signedInAs = who
                "$label ${if (report.ok) "✓" else "✗"} as $who\n$report\n\n" +
                    "response JSON (POST this to your server):\n${result.value.json}"
            }
        }

    suspend fun prf(): String {
        val options = PasskeyExtensions.withPrf(rp.authenticationOptions(), PRF_SALT)
        val assertion = when (val result = passkeys.signIn(options)) {
            is PasskeyResult.Failure -> return failure("PRF", result.error)
            is PasskeyResult.Success -> result.value
        }
        val output = assertion.prf?.first ?: return "PRF ✗ no PRF output: this browser or authenticator does not support PRF"
        val hex = output.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        val stability = when (val previous = prfByCredential.put(assertion.credentialId, hex)) {
            null -> "first run for this passkey; run again to compare"
            hex -> "same as the previous run ✓ (stable)"
            else -> "DIFFERS from the previous run ($previous) ✗"
        }
        return "PRF ✓ ${output.size} bytes, first 8: $hex\n$stability"
    }

    suspend fun forgetLast(): String {
        val credential = rp.credentials.lastOrNull() ?: return "Forget last passkey: create a passkey first"
        return when (val result = passkeys.signals.unknownCredential(rp.rpId, credential.id)) {
            is PasskeyResult.Failure -> failure("Forget last passkey", result.error)
            is PasskeyResult.Success ->
                "Forget last passkey ✓ told the browser ${credential.id} is unknown; its password manager may remove it"
        }
    }

    suspend fun rename(): String {
        val credential = rp.credentials.lastOrNull() ?: return "Rename user: create a passkey first"
        val name = username.trim().ifEmpty { return "Rename user: type the new name in the username field" }
        return when (val result = passkeys.signals.currentUserDetails(rp.rpId, credential.userHandle, name, name)) {
            is PasskeyResult.Failure -> failure("Rename user", result.error)
            is PasskeyResult.Success -> "Rename user ✓ asked the browser to show ${credential.username}'s passkey as $name"
        }
    }

    private fun failure(label: String, error: PasskeyError): String =
        "$label ✗ [${error::class.simpleName}${error.checkId?.let { " $it" }.orEmpty()}] ${error.message}" +
            error.fixHint?.let { "\nfix: $it" }.orEmpty()
}
