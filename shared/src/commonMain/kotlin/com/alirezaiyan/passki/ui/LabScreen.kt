package com.alirezaiyan.passki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alirezaiyan.passki.DemoController
import com.alirezaiyan.passki.compose.PasskeyUsernameField

/** One flow in the lab. [label] is the activity/log label its step records under. */
private class LabFlow(val label: String, val title: String, val description: String, val action: suspend () -> String)

@Composable
fun LabScreen(controller: DemoController) {
    var showAutofillDialog by remember { mutableStateOf(false) }

    ScreenColumn("Lab", "Every PassKi flow against the demo server. Each result is checked server-side and logged.") {

        LabGroup(
            "Sign in", controller,
            LabFlow("Sign in (device only)", "Device only", "Only passkeys already on this device; fails fast with NoCredentials") { controller.deviceOnlySignIn() },
            LabFlow("Sign in or password", "Passkey or password", "One system sheet with passkeys and saved passwords") { controller.signInOrPassword() },
        ) {
            ActionRow(
                "Autofill in a dialog",
                "The username field inside a Dialog window still gets the suggestion",
                controller.latest("Sign in (autofill in dialog)"),
                busy = false,
            ) { showAutofillDialog = true }
        }
        LabGroup(
            "Create", controller,
            LabFlow("Register", "Register with PRF", "A fresh demo user and passkey, with the PRF extension on") { controller.register() },
            LabFlow("Upgrade", "Upgrade", "Conditional create: adds a passkey without UI, if the platform agrees") { controller.upgrade() },
        )
        LabGroup(
            "Extensions and signals", controller,
            LabFlow("PRF", "PRF: derive a key", "Run twice: the first 8 bytes must match for the same passkey") { controller.prf() },
            LabFlow("Signal unknown", "Forget last passkey", "signals.unknownCredential for the last credential") { controller.signalUnknown() },
            LabFlow("Signal user details", "Rename user", "signals.currentUserDetails with the username from the Demo tab") { controller.signalUserDetails() },
        )

        SectionLabel("Testing")
        Panel {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Share responses with PassKi tests", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stores raw registration responses on the demo server for 7 days, as contract-test fixtures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = controller.shareFixture, onCheckedChange = { controller.shareFixture = it })
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            SectionLabel("Activity", Modifier.weight(1f))
            if (controller.activities.isNotEmpty()) {
                TextButton(onClick = controller::clearActivity) { Text("Clear") }
            }
        }
        Panel {
            if (controller.activities.isEmpty()) {
                Text(
                    "Run a flow and its result shows up here, newest first. Tap an entry for the full server verdict.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp),
                )
            } else {
                Rows(controller.activities.take(30)) { ActivityItem(it) }
            }
        }
    }

    if (showAutofillDialog) {
        // A Dialog is its own window (Android): the field must still get the passkey suggestion.
        Dialog(onDismissRequest = { showAutofillDialog = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                var dialogUsername by remember { mutableStateOf("") }
                Column(Modifier.padding(24.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Focus the field to get the passkey suggestion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    PasskeyUsernameField(
                        value = dialogUsername,
                        onValueChange = { dialogUsername = it },
                        requestOptions = {
                            println("PASSKI_DEMO Autofill (dialog): waiting for a passkey suggestion")
                            controller.rp.authenticationOptions()
                        },
                        onSignedIn = {
                            showAutofillDialog = false
                            controller.onSignedIn("Sign in (autofill in dialog)", it)
                        },
                        onError = { controller.onError("Sign in (autofill in dialog)", it) },
                        passkeys = controller.passkeys,
                        label = "Username (dialog)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabGroup(title: String, controller: DemoController, vararg flows: LabFlow, extra: (@Composable () -> Unit)? = null) {
    SectionLabel(title)
    Panel {
        Rows(flows.toList()) { flow ->
            ActionRow(flow.title, flow.description, controller.latest(flow.label), controller.running == flow.label) {
                controller.run(flow.label, flow.action)
            }
        }
        if (extra != null) {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            extra()
        }
    }
}
