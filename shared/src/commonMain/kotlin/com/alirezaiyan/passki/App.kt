package com.alirezaiyan.passki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App(passkeys: Passkeys = remember { Passkeys() }, autorun: Boolean = false) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var log by remember { mutableStateOf("Ready. RP ID: ${DemoRelyingParty.RP_ID}") }

        suspend fun execute(label: String, action: suspend () -> PasskeyResult<*>) {
            log = "$label…"
            val result = action()
            log = when (result) {
                is PasskeyResult.Success -> "$label ✓\n${result.value}"
                is PasskeyResult.Failure -> failureLog(label, result.error)
            }
            println("PASSKI_DEMO $log")
            // Full response, for recording passki-test contract fixtures. Contains no secrets.
            ((result as? PasskeyResult.Success)?.value as? PasskeyRegistration)?.let { println("PASSKI_FIXTURE ${it.json}") }
        }

        fun run(label: String, action: suspend () -> PasskeyResult<*>) {
            scope.launch { execute(label, action) }
        }

        // Unattended smoke run (simulator/CI): steps that never show UI first, then the ones that may
        // open a system sheet (these wait for a tap).
        LaunchedEffect(autorun) {
            if (!autorun) return@LaunchedEffect
            delay(1_500)
            execute("Capabilities") { PasskeyResult.Success(passkeys.capabilities()) }
            println("PASSKI_DEMO ${PasskeyDoctor.run(DemoRelyingParty.RP_ID)}")
            // No passkey matches this id, so the device-only request has nothing to offer.
            execute("Sign in (device only, unknown credential)") {
                passkeys.signIn(DemoRelyingParty.requestOptions(listOf("AAAAAAAAAAAAAAAAAAAAAA")), SignInMode.ImmediateOnly)
            }
            execute("Signal unknown") { passkeys.signals.unknownCredential(DemoRelyingParty.RP_ID, "AA") }
            // Conditional create never shows UI: either succeeds silently or is declined.
            execute("Upgrade") { passkeys.upgrade(DemoRelyingParty.creationOptions()) }
            println("PASSKI_DEMO Interactive steps follow: a system passkey sheet may now be visible")
            execute("Sign in (device only)") {
                passkeys.signIn(DemoRelyingParty.requestOptions(), SignInMode.ImmediateOnly)
            }
            execute("Register") { passkeys.register(DemoRelyingParty.creationOptions()) }
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
            // Autofill spike: while the username field is focused, passkeys are offered in its keyboard.
            var username by remember { mutableStateOf("") }
            var usernameFocused by remember { mutableStateOf(false) }
            LaunchedEffect(usernameFocused) {
                if (!usernameFocused) return@LaunchedEffect
                println("PASSKI_DEMO Autofill: waiting for a passkey suggestion")
                execute("Sign in (autofill)") {
                    passkeys.signIn(DemoRelyingParty.requestOptions(), SignInMode.Autofill)
                }
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (passkey autofill)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username }
                    .onFocusChanged { usernameFocused = it.isFocused },
            )
            Button(onClick = { run("Capabilities") { PasskeyResult.Success(passkeys.capabilities()) } }) {
                Text("Capabilities")
            }
            Button(onClick = {
                run("Register") {
                    passkeys.register(DemoRelyingParty.creationOptions())
                        .onSuccess { DemoRelyingParty.credentialIds += it.credentialId }
                }
            }) { Text("Register passkey") }
            Button(onClick = { run("Sign in") { passkeys.signIn(DemoRelyingParty.requestOptions()) } }) {
                Text("Sign in")
            }
            Button(onClick = {
                run("Sign in (device only)") {
                    passkeys.signIn(DemoRelyingParty.requestOptions(), SignInMode.ImmediateOnly)
                }
            }) { Text("Sign in (device only)") }
            Button(onClick = { run("Sign in or password") { passkeys.signInOrPassword(DemoRelyingParty.requestOptions()) } }) {
                Text("Sign in (passkey or password)")
            }
            Button(onClick = { run("Upgrade") { passkeys.upgrade(DemoRelyingParty.creationOptions()) } }) {
                Text("Upgrade (conditional create)")
            }
            Button(onClick = {
                run("Signal unknown") {
                    val id = DemoRelyingParty.credentialIds.lastOrNull() ?: "AA"
                    passkeys.signals.unknownCredential(DemoRelyingParty.RP_ID, id)
                }
            }) { Text("Signal: forget last passkey") }
            Button(onClick = {
                scope.launch {
                    log = "Doctor…"
                    log = PasskeyDoctor.run(DemoRelyingParty.RP_ID).toString()
                    println("PASSKI_DEMO $log")
                }
            }) { Text("Run doctor") }
            SelectionContainer {
                Text(log, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** One log entry per failure: the error type and message, then the fix hint on its own line when there is one. */
internal fun failureLog(label: String, error: PasskeyError): String =
    "$label ✗ [${error::class.simpleName}] ${error.message}" + error.fixHint?.let { "\nfix: $it" }.orEmpty()
