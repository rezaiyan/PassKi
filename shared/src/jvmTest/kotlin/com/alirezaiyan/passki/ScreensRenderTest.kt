package com.alirezaiyan.passki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.alirezaiyan.passki.compose.rememberPasskeySignIn
import com.alirezaiyan.passki.ui.DemoScreen
import com.alirezaiyan.passki.ui.LabScreen
import com.alirezaiyan.passki.ui.PassKiTheme
import com.alirezaiyan.passki.ui.SetupScreen
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders every screen of the sample headlessly, so a layout that throws fails the build. Set
 * `PASSKI_SCREENSHOTS=<dir>` to also write each screen as a PNG for review.
 */
@OptIn(ExperimentalTestApi::class)
class ScreensRenderTest {
    private val rp = DemoRelyingParty(
        client = HttpClient(
            MockEngine {
                respond(
                    """{"rpId":"passki.alirezaiyan.com","origins":["https://passki.alirezaiyan.com"]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
    )

    private val verdict = ServerVerdict(
        verified = true,
        credentialId = "Z_51XXZLGZtJJ6o_tOWw0wQk3x1bA9yPq",
        signCount = 3,
        userVerified = true,
        backupEligible = true,
        backedUp = true,
        aaguid = "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4",
        userHandle = "_6p3C4cTU_qdN80-4CWAA",
        origin = "android:apk-key-hash:WeWSMk5s5vpf4vCKORgTLaUd3l35vRHwXOgjV4jiBsA",
        rpId = "passki.alirezaiyan.com",
    )

    private fun SemanticsNodeInteractionsProvider.save(name: String) {
        val dir = System.getenv("PASSKI_SCREENSHOTS") ?: return
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun demoSignedOutAndSignedIn() = runDesktopComposeUiTest(412, 900) {
        lateinit var controller: DemoController
        setContent {
            val scope = rememberCoroutineScope()
            controller = remember { DemoController(Passkeys(), rp, null, "JVM test", scope) }
            PassKiTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { DemoScreen(controller, rememberPasskeySignIn(requestOptions = { "{}" }, passkeys = controller.passkeys)) } }
        }
        onNodeWithText("Sign in with a passkey").assertExists()
        save("demo-signed-out")

        controller.username = "ali"
        controller.session = Session(verdict, "Sign in (autofill)")
        waitForIdle()
        onNodeWithText("Verified by the server").assertExists()
        save("demo-signed-in")
    }

    @Test
    fun labWithActivity() = runDesktopComposeUiTest(412, 1400) {
        lateinit var controller: DemoController
        setContent {
            val scope = rememberCoroutineScope()
            controller = remember { DemoController(Passkeys(), rp, null, "JVM test", scope) }
            PassKiTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LabScreen(controller) } }
        }
        controller.show("Register", failureLog("Register", PasskeyError.Misconfigured("App is not associated with passki.alirezaiyan.com.", "Serve apple-app-site-association.")))
        controller.show("Sign in (device only)", "Sign in (device only) ✓\n$verdict")
        waitForIdle()
        onNodeWithText("Activity").assertExists()
        save("lab")
    }

    @Test
    fun setupAfterACheck() = runDesktopComposeUiTest(412, 1300) {
        lateinit var controller: DemoController
        setContent {
            val scope = rememberCoroutineScope()
            controller = remember { DemoController(Passkeys(), rp, null, "JVM test", scope) }
            PassKiTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SetupScreen(controller) } }
        }
        controller.run("Setup check") { controller.setupCheck() }
        waitUntil(timeoutMillis = 10_000) { controller.checks != null }
        onNodeWithText("SRV-01").assertExists()
        save("setup")
    }
}
