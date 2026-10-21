package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.HttpResult
import com.alirezaiyan.passki.internal.doctor.MAX_BODY_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MS = 10_000

/** [HttpGetter] on [HttpURLConnection]: redirects are reported, never followed, and failures become [HttpResult.error]. */
internal object UrlConnectionGetter : HttpGetter {
    override suspend fun get(url: String): HttpResult = withContext(Dispatchers.IO) {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext failure(url, e)
        } catch (e: IllegalArgumentException) {
            return@withContext failure(url, e)
        }
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readCapped() }
            HttpResult(
                url = url,
                status = status,
                contentType = connection.contentType,
                body = body?.text,
                redirectedTo = if (status in 300..399) connection.getHeaderField("Location") else null,
                error = null,
                truncated = body?.truncated == true,
            )
        } catch (e: IOException) {
            failure(url, e)
        } catch (e: SecurityException) { // INTERNET permission missing
            failure(url, e)
        } finally {
            connection.disconnect()
        }
    }

    private class CappedBody(val text: String, val truncated: Boolean)

    /** Reads at most [MAX_BODY_BYTES], then probes one more byte to tell "exactly at the cap" from "cut off". */
    private fun InputStream.readCapped(): CappedBody { // InputStream.readNBytes needs API 33.
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var ended = false
        while (out.size() < MAX_BODY_BYTES) {
            val read = read(buffer, 0, minOf(buffer.size, MAX_BODY_BYTES - out.size()))
            if (read < 0) {
                ended = true
                break
            }
            out.write(buffer, 0, read)
        }
        val truncated = !ended && read() >= 0
        return CappedBody(out.toByteArray().decodeToString(), truncated)
    }

    private fun failure(url: String, e: Exception) =
        HttpResult(url, status = -1, contentType = null, body = null, redirectedTo = null, error = e.toString())
}
