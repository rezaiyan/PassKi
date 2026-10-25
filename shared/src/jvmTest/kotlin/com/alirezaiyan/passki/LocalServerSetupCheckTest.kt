package com.alirezaiyan.passki

import com.alirezaiyan.passki.demo.PasskiDemoServer
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Setup check against a passki-web started with `PASSKI_RP_ID=localhost`, as the web sample's local mode uses. */
class LocalServerSetupCheckTest {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/api/demo/server-config") { exchange ->
            val body = """{"rpId":"localhost","origins":["https://localhost","http://localhost:8095"]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val rp = PasskiDemoServer("http://127.0.0.1:${server.address.port}")

    @AfterTest
    fun stopServer() = server.stop(0)

    private fun row(controller: DemoController, id: String) = controller.checks.orEmpty().single { it.id == id }

    @Test
    fun serverChecksPassWhenThePageIsOnTheServersRpId() = runTest {
        val controller = DemoController(Passkeys(), rp, "http://localhost:8095", "test", TestScope(), appRpId = "localhost")

        val log = controller.setupCheck()

        assertEquals(DoctorStatus.Pass, row(controller, "SRV-01").status, log)
        assertEquals(DoctorStatus.Pass, row(controller, "SRV-02").status, log)
        assertTrue("[PASS] SRV-01 server RP ID is localhost" in log, log)
    }

    @Test
    fun srv01FailsWhenTheAppUsesAnotherRpId() = runTest {
        val controller = DemoController(Passkeys(), rp, null, "test", TestScope())

        controller.setupCheck()

        val srv01 = row(controller, "SRV-01")
        assertEquals(DoctorStatus.Fail, srv01.status)
        assertEquals("Server RP ID is localhost, the app uses ${PasskiDemoServer.RP_ID}", srv01.message)
    }
}
