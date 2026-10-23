package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials
import platform.AuthenticationServices.ASAuthorizationRequest
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ASAuthorizationController.delegate is weak: keep sessions alive until a callback fires. Main thread only.
// File-level because Kotlin/Native does not allow fields in the companion of an ObjC subclass.
private val active = mutableSetOf<AuthorizationSession>()

private val arbiter = AuthorizationArbiter()

/** Bridges one ASAuthorizationController run to a suspend call. */
internal class AuthorizationSession private constructor(
    private val window: UIWindow,
    private val continuation: CancellableContinuation<ASAuthorization>,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    // Set when the app resigns active, i.e. the out-of-process system sheet really appeared. This
    // distinguishes "sheet shown, user cancelled" from "nothing to show" under
    // preferImmediatelyAvailableCredentials (both report code 1001). iOS asks for the presentation
    // anchor in both cases, so the anchor callback can't be used for this (verified on the simulator).
    private var presented = false
    private var resignObserver: Any? = null

    // Ends this request's registration with the arbiter; idempotent.
    private var release: () -> Unit = {}

    private fun observeSheet() {
        resignObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> presented = true }
    }

    private fun finish() {
        active.remove(this)
        release()
        resignObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        resignObserver = null
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        finish()
        if (continuation.isActive) continuation.resume(didCompleteWithAuthorization)
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        finish()
        if (continuation.isActive) {
            continuation.resumeWithException(
                AppleAuthorizationException(
                    code = didCompleteWithError.code,
                    message = didCompleteWithError.localizedDescription,
                    presented = presented,
                ),
            )
        }
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        window

    /** How the controller is started. */
    enum class Presentation { Modal, PreferImmediatelyAvailable, AutoFill }

    companion object {
        suspend fun perform(
            requests: List<ASAuthorizationRequest>,
            anchor: () -> UIWindow?,
            preferImmediatelyAvailable: Boolean,
        ): ASAuthorization = perform(
            requests,
            anchor,
            if (preferImmediatelyAvailable) Presentation.PreferImmediatelyAvailable else Presentation.Modal,
        )

        /**
         * Offers [requests] in the QuickType bar of the focused username field
         * (`performAutoFillAssistedRequests`, iOS 16+). Cancelling the coroutine calls `controller.cancel()`;
         * the resulting 1001 is ignored because the continuation is no longer active.
         *
         * Waits while a modal request runs, and a modal request that starts later cancels this one with 1001
         * ([AuthorizationArbiter]).
         */
        suspend fun performAutoFill(requests: List<ASAuthorizationRequest>, anchor: () -> UIWindow?): ASAuthorization =
            perform(requests, anchor, Presentation.AutoFill)

        private suspend fun perform(
            requests: List<ASAuthorizationRequest>,
            anchor: () -> UIWindow?,
            presentation: Presentation,
        ): ASAuthorization = withContext(Dispatchers.Main) {
            val autoFill = presentation == Presentation.AutoFill
            // AutoFill waits out modal requests. Nothing below suspends before the controller starts and registers,
            // so no modal request can begin in between.
            if (autoFill) arbiter.awaitNoModal()
            // Resolve on the main thread (UIKit) and fail fast rather than present on a detached window.
            val window = anchor() ?: throw PasskeyException(
                PasskeyError.Misconfigured(
                    "No foreground window to present the passkey sheet.",
                    "Call PassKi while your app has a visible, foreground-active window scene.",
                ),
            )
            suspendCancellableCoroutine { continuation ->
                val session = AuthorizationSession(window, continuation)
                active += session
                session.observeSheet()
                val controller = ASAuthorizationController(authorizationRequests = requests)
                controller.delegate = session
                controller.presentationContextProvider = session
                session.release = if (autoFill) arbiter.beginAutoFill { controller.cancel() } else arbiter.beginModal()
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        controller.cancel()
                        session.finish()
                    }
                }
                when (presentation) {
                    Presentation.PreferImmediatelyAvailable ->
                        controller.performRequestsWithOptions(ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials)
                    Presentation.AutoFill -> controller.performAutoFillAssistedRequests()
                    Presentation.Modal -> controller.performRequests()
                }
            }
        }
    }
}
