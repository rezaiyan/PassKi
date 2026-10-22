package com.alirezaiyan.passki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alirezaiyan.passki.compose.PasskeySignInButton
import com.alirezaiyan.passki.compose.PasskeyUsernameField
import com.alirezaiyan.passki.compose.rememberPasskeySignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fixed PRF salt: the same passkey must return the same output for it on every run. */
private val PRF_SALT = "passki-demo-prf-salt-v1".encodeToByteArray()

/**
 * The PassKi sample against the live demo relying party ([DEMO_RP_URL]): the server issues every challenge and
 * verifies every response, and the log shows its verdict.
 *
 * @param appOrigin the WebAuthn origin this app produces (Android: `android:apk-key-hash:…`); null means the
 *   HTTPS origin of the RP ID (iOS, web).
 */
@Composable
fun App(
    passkeys: Passkeys = remember { Passkeys() },
    autorun: Boolean = false,
    appOrigin: String? = null,
    rp: DemoRelyingParty = remember { DemoRelyingParty() },
) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var log by remember { mutableStateOf("Ready. RP ID: ${DemoRelyingParty.RP_ID} (server $DEMO_RP_URL)") }
        var username by remember { mutableStateOf("") }
        var shareFixture by remember { mutableStateOf(false) }
        var showAutofillDialog by remember { mutableStateOf(false) }
        val prfByCredential = remember { mutableStateMapOf<String, String>() }
        val platform = remember { getPlatform().name }

        fun show(entry: String) {
            log = entry
            println("PASSKI_DEMO $entry")
        }

        /** Runs one step; device failures and server errors both end up in the log, never as a crash. */
        suspend fun step(label: String, action: suspend () -> String) {
            log = "$label…"
            show(
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DemoServerException) {
                    "$label ✗ [server] ${e.message}"
                } catch (e: Throwable) {
                    "$label ✗ [${e::class.simpleName}] ${e.message}"
                },
            )
        }

        fun run(label: String, action: suspend () -> String) {
            scope.launch { step(label, action) }
        }

        suspend fun verifyRegistration(label: String, result: PasskeyResult<PasskeyRegistration>): String = when (result) {
            is PasskeyResult.Failure -> failureLog(label, result.error)
            is PasskeyResult.Success -> {
                val registration = result.value
                // Full response, for recording passki-test contract fixtures. Contains no secrets.
                println("PASSKI_FIXTURE ${registration.json}")
                val share = if (shareFixture) DemoRelyingParty.fixturePlatform(platform) else null
                val verdict = try {
                    rp.verifyRegistration(registration.json, share)
                } catch (e: DemoServerException) {
                    return "$label ✓ on device, ✗ by server: ${e.message}"
                }
                "$label ✓ prfEnabled=${registration.prfEnabled}\n$verdict" +
                    share?.let { "\nshared with PassKi tests as a $it fixture" }.orEmpty()
            }
        }

        suspend fun verifyAssertion(label: String, assertion: PasskeyAssertion): String {
            println("PASSKI_ASSERTION ${assertion.json}")
            val verdict = try {
                rp.verifyAuthentication(assertion.json)
            } catch (e: DemoServerException) {
                return "$label ✓ on device, ✗ by server: ${e.message}"
            }
            return "$label ✓\n$verdict"
        }

        suspend fun verifyAssertion(label: String, result: PasskeyResult<PasskeyAssertion>): String = when (result) {
            is PasskeyResult.Failure -> failureLog(label, result.error)
            is PasskeyResult.Success -> verifyAssertion(label, result.value)
        }

        fun onSignedIn(label: String, assertion: PasskeyAssertion) = run(label) { verifyAssertion(label, assertion) }
        fun onError(label: String, error: PasskeyError) = show(failureLog(label, error))

        suspend fun register(): String {
            val options = PasskeyExtensions.withPrf(rp.registrationOptions(username))
            return verifyRegistration("Register", passkeys.register(options))
        }

        suspend fun deviceOnlySignIn(): String =
            verifyAssertion("Sign in (device only)", passkeys.signIn(rp.authenticationOptions(rp.account?.userHandle), SignInMode.ImmediateOnly))

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

        suspend fun setupCheck(): String {
            val doctor = PasskeyDoctor.run(DemoRelyingParty.RP_ID)
            val expectedOrigin = appOrigin ?: "https://${DemoRelyingParty.RP_ID}"
            val server = try {
                val config = rp.serverConfig()
                val srv01 = if (config.rpId == DemoRelyingParty.RP_ID) {
                    "[PASS] SRV-01 server RP ID is ${config.rpId}"
                } else {
                    "[FAIL] SRV-01 server RP ID is ${config.rpId}, app uses ${DemoRelyingParty.RP_ID}"
                }
                val srv02 = if (expectedOrigin in config.origins) {
                    "[PASS] SRV-02 server allows this app's origin $expectedOrigin"
                } else {
                    "[FAIL] SRV-02 server does not allow $expectedOrigin\n       allowed: ${config.origins.joinToString()}"
                }
                "$srv01\n$srv02"
            } catch (e: DemoServerException) {
                "[FAIL] SRV-01 ${e.message}\n[SKIP] SRV-02 blocked by SRV-01"
            }
            val ids = doctor.checks.mapNotNull { it.id } + listOf("SRV-01", "SRV-02")
            return "Setup check ($platform)\n$doctor\nServer ($DEMO_RP_URL/api/demo/server-config):\n$server\ncatalog IDs: ${ids.joinToString()}"
        }

        val signInState = rememberPasskeySignIn(requestOptions = { rp.authenticationOptions() }, passkeys = passkeys)

        // Unattended smoke run (simulator/CI): steps that never show UI first, then the ones that may
        // open a system sheet (these wait for a tap).
        LaunchedEffect(autorun) {
            if (!autorun) return@LaunchedEffect
            delay(1_500)
            step("Capabilities") { "Capabilities ✓\n${passkeys.capabilities()}" }
            step("Setup check") { setupCheck() }
            step("Server options") {
                val creation = rp.registrationOptions("autorun")
                val request = rp.authenticationOptions()
                "Server options ✓ registration ${creation.length} chars, sign-in ${request.length} chars"
            }
            // No passkey matches this id, so the device-only request has nothing to offer.
            step("Sign in (device only, unknown credential)") {
                val options = DemoRelyingParty.withAllowCredentials(rp.authenticationOptions(), listOf("AAAAAAAAAAAAAAAAAAAAAA"))
                verifyAssertion("Sign in (device only, unknown credential)", passkeys.signIn(options, SignInMode.ImmediateOnly))
            }
            step("Signal unknown") {
                when (val r = passkeys.signals.unknownCredential(DemoRelyingParty.RP_ID, "AA")) {
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

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // While the username field is focused, passkeys are offered in its keyboard (passki-compose).
            PasskeyUsernameField(
                value = username,
                onValueChange = { username = it },
                requestOptions = {
                    println("PASSKI_DEMO Autofill: waiting for a passkey suggestion")
                    rp.authenticationOptions()
                },
                onSignedIn = { onSignedIn("Sign in (autofill)", it) },
                onError = { onError("Sign in (autofill)", it) },
                passkeys = passkeys,
                label = "Username (passkey autofill)",
                modifier = Modifier.fillMaxWidth(),
            )
            // A Dialog is its own window (Android): the field must still get the passkey suggestion.
            Button(onClick = { showAutofillDialog = true }) { Text("Autofill in dialog") }
            if (showAutofillDialog) {
                Dialog(onDismissRequest = { showAutofillDialog = false }) {
                    Surface(shape = MaterialTheme.shapes.large) {
                        var dialogUsername by remember { mutableStateOf("") }
                        PasskeyUsernameField(
                            value = dialogUsername,
                            onValueChange = { dialogUsername = it },
                            requestOptions = {
                                println("PASSKI_DEMO Autofill (dialog): waiting for a passkey suggestion")
                                rp.authenticationOptions()
                            },
                            onSignedIn = {
                                showAutofillDialog = false
                                onSignedIn("Sign in (autofill in dialog)", it)
                            },
                            onError = { onError("Sign in (autofill in dialog)", it) },
                            passkeys = passkeys,
                            label = "Username (dialog)",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = shareFixture, onCheckedChange = { shareFixture = it })
                Text("Share response for PassKi tests (stored 7 days on the demo RP)")
            }
            Button(onClick = { run("Register") { register() } }) { Text("Register passkey") }
            PasskeySignInButton(
                state = signInState,
                onSignedIn = { onSignedIn("Sign in", it) },
                onError = { onError("Sign in", it) },
            ) { Text("Sign in") }
            Button(onClick = { run("Sign in (device only)") { deviceOnlySignIn() } }) { Text("Sign in (device only)") }
            Button(onClick = {
                run("Sign in or password") {
                    when (val r = passkeys.signInOrPassword(rp.authenticationOptions())) {
                        is PasskeyResult.Failure -> failureLog("Sign in or password", r.error)
                        is PasskeyResult.Success -> when (val c = r.value) {
                            is SignInCredential.Passkey -> verifyAssertion("Sign in or password (passkey)", c.assertion)
                            is SignInCredential.Password -> "Sign in or password ✓ password for ${c.id} (the demo RP has no passwords)"
                        }
                    }
                }
            }) { Text("Sign in (passkey or password)") }
            Button(onClick = { run("PRF") { prf() } }) { Text("PRF (derive key, first 8 bytes)") }
            Button(onClick = {
                run("Upgrade") { verifyRegistration("Upgrade", passkeys.upgrade(rp.registrationOptions(username))) }
            }) { Text("Upgrade (conditional create)") }
            Button(onClick = {
                run("Signal unknown") {
                    val id = rp.account?.credentialIds?.lastOrNull() ?: return@run "Signal unknown: register or sign in first"
                    when (val r = passkeys.signals.unknownCredential(DemoRelyingParty.RP_ID, id)) {
                        is PasskeyResult.Success -> "Signal unknown ✓ $id"
                        is PasskeyResult.Failure -> failureLog("Signal unknown", r.error)
                    }
                }
            }) { Text("Signal: forget last passkey") }
            Button(onClick = {
                run("Signal user details") {
                    val account = rp.account ?: return@run "Signal user details: register or sign in first"
                    val name = username.ifBlank { "PassKi demo user" }
                    when (val r = passkeys.signals.currentUserDetails(DemoRelyingParty.RP_ID, account.userHandle, name, name)) {
                        is PasskeyResult.Success -> "Signal user details ✓ ${account.userHandle} → $name"
                        is PasskeyResult.Failure -> failureLog("Signal user details", r.error)
                    }
                }
            }) { Text("Signal: rename user") }
            Button(onClick = { run("Capabilities") { "Capabilities ✓\n${passkeys.capabilities()}" } }) { Text("Capabilities") }
            Button(onClick = { run("Setup check") { setupCheck() } }) { Text("Setup check (doctor + server)") }
            SelectionContainer {
                Text(log, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** One log entry per failure: the error type and message, then the fix hint on its own line when there is one. */
internal fun failureLog(label: String, error: PasskeyError): String =
    "$label ✗ [${error::class.simpleName}] ${error.message}" + error.fixHint?.let { "\nfix: $it" }.orEmpty()
