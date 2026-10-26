@file:OptIn(ExperimentalWasmJsInterop::class)

package com.alirezaiyan.passki

internal actual fun consoleLog(method: String, message: String): Unit = js("console[method](message)")
