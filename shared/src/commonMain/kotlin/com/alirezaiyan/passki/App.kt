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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun App(passkeys: Passkeys = remember { Passkeys() }) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var log by remember { mutableStateOf("Ready. RP ID: ${DemoRelyingParty.RP_ID}") }

        fun run(label: String, action: suspend () -> PasskeyResult<*>) {
            scope.launch {
                log = "$label…"
                log = when (val result = action()) {
                    is PasskeyResult.Success -> "$label ✓\n${result.value}"
                    is PasskeyResult.Failure -> failureLog(label, result.error)
                }
                println("PASSKI_DEMO $log")
            }
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

/** One log entry per failure: the message, then the fix hint on its own line when there is one. */
internal fun failureLog(label: String, error: PasskeyError): String =
    "$label ✗ ${error.message}" + error.fixHint?.let { "\nfix: $it" }.orEmpty()
