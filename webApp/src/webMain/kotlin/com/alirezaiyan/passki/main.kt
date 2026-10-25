package com.alirezaiyan.passki

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.alirezaiyan.passki.webdemo.LocalRelyingParty
import com.alirezaiyan.passki.webdemo.WebDemo

private fun pageHostname(): String = js("window.location.hostname")

private fun pageOrigin(): String = js("window.location.origin")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Browsers only accept an RP ID that matches the page, so the web sample is its own relying party (see
    // LocalRelyingParty) instead of using the hosted demo server like the Android, iOS and desktop samples.
    val rp = LocalRelyingParty(rpId = pageHostname(), origin = pageOrigin())
    ComposeViewport {
        WebDemo(rp)
    }
}
