package com.example.consumer

import android.app.Activity
import android.os.Bundle
import com.alirezaiyan.passki.PassKi
import com.alirezaiyan.passki.PasskeyDoctor
import com.alirezaiyan.passki.PasskiLogLevel
import com.alirezaiyan.passki.demo.PasskiDemoServer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * The entry point that keeps PassKi's code in the minified release build: without one, R8 would strip everything and
 * a missing keep rule couldn't fail the build. It runs the setup check and the try-it flow against the demo server.
 */
class MainActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PassKi.logLevel = PasskiLogLevel.Debug
        scope.launch {
            println(PasskeyDoctor.run(PasskiDemoServer.RP_ID))
            tryIt()
        }
    }
}
