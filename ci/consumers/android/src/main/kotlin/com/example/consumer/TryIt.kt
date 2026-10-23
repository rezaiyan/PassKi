package com.example.consumer

import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.demo.PasskiDemoServer
import com.alirezaiyan.passki.onSuccess

/** The README's "try it in your app" code, verbatim. */
suspend fun tryIt(passkeys: Passkeys = Passkeys()) {
    val demo = PasskiDemoServer()
    passkeys.register(demo.registrationOptions("alice"))
        .onSuccess { println(demo.verifyRegistration(it.json)) } // verified by server ✓ signCount 0 …
    passkeys.signIn(demo.authenticationOptions())
        .onSuccess { println(demo.verifyAuthentication(it.json)) }
}
