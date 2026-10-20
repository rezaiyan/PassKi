package com.alirezaiyan.passki.apple

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
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ASAuthorizationController.delegate is weak: keep sessions alive until a callback fires. Main thread only.
// File-level because Kotlin/Native does not allow fields in the companion of an ObjC subclass.
private val active = mutableSetOf<AuthorizationSession>()

/** Bridges one ASAuthorizationController run to a suspend call. */
internal class AuthorizationSession private constructor(
    private val anchor: () -> UIWindow?,
    private val continuation: CancellableContinuation<ASAuthorization>,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        active.remove(this)
        if (continuation.isActive) continuation.resume(didCompleteWithAuthorization)
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        active.remove(this)
        if (continuation.isActive) {
            continuation.resumeWithException(
                AppleAuthorizationException(didCompleteWithError.code, didCompleteWithError.localizedDescription),
            )
        }
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        anchor() ?: UIWindow()

    companion object {
        suspend fun perform(
            requests: List<ASAuthorizationRequest>,
            anchor: () -> UIWindow?,
            preferImmediatelyAvailable: Boolean,
        ): ASAuthorization = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val session = AuthorizationSession(anchor, continuation)
                active += session
                val controller = ASAuthorizationController(authorizationRequests = requests)
                controller.delegate = session
                controller.presentationContextProvider = session
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        controller.cancel()
                        active.remove(session)
                    }
                }
                if (preferImmediatelyAvailable) {
                    controller.performRequestsWithOptions(ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials)
                } else {
                    controller.performRequests()
                }
            }
        }
    }
}
