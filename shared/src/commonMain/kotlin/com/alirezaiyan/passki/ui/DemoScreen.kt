package com.alirezaiyan.passki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alirezaiyan.passki.DemoController
import com.alirezaiyan.passki.Outcome
import com.alirezaiyan.passki.Session
import com.alirezaiyan.passki.compose.PasskeySignInButton
import com.alirezaiyan.passki.compose.PasskeySignInState
import com.alirezaiyan.passki.compose.PasskeyUsernameField
import com.alirezaiyan.passki.demo.PasskiDemoServer

private val DEMO_LABELS = setOf("Sign in", "Sign in (autofill)", "Register")

@Composable
fun DemoScreen(controller: DemoController, signIn: PasskeySignInState) {
    val session = controller.session
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NightHeader {
            if (session == null) {
                Text("Welcome back", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "Sign in with a passkey. Nothing to type, nothing to leak.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Night.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                SignedInHeading(controller, session)
            }
        }
        Box(Modifier.fillMaxWidth().offset(y = (-28).dp), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                if (session == null) SignInCard(controller, signIn) else AccountContent(controller, session)
                ServerFooter()
            }
        }
    }
}

/** The always-dark brand band at the top of the demo tab. */
@Composable
private fun NightHeader(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Night.bg, Color(0xFF121A45))))
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 60.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(32)
                Text(
                    "PassKi Demo",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = displayFamily(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.height(36.dp))
            content()
        }
    }
}

/** Violet tile with the key, as on the website. */
@Composable
fun BrandMark(size: Int) {
    IconTile(PassKiIcons.Key, Color.White, Color(0xFF6E56FF), size)
}

@Composable
private fun SignInCard(controller: DemoController, signIn: PasskeySignInState) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(Modifier.padding(24.dp)) {
            // While the username field is focused, passkeys are offered in its keyboard (passki-compose).
            PasskeyUsernameField(
                value = controller.username,
                onValueChange = { controller.username = it },
                requestOptions = {
                    println("PASSKI_DEMO Autofill: waiting for a passkey suggestion")
                    controller.rp.authenticationOptions()
                },
                onSignedIn = { controller.onSignedIn("Sign in (autofill)", it) },
                onError = { controller.onError("Sign in (autofill)", it) },
                passkeys = controller.passkeys,
                label = "Username",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Tap the field: saved passkeys show up above the keyboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            PasskeySignInButton(
                state = signIn,
                onSignedIn = { controller.onSignedIn("Sign in", it) },
                onError = { controller.onError("Sign in", it) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(PassKiIcons.Key, null, modifier = Modifier.size(20.dp))
                Text("Sign in with a passkey", modifier = Modifier.padding(start = 10.dp))
            }
            Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "New here?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            val registering = controller.running == "Register"
            OutlinedButton(
                onClick = { controller.run("Register") { controller.register() } },
                enabled = !registering,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (registering) StatusMark(Tone.Busy, 20) else Text("Create an account")
            }
            Text(
                "Creates a demo user and a passkey for it. The username above becomes its name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            controller.activities.firstOrNull { it.label in DEMO_LABELS && it.outcome != Outcome.Running }
                ?.takeIf { it.outcome == Outcome.Failed }
                ?.let { failure ->
                    Column(
                        Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(LocalStatusColors.current.failBg)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusMark(Tone.Fail, 22)
                            Text(
                                failure.label + " didn't finish",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 10.dp).weight(1f),
                            )
                            failure.checkId?.let { IdChip(it) }
                        }
                        Text(
                            failure.title.substringAfter("✗ ").ifBlank { failure.title },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        failure.fixHint?.let { FixHint(it, Modifier.padding(top = 10.dp)) }
                    }
                }
        }
    }
}

@Composable
private fun SignedInHeading(controller: DemoController, session: Session) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val initial = controller.username.firstOrNull()?.uppercaseChar() ?: 'P'
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF2A2F66)), contentAlignment = Alignment.Center) {
            Text(initial.toString(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text("You're signed in", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                controller.username.ifBlank { "Demo user" } + " · " + session.via,
                style = MaterialTheme.typography.bodyMedium,
                color = Night.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountContent(controller: DemoController, session: Session) {
    val v = session.verdict
    val status = LocalStatusColors.current
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusMark(if (v.verified) Tone.Pass else Tone.Fail, 30)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        if (v.verified) "Verified by the server" else "Not verified by the server",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "SimpleWebAuthn on ${PasskiDemoServer.RP_ID} checked the signature and challenge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            fun yesNo(b: Boolean) = if (b) "Yes" else "No"
            val rows = buildList {
                add(Triple("Credential", v.credentialId.middleEllipsis(28), true))
                add(Triple("Sign count", v.signCount.toString(), false))
                add(Triple("User verified", yesNo(v.userVerified), false))
                add(Triple("Synced", if (v.backedUp) "Yes, backed up" else if (v.backupEligible) "Eligible, not yet" else "No, device-bound", false))
                add(Triple("Origin", v.origin, true))
                v.aaguid?.let { add(Triple("AAGUID", it, true)) }
            }
            Rows(rows) { (label, value, mono) ->
                InfoRow(label, value, mono, valueColor = if (label == "User verified" && value == "Yes") status.pass else null)
            }
        }
    }
    SectionLabel("Try next")
    Panel {
        Rows(
            listOf(
                Triple("PRF", "Derive a key", "Same passkey, same salt, same key: run it twice to compare") to suspend { controller.prf() },
                Triple("Signal user details", "Rename this user", "Updates the name in the password manager (signals)") to suspend { controller.signalUserDetails() },
                Triple("Signal unknown", "Forget this passkey", "Tells the password manager the server deleted it") to suspend { controller.signalUnknown() },
            ),
        ) { (meta, action) ->
            val (label, title, description) = meta
            ActionRow(title, description, controller.latest(label), controller.running == label) { controller.run(label, action) }
        }
    }
    TextButton(onClick = { controller.signOut() }, modifier = Modifier.padding(top = 8.dp)) {
        Icon(PassKiIcons.Logout, null, modifier = Modifier.size(18.dp))
        Text("Sign out", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ServerFooter() {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PassKiIcons.Server, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Demo server ${PasskiDemoServer.RP_ID}. Users expire after 24 h.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.middleEllipsis(max: Int): String =
    if (length <= max) this else take(max / 2 - 1) + "…" + takeLast(max / 2 - 1)
