package com.alirezaiyan.passki.demo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasskiDemoServerTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun rp(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PasskiDemoServer(
            "https://rp.test",
            HttpClient(MockEngine { request ->
                requests += request to when (val body = request.body) {
                    is TextContent -> body.text
                    is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                    else -> ""
                }
                handler(request)
            }),
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun verdict(credentialId: String, handle: String = "H1", signCount: Int = 0) =
        """{"verified":true,"credentialId":"$credentialId","signCount":$signCount,"userVerified":true,"backupEligible":true,
           "backedUp":true,"userHandle":"$handle","origin":"android:apk-key-hash:x","rpId":"passki.alirezaiyan.com","extra":1}"""

    @Test
    fun registrationOptions_postsUsernameAndReturnsServerJson() = runTest {
        val rp = rp { json("""{"challenge":"c1","user":{"id":"H1"}}""") }
        assertEquals("""{"challenge":"c1","user":{"id":"H1"}}""", rp.registrationOptions("alice"))
        val (request, body) = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://rp.test/api/demo/register/options", request.url.toString())
        assertEquals("alice", Json.parseToJsonElement(body).jsonObject["username"]!!.jsonPrimitive.content)
    }

    @Test
    fun verifyRegistration_wrapsResponseAddsOptInCaptureAndRemembersAccount() = runTest {
        val rp = rp { json(verdict("C1")) }
        val verdict = rp.verifyRegistration("""{"id":"C1","response":{}}""", shareFixtureAs = "android")
        assertTrue(verdict.verified)
        assertEquals(DemoAccount("H1", listOf("C1")), rp.account)
        val body = Json.parseToJsonElement(requests.single().second).jsonObject
        assertEquals("C1", body["response"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("android", body["capture"]!!.jsonObject["platform"]!!.jsonPrimitive.content)
        assertTrue(verdict.toString().startsWith("verified by server ✓ signCount 0 userVerified true"), verdict.toString())
    }

    @Test
    fun verifyRegistration_withoutOptIn_sendsNoCapture() = runTest {
        val rp = rp { json(verdict("C1")) }
        rp.verifyRegistration("""{"id":"C1"}""")
        assertNull(Json.parseToJsonElement(requests.single().second).jsonObject["capture"])
    }

    @Test
    fun verifyAuthentication_updatesAccountWithServerHandle() = runTest {
        val rp = rp { json(verdict("C9", handle = "H9", signCount = 3)) }
        assertEquals(3, rp.verifyAuthentication("""{"id":"C9"}""").signCount)
        assertEquals(DemoAccount("H9", listOf("C9")), rp.account)
        assertEquals("https://rp.test/api/demo/login/verify", requests.single().first.url.toString())
    }

    @Test
    fun serverError_surfacesStatusAndServerMessage() = runTest {
        val rp = rp { json("""{"error":"unknown, expired or already used challenge"}""", HttpStatusCode.BadRequest) }
        val e = assertFailsWith<DemoServerException> { rp.verifyAuthentication("""{"id":"C1"}""") }
        assertEquals(400, e.status)
        assertEquals("demo server HTTP 400: unknown, expired or already used challenge", e.message)
    }

    @Test
    fun unreachableServer_isReportedWithoutStatus() = runTest {
        val rp = rp { throw IllegalStateException("no route to host") }
        val e = assertFailsWith<DemoServerException> { rp.serverConfig() }
        assertNull(e.status)
        assertTrue(e.message!!.contains("unreachable"), e.message)
    }

    @Test
    fun serverConfig_parsesRpIdAndOrigins() = runTest {
        val rp = rp { json("""{"rpId":"passki.alirezaiyan.com","origins":["https://passki.alirezaiyan.com","android:apk-key-hash:x"]}""") }
        assertEquals(DemoServerConfig("passki.alirezaiyan.com", listOf("https://passki.alirezaiyan.com", "android:apk-key-hash:x")), rp.serverConfig())
    }
}
