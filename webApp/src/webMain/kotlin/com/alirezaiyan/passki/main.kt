package com.alirezaiyan.passki

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.alirezaiyan.passki.demo.PasskiDemoServer
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // On localhost the page can't use passkeys for the hosted RP, so it talks to a local passki-web on the same
    // origin instead (the dev server proxies /api to it, see webApp/webpack.config.d).
    val local = window.location.hostname == "localhost"
    ComposeViewport {
        if (local) {
            val origin = window.location.origin
            App(appOrigin = origin, rp = remember { PasskiDemoServer(origin) }, appRpId = window.location.hostname)
        } else {
            App()
        }
    }
}
