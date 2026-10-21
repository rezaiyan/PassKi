package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.HttpResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

private const val TIMEOUT_SECONDS = 10.0
private const val MAX_BODY_BYTES = 1 shl 20

/**
 * [HttpGetter] on NSURLSession: redirects are reported, never followed, and failures become [HttpResult.error].
 * Each call uses its own ephemeral session (no cookies or cache) so the redirect-refusing delegate stays scoped to it.
 */
internal object NSUrlSessionGetter : HttpGetter {
    override suspend fun get(url: String): HttpResult {
        val nsUrl = NSURL.URLWithString(url) ?: return failure(url, "invalid URL")
        val request = NSMutableURLRequest.requestWithURL(nsUrl, NSURLRequestReloadIgnoringLocalCacheData, TIMEOUT_SECONDS)
        request.setValue("application/json", forHTTPHeaderField = "Accept")

        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.timeoutIntervalForRequest = TIMEOUT_SECONDS
        configuration.timeoutIntervalForResource = TIMEOUT_SECONDS
        val session = NSURLSession.sessionWithConfiguration(configuration, delegate = RefuseRedirects(), delegateQueue = null)

        return suspendCancellableCoroutine { continuation ->
            val task = session.dataTaskWithRequest(request) { data, response, error ->
                val result = if (error != null) {
                    failure(url, error.localizedDescription)
                } else {
                    val http = response as? NSHTTPURLResponse
                    val status = http?.statusCode?.toInt() ?: -1
                    HttpResult(
                        url = url,
                        status = status,
                        contentType = http?.valueForHTTPHeaderField("Content-Type"),
                        body = data?.toCappedString(),
                        redirectedTo = if (status in 300..399) http?.valueForHTTPHeaderField("Location") else null,
                        error = if (http == null) "not an HTTP response" else null,
                    )
                }
                session.finishTasksAndInvalidate()
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }

    private fun failure(url: String, error: String) =
        HttpResult(url, status = -1, contentType = null, body = null, redirectedTo = null, error = error)
}

/** Completes redirects with a null request, so the task finishes with the 3xx response itself. */
private class RefuseRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        completionHandler(null)
    }
}

/** UTF-8 decode of at most [MAX_BODY_BYTES]; invalid sequences become U+FFFD. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toCappedString(): String {
    val size = minOf(length.toLong(), MAX_BODY_BYTES.toLong()).toInt()
    if (size == 0) return ""
    val bytes = ByteArray(size)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, size.toULong()) }
    return bytes.decodeToString()
}
