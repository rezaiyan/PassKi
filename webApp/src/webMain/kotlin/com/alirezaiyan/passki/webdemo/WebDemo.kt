package com.alirezaiyan.passki.webdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.ui.ActionRow
import com.alirezaiyan.passki.ui.ActivityItem
import com.alirezaiyan.passki.ui.FixHint
import com.alirezaiyan.passki.ui.InfoRow
import com.alirezaiyan.passki.ui.LocalStatusColors
import com.alirezaiyan.passki.ui.Panel
import com.alirezaiyan.passki.ui.PassKiTheme
import com.alirezaiyan.passki.ui.Rows
import com.alirezaiyan.passki.ui.ScreenColumn
import com.alirezaiyan.passki.ui.SectionLabel

/** One flow on the page. [label] is the activity label its run records under. */
private class Flow(val label: String, val title: String, val description: String, val action: suspend () -> String)

/**
 * The web sample: PassKi's browser implementation against a [LocalRelyingParty] for the page's own domain, so it runs
 * on `localhost` or wherever you host it, with no server to set up.
 */
@Composable
fun WebDemo(rp: LocalRelyingParty, passkeys: Passkeys = remember { Passkeys() }) {
    PassKiTheme {
        val scope = rememberCoroutineScope()
        val controller = remember { WebDemoController(passkeys, rp, scope) }
        LaunchedEffect(Unit) { controller.loadCapabilities() }
        val caps = controller.capabilities

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenColumn(
                "Passkeys in the browser",
                "PassKi on Kotlin/JS and Wasm: the same API as on Android and iOS, here through WebAuthn.",
            ) {
                if (caps != null && !caps.isSupported) {
                    FixHint(
                        "This browser has no WebAuthn here. Use a current browser, and open the page over HTTPS or from localhost.",
                        Modifier.padding(top = 16.dp),
                    )
                }

                SectionLabel("This page")
                Panel {
                    InfoRow("RP ID", rp.rpId, mono = true)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow("Origin", rp.origin, mono = true)
                    if (caps != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            "Supports",
                            listOf(
                                "passkeys" to caps.isSupported,
                                "autofill" to caps.autofill,
                                "device only" to caps.immediateSignIn,
                                "upgrade" to caps.conditionalCreate,
                                "signals" to caps.signals,
                            ).joinToString("  ") { (name, on) -> (if (on) "✓ " else "✗ ") + name },
                        )
                    }
                }

                SectionLabel("Account")
                Panel {
                    OutlinedTextField(
                        value = controller.username,
                        onValueChange = { controller.username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val who = controller.signedInAs
                        Text(
                            if (who == null) "Not signed in" else "Signed in as $who",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (who == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalStatusColors.current.pass,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        )
                        if (who != null) TextButton(onClick = controller::signOut) { Text("Sign out") }
                    }
                }

                FlowGroup(
                    "Create", controller,
                    Flow("Create a passkey", "Create a passkey", "For the username above, with the PRF extension on") { controller.register() },
                    Flow("Create it again", "Create it again", "Same user: excludeCredentials makes the browser refuse") { controller.registerAgain() },
                )
                FlowGroup(
                    "Sign in", controller,
                    Flow("Sign in", "Sign in with a passkey", "Any passkey for this site; no username needed") { controller.signIn() },
                    Flow(
                        "Sign in (device only)", "Device only",
                        if (caps?.immediateSignIn == true) {
                            "Only passkeys already on this device; fails fast with NoCredentials"
                        } else {
                            "Needs mediation \"immediate\", which this browser lacks: expect NotSupported"
                        },
                    ) { controller.signIn(SignInMode.ImmediateOnly, "Sign in (device only)") },
                )
                FlowGroup(
                    "Extensions and signals", controller,
                    Flow("PRF", "PRF: derive a key", "Run twice: the first 8 bytes must match for the same passkey") { controller.prf() },
                    Flow("Forget last passkey", "Forget last passkey", "signals.unknownCredential for the last passkey created here") { controller.forgetLast() },
                    Flow("Rename user", "Rename user", "signals.currentUserDetails with the username above") { controller.rename() },
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Activity", Modifier.weight(1f))
                    if (controller.activities.isNotEmpty()) {
                        TextButton(onClick = controller::clearActivity) { Text("Clear") }
                    }
                }
                Panel {
                    if (controller.activities.isEmpty()) {
                        Text(
                            "Run a flow and its result shows up here, newest first. Tap an entry for the response JSON.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        Rows(controller.activities.take(30)) { ActivityItem(it) }
                    }
                }

                Text(
                    "The options come from a stand-in relying party inside this page, for ${rp.rpId}. It reads the " +
                        "challenge, origin and flags like a server would, but doesn't check signatures: in your app, your " +
                        "server creates the options and verifies every response. Autofill isn't shown because Compose for " +
                        "Web draws on a canvas, and passkey autofill needs an HTML input.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun FlowGroup(title: String, controller: WebDemoController, vararg flows: Flow) {
    SectionLabel(title)
    Panel {
        Rows(flows.toList()) { flow ->
            ActionRow(flow.title, flow.description, controller.latest(flow.label), busy = controller.running == flow.label) {
                controller.run(flow.label, flow.action)
            }
        }
    }
}
