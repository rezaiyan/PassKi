package com.alirezaiyan.passki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alirezaiyan.passki.demo.DemoServerException
import com.alirezaiyan.passki.demo.DemoVerdict
import com.alirezaiyan.passki.demo.PasskiDemoServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fixed PRF salt: the same passkey must return the same output for it on every run. */
private val PRF_SALT = "passki-demo-prf-salt-v1".encodeToByteArray()

/** How one step ended, read from its log entry (✗ wins over ✓; neither means a plain notice). */
enum class Outcome { Running, Ok, Failed, Info }

/** One finished (or running) step. [text] is exactly what was logged after `PASSKI_DEMO`. */
data class Activity(val seq: Int, val label: String, val outcome: Outcome, val text: String) {
    val title: String get() = text.lineSequence().first()
    val detail: String get() = text.lineSequence().drop(1).filterNot { it.startsWith("fix: ") }.joinToString("\n")
    val fixHint: String? get() = text.lineSequence().firstOrNull { it.startsWith("fix: ") }?.removePrefix("fix: ")
}

/** The server-verified sign-in shown on the account screen. */
data class Session(val verdict: DemoVerdict, val via: String)

/** One row of the setup checklist: a doctor check or a server check (SRV-01/02). */
data class CheckRow(val id: String?, val name: String, val status: DoctorStatus, val message: String, val fixHint: String?)

/**
 * State and flows of the sample, shared by every screen. Each flow runs as a "step": its log entry is printed as a
 * `PASSKI_DEMO` line (device tests and the README rely on these) and recorded as an [Activity].
 */
class DemoController(
    val passkeys: Passkeys,
    val rp: PasskiDemoServer,
    private val appOrigin: String?,
    val platform: String,
    private val scope: CoroutineScope,
) {
    var activities by mutableStateOf(listOf<Activity>())
        private set
    var running by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf<Session?>(null)
    var username by mutableStateOf("")
    var shareFixture by mutableStateOf(false)
    var capabilities by mutableStateOf<PasskeyCapabilities?>(null)
        private set
    var checks by mutableStateOf<List<CheckRow>?>(null)
        private set

    private val prfByCredential = mutableMapOf<String, String>()
    private var seq = 0

    /** The most recent activity for [label], for status next to the button that started it. */
    fun latest(label: String): Activity? = activities.firstOrNull { it.label == label }

    fun clearActivity() {
        activities = emptyList()
    }

    private fun record(label: String, text: String, outcome: Outcome) {
        activities = listOf(Activity(++seq, label, outcome, text)) + activities.filterNot { it.label == label && it.outcome == Outcome.Running }
    }

    fun show(label: String, entry: String) {
        record(label, entry, outcomeOf(entry))
        println("PASSKI_DEMO $entry")
    }

    /** Runs one step; device failures and server errors both end up in the log, never as a crash. */
    suspend fun step(label: String, action: suspend () -> String) {
        running = label
        record(label, "$label…", Outcome.Running)
        val entry = try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DemoServerException) {
            "$label ✗ [server] ${e.message}"
        } catch (e: Throwable) {
            "$label ✗ [${e::class.simpleName}] ${e.message}"
        } finally {
            running = null
        }
        show(label, entry)
    }

    fun run(label: String, action: suspend () -> String) {
        scope.launch { step(label, action) }
    }

    private suspend fun verifyRegistration(label: String, result: PasskeyResult<PasskeyRegistration>): String = when (result) {
        is PasskeyResult.Failure -> failureLog(label, result.error)
        is PasskeyResult.Success -> {
            val registration = result.value
            // Full response, for recording passki-test contract fixtures. Contains no secrets.
            println("PASSKI_FIXTURE ${registration.json}")
            val share = if (shareFixture) DemoSupport.fixturePlatform(platform) else null
            val verdict = try {
                rp.verifyRegistration(registration.json, share)
            } catch (e: DemoServerException) {
                return "$label ✓ on device, ✗ by server: ${e.message}"
            }
            if (verdict.verified) session = Session(verdict, label)
            "$label ✓ prfEnabled=${registration.prfEnabled}\n$verdict" +
                share?.let { "\nshared with PassKi tests as a $it fixture" }.orEmpty()
        }
    }

    private suspend fun verifyAssertion(label: String, assertion: PasskeyAssertion): String {
        println("PASSKI_ASSERTION ${assertion.json}")
        val verdict = try {
            rp.verifyAuthentication(assertion.json)
        } catch (e: DemoServerException) {
            return "$label ✓ on device, ✗ by server: ${e.message}"
        }
        if (verdict.verified) session = Session(verdict, label)
        return "$label ✓\n$verdict"
    }

    private suspend fun verifyAssertion(label: String, result: PasskeyResult<PasskeyAssertion>): String = when (result) {
        is PasskeyResult.Failure -> failureLog(label, result.error)
        is PasskeyResult.Success -> verifyAssertion(label, result.value)
    }

    fun onSignedIn(label: String, assertion: PasskeyAssertion) = run(label) { verifyAssertion(label, assertion) }

    fun onError(label: String, error: PasskeyError) = show(label, failureLog(label, error))

    suspend fun register(): String {
        val options = PasskeyExtensions.withPrf(rp.registrationOptions(username))
        return verifyRegistration("Register", passkeys.register(options))
    }

    suspend fun deviceOnlySignIn(): String =
        verifyAssertion("Sign in (device only)", passkeys.signIn(rp.authenticationOptions(rp.account?.userHandle), SignInMode.ImmediateOnly))

    suspend fun signInOrPassword(): String = when (val r = passkeys.signInOrPassword(rp.authenticationOptions())) {
        is PasskeyResult.Failure -> failureLog("Sign in or password", r.error)
        is PasskeyResult.Success -> when (val c = r.value) {
            is SignInCredential.Passkey -> verifyAssertion("Sign in or password (passkey)", c.assertion)
            is SignInCredential.Password -> "Sign in or password ✓ password for ${c.id} (the demo RP has no passwords)"
        }
    }

    suspend fun upgrade(): String = verifyRegistration("Upgrade", passkeys.upgrade(rp.registrationOptions(username)))

    suspend fun prf(): String {
        val options = PasskeyExtensions.withPrf(rp.authenticationOptions(rp.account?.userHandle), PRF_SALT)
        val assertion = when (val result = passkeys.signIn(options)) {
            is PasskeyResult.Failure -> return failureLog("PRF", result.error)
            is PasskeyResult.Success -> result.value
        }
        val server = verifyAssertion("PRF sign-in", assertion)
        val output = assertion.prf?.first ?: return "PRF ✗ no PRF output (provider or OS does not support PRF)\n$server"
        val hex = output.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        val previous = prfByCredential.put(assertion.credentialId, hex)
        val stability = when (previous) {
            null -> "first run for this passkey; run again to compare"
            hex -> "same as the previous run ✓ (stable)"
            else -> "DIFFERS from the previous run ($previous) ✗"
        }
        return "PRF ✓ ${output.size} bytes, first 8: $hex\n$stability\n$server"
    }

    suspend fun signalUnknown(): String {
        val id = rp.account?.credentialIds?.lastOrNull() ?: return "Signal unknown: register or sign in first"
        return when (val r = passkeys.signals.unknownCredential(PasskiDemoServer.RP_ID, id)) {
            is PasskeyResult.Success -> "Signal unknown ✓ $id"
            is PasskeyResult.Failure -> failureLog("Signal unknown", r.error)
        }
    }

    suspend fun signalUserDetails(): String {
        val account = rp.account ?: return "Signal user details: register or sign in first"
        val name = username.ifBlank { "PassKi demo user" }
        return when (val r = passkeys.signals.currentUserDetails(PasskiDemoServer.RP_ID, account.userHandle, name, name)) {
            is PasskeyResult.Success -> "Signal user details ✓ ${account.userHandle} → $name"
            is PasskeyResult.Failure -> failureLog("Signal user details", r.error)
        }
    }

    suspend fun loadCapabilities(): String {
        val caps = passkeys.capabilities()
        capabilities = caps
        return "Capabilities ✓\n$caps"
    }

    suspend fun setupCheck(): String {
        val doctor = PasskeyDoctor.run(PasskiDemoServer.RP_ID)
        val expectedOrigin = appOrigin ?: "https://${PasskiDemoServer.RP_ID}"
        val rows = doctor.checks.map { CheckRow(it.id, it.name, it.status, it.message, it.fixHint) }.toMutableList()
        val server = try {
            val config = rp.serverConfig()
            val rpOk = config.rpId == PasskiDemoServer.RP_ID
            val originOk = expectedOrigin in config.origins
            rows += CheckRow(
                "SRV-01", "Server RP ID", if (rpOk) DoctorStatus.Pass else DoctorStatus.Fail,
                if (rpOk) "Server RP ID is ${config.rpId}" else "Server RP ID is ${config.rpId}, the app uses ${PasskiDemoServer.RP_ID}",
                if (rpOk) null else "Use the same RP ID on the server and in the app.",
            )
            rows += CheckRow(
                "SRV-02", "Server allows this app's origin", if (originOk) DoctorStatus.Pass else DoctorStatus.Fail,
                if (originOk) "Origin $expectedOrigin is allowed" else "Origin $expectedOrigin is not allowed",
                if (originOk) null else "Allowed: ${config.origins.joinToString()}",
            )
            val srv01 = if (rpOk) {
                "[PASS] SRV-01 server RP ID is ${config.rpId}"
            } else {
                "[FAIL] SRV-01 server RP ID is ${config.rpId}, app uses ${PasskiDemoServer.RP_ID}"
            }
            val srv02 = if (originOk) {
                "[PASS] SRV-02 server allows this app's origin $expectedOrigin"
            } else {
                "[FAIL] SRV-02 server does not allow $expectedOrigin\n       allowed: ${config.origins.joinToString()}"
            }
            "$srv01\n$srv02"
        } catch (e: DemoServerException) {
            rows += CheckRow("SRV-01", "Server RP ID", DoctorStatus.Fail, e.message ?: "Demo server unreachable", null)
            rows += CheckRow("SRV-02", "Server allows this app's origin", DoctorStatus.Skipped, "Blocked by SRV-01", null)
            "[FAIL] SRV-01 ${e.message}\n[SKIP] SRV-02 blocked by SRV-01"
        }
        checks = rows
        val ids = doctor.checks.mapNotNull { it.id } + listOf("SRV-01", "SRV-02")
        return "Setup check ($platform)\n$doctor\nServer (${PasskiDemoServer.URL}/api/demo/server-config):\n$server\ncatalog IDs: ${ids.joinToString()}"
    }

    /**
     * Unattended smoke run (simulator/CI): steps that never show UI first, then the ones that may open a system
     * sheet (these wait for a tap).
     */
    suspend fun autorun() {
        step("Capabilities") { loadCapabilities() }
        step("Setup check") { setupCheck() }
        step("Server options") {
            val creation = rp.registrationOptions("autorun")
            val request = rp.authenticationOptions()
            "Server options ✓ registration ${creation.length} chars, sign-in ${request.length} chars"
        }
        // No passkey matches this id, so the device-only request has nothing to offer.
        step("Sign in (device only, unknown credential)") {
            val options = DemoSupport.withAllowCredentials(rp.authenticationOptions(), listOf("AAAAAAAAAAAAAAAAAAAAAA"))
            verifyAssertion("Sign in (device only, unknown credential)", passkeys.signIn(options, SignInMode.ImmediateOnly))
        }
        step("Signal unknown") {
            when (val r = passkeys.signals.unknownCredential(PasskiDemoServer.RP_ID, "AA")) {
                is PasskeyResult.Success -> "Signal unknown ✓"
                is PasskeyResult.Failure -> failureLog("Signal unknown", r.error)
            }
        }
        // Conditional create never shows UI: either succeeds silently or is declined.
        step("Upgrade") { verifyRegistration("Upgrade", passkeys.upgrade(rp.registrationOptions("autorun-upgrade"))) }
        println("PASSKI_DEMO Interactive steps follow: a system passkey sheet may now be visible")
        step("Sign in (device only)") { deviceOnlySignIn() }
        step("Register") { register() }
        step("PRF") { prf() }
        step("PRF") { prf() }
        println("PASSKI_DEMO Autorun done")
    }

    companion object {
        internal fun outcomeOf(entry: String): Outcome = when {
            "✗" in entry -> Outcome.Failed
            "✓" in entry -> Outcome.Ok
            else -> Outcome.Info
        }
    }
}

/** One log entry per failure: the error type and message, then the fix hint on its own line when there is one. */
internal fun failureLog(label: String, error: PasskeyError): String =
    "$label ✗ [${error::class.simpleName}] ${error.message}" + error.fixHint?.let { "\nfix: $it" }.orEmpty()
