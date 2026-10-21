package com.alirezaiyan.passki.android

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlConnectionGetterTest {
    private lateinit var server: HttpServer
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/ok") { exchange ->
                val body = "[]".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/moved") { exchange ->
                exchange.responseHeaders.add("Location", "$base/ok")
                exchange.sendResponseHeaders(301, -1)
                exchange.close()
            }
            createContext("/huge") { exchange ->
                val body = ByteArray((1 shl 20) + 10) { '['.code.toByte() }
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/exact") { exchange ->
                val body = ByteArray(1 shl 20) { ' '.code.toByte() }
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/missing") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            start()
        }
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun okReturnsBodyAndContentType() = runTest {
        val result = UrlConnectionGetter.get("$base/ok")
        assertEquals(200, result.status)
        assertEquals("application/json", result.contentType)
        assertEquals("[]", result.body)
        assertNull(result.redirectedTo)
        assertNull(result.error)
    }

    @Test
    fun redirectIsNotFollowedAndLocationReported() = runTest {
        val result = UrlConnectionGetter.get("$base/moved")
        assertEquals(301, result.status)
        assertEquals("$base/ok", result.redirectedTo)
    }

    @Test
    fun notFoundKeepsStatus() = runTest {
        assertEquals(404, UrlConnectionGetter.get("$base/missing").status)
    }

    @Test
    fun transportFailureIsStatusMinusOneWithError() = runTest {
        val port = server.address.port
        server.stop(0)
        val result = UrlConnectionGetter.get("http://127.0.0.1:$port/ok")
        assertEquals(-1, result.status)
        assertNotNull(result.error)
    }

    @Test
    fun bodyOverCapIsTruncatedAndFlagged() = runTest {
        val result = UrlConnectionGetter.get("$base/huge")
        assertEquals(1 shl 20, result.body!!.length)
        assertTrue(result.truncated)
    }

    @Test
    fun bodyExactlyAtCapIsNotFlagged() = runTest {
        val result = UrlConnectionGetter.get("$base/exact")
        assertEquals(1 shl 20, result.body!!.length)
        assertFalse(result.truncated)
    }

    @Test
    fun smallBodyIsNotTruncated() = runTest {
        assertFalse(UrlConnectionGetter.get("$base/ok").truncated)
    }
}
