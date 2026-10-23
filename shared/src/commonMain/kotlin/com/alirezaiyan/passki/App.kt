package com.alirezaiyan.passki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alirezaiyan.passki.compose.PasskeySignInState
import com.alirezaiyan.passki.compose.rememberPasskeySignIn
import com.alirezaiyan.passki.demo.PasskiDemoServer
import com.alirezaiyan.passki.ui.BrandMark
import com.alirezaiyan.passki.ui.DemoScreen
import com.alirezaiyan.passki.ui.LabScreen
import com.alirezaiyan.passki.ui.PassKiIcons
import com.alirezaiyan.passki.ui.PassKiTheme
import com.alirezaiyan.passki.ui.SetupScreen
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    Demo("Demo", PassKiIcons.Key),
    Lab("Lab", PassKiIcons.Flask),
    Setup("Setup", PassKiIcons.Shield),
}

/**
 * The PassKi sample against the live demo relying party ([PasskiDemoServer.URL]): the server issues every challenge and
 * verifies every response. Demo is a real-looking sign-in, Lab runs every flow, Setup shows the doctor checks.
 *
 * @param appOrigin the WebAuthn origin this app produces (Android: `android:apk-key-hash:…`); null means the
 *   HTTPS origin of the RP ID (iOS, web).
 */
@Composable
fun App(
    passkeys: Passkeys = remember { Passkeys() },
    autorun: Boolean = false,
    appOrigin: String? = null,
    rp: PasskiDemoServer = remember { PasskiDemoServer() },
) {
    PassKiTheme {
        val scope = rememberCoroutineScope()
        val controller = remember { DemoController(passkeys, rp, appOrigin, getPlatform().name, scope) }
        val signIn = rememberPasskeySignIn(requestOptions = { rp.authenticationOptions() }, passkeys = passkeys)
        var tab by remember { mutableStateOf(Tab.Demo) }

        LaunchedEffect(autorun) {
            if (!autorun) return@LaunchedEffect
            delay(1_500)
            controller.autorun()
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = { Box(Modifier.padding(vertical = 16.dp)) { BrandMark(40) } },
                    ) {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                                colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f)) { TabContent(tab, controller, signIn) }
                }
            } else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) { TabContent(tab, controller, signIn) }
                }
            }
        }
    }
}

@Composable
private fun TabContent(tab: Tab, controller: DemoController, signIn: PasskeySignInState) {
    when (tab) {
        Tab.Demo -> DemoScreen(controller, signIn)
        Tab.Lab -> LabScreen(controller)
        Tab.Setup -> SetupScreen(controller)
    }
}
