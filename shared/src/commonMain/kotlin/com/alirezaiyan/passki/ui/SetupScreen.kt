package com.alirezaiyan.passki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alirezaiyan.passki.CheckRow
import com.alirezaiyan.passki.DemoController
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.demo.PasskiDemoServer

/**
 * Scrolling layout of the Lab and Setup tabs: a night band with the title (it sits under the status bar, like the
 * Demo tab's header), then centred content.
 */
@Composable
fun ScreenColumn(
    title: String,
    subtitle: String,
    band: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Night.bg, Color(0xFF121A45))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Night.muted, modifier = Modifier.padding(top = 6.dp))
                band?.invoke(this)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SetupScreen(controller: DemoController) {
    LaunchedEffect(Unit) {
        if (controller.capabilities == null) controller.step("Capabilities") { controller.loadCapabilities() }
    }
    val checks = controller.checks
    val running = controller.running == "Setup check"

    ScreenColumn(
        "Setup",
        "PasskeyDoctor and server checks for ${PasskiDemoServer.RP_ID}. Same IDs as ./gradlew passkiCheck and the web verifier.",
        band = {
            Spacer(Modifier.height(20.dp))
            SummaryCard(checks, running) { controller.run("Setup check") { controller.setupCheck() } }
        },
    ) {

        if (checks != null) {
            SectionLabel("Checks")
            Panel { Rows(checks) { CheckItem(it) } }
        }

        SectionLabel("This device")
        Panel {
            val caps = controller.capabilities
            if (caps == null) {
                Text("Reading capabilities…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(18.dp))
            } else {
                Rows(
                    listOf(
                        "Passkeys" to caps.isSupported,
                        "Device-only sign-in" to caps.immediateSignIn,
                        "Passkey or password sheet" to caps.unifiedPasswordSignIn,
                        "Upgrade (conditional create)" to caps.conditionalCreate,
                        "Signals" to caps.signals,
                        "Keyboard autofill" to caps.autofill,
                    ),
                ) { (label, on) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        StatusMark(if (on) Tone.Pass else Tone.Skip, 22)
                    }
                }
            }
        }
        Text(
            "Platform: ${controller.platform}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
}

/** Night summary: overall verdict, counts and the run button. */
@Composable
private fun SummaryCard(checks: List<CheckRow>?, running: Boolean, onRun: () -> Unit) {
    val failed = checks?.count { it.status == DoctorStatus.Fail } ?: 0
    val warned = checks?.count { it.status == DoctorStatus.Warn } ?: 0
    val passed = checks?.count { it.status == DoctorStatus.Pass } ?: 0
    val skipped = checks?.count { it.status == DoctorStatus.Skipped } ?: 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Night.panel)
            .border(1.dp, Night.line, MaterialTheme.shapes.extraLarge)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                running -> StatusMark(Tone.Busy, 40)
                checks == null -> IconTile(PassKiIcons.Shield, Night.accent, Night.panel, 40)
                failed > 0 -> StatusMark(Tone.Fail, 40)
                warned > 0 -> StatusMark(Tone.Warn, 40)
                else -> StatusMark(Tone.Pass, 40)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    when {
                        running -> "Checking…"
                        checks == null -> "Not checked yet"
                        failed > 0 -> "$failed ${if (failed == 1) "check fails" else "checks fail"}"
                        warned > 0 -> "Works, with $warned ${if (warned == 1) "warning" else "warnings"}"
                        else -> "Ready for passkeys"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    if (checks == null) "Runs the on-device doctor, then asks the demo server for its RP ID and origins."
                    else "$passed passed · $warned warnings · $failed failed · $skipped skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Button(
            onClick = onRun,
            enabled = !running,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E56FF), contentColor = Color.White),
            modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
        ) {
            Icon(PassKiIcons.Refresh, null, modifier = Modifier.size(18.dp))
            Text(if (checks == null) "Run setup check" else "Run again", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CheckItem(row: CheckRow) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusMark(row.status.tone(), 24)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.id?.let { IdChip(it) }
                Text(row.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(
                row.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (row.fixHint != null && row.status != DoctorStatus.Pass) FixHint(row.fixHint, Modifier.padding(top = 8.dp))
        }
    }
}
