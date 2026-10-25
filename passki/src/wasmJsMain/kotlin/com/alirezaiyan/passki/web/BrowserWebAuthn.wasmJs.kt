@file:OptIn(ExperimentalWasmJsInterop::class)

package com.alirezaiyan.passki.web

internal actual fun passkiAbort(id: Int): Unit = js(ABORT_JS)

internal actual fun passkiWebAuthn(
    op: String,
    json: String,
    mediation: String,
    id: Int,
    ok: (String) -> Unit,
    fail: (String, String) -> Unit,
): Unit = js(WEBAUTHN_JS)
