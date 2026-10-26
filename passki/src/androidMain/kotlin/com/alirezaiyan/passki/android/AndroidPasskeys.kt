package com.alirezaiyan.passki.android

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.autofill.AutofillManager
import android.view.inspector.WindowInspector
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PendingGetCredentialRequest
import androidx.credentials.PublicKeyCredential
import androidx.credentials.SignalAllAcceptedCredentialIdsRequest
import androidx.credentials.SignalCredentialStateRequest
import androidx.credentials.SignalCurrentUserDetailsRequest
import androidx.credentials.SignalUnknownCredentialRequest
import androidx.credentials.pendingGetCredentialRequest
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.SignalOutcome
import com.alirezaiyan.passki.internal.doctor.CheckIds
import com.alirezaiyan.passki.internal.SignalRequests
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.ModalGate
import com.alirezaiyan.passki.internal.exclusive
import com.alirezaiyan.passki.internal.passkeyCatching
import com.alirezaiyan.passki.internal.toSignalOutcome
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val INIT_HINT: String =
    "Call PassKi while an Activity is in the foreground. If you removed PassKi's androidx.startup " +
        "initializer, call PasskiAndroid.init(application) in Application.onCreate()."

/** Autofill (View.setPendingCredentialRequest) needs Android 15 (API 35). Null when available. */
internal fun autofillUnsupported(sdkInt: Int): PasskeyError.NotSupported? =
    if (sdkInt >= 35) null else PasskeyError.NotSupported("Passkey autofill requires Android 15 (API 35) or newer.")

internal val NO_FOREGROUND_ACTIVITY: PasskeyError.Misconfigured =
    PasskeyError.Misconfigured("No foreground Activity to show the passkey sheet.", INIT_HINT, CheckIds.AND_06)

internal val NOT_INITIALISED: PasskeyError.Misconfigured =
    PasskeyError.Misconfigured("PassKi is not initialised.", INIT_HINT, CheckIds.AND_06)

internal val NO_FOCUSED_FIELD: PasskeyError.Misconfigured = PasskeyError.Misconfigured(
    "Autofill needs a focused text field",
    "Use PasskeyUsernameField from passki-compose, or focus your username text field before calling " +
        "signIn(options, SignInMode.Autofill).",
)

/** A window of the app (activity, dialog, bottom sheet…): whether it has window focus, and its focused view. */
internal class WindowFocus<V>(val hasWindowFocus: Boolean, val focused: V?)

/**
 * The view to attach an autofill request to. Generic so it is testable without a device.
 * 1. An explicit [anchor] (passki-compose passes the field's own View): its focused descendant, else itself.
 * 2. The focused view of the app window that has window focus. A Compose `Dialog` or `ModalBottomSheet` is its own
 *    window, so the activity's `currentFocus` would be a stale view behind it and the suggestion would never show.
 * 3. The activity's `currentFocus`, e.g. while no window has gained focus yet.
 */
internal fun <V : Any> chooseAutofillTarget(
    anchor: V?,
    anchorFocus: V?,
    windows: List<WindowFocus<V>>,
    activityFocus: V?,
): V? = anchor?.let { anchorFocus ?: it }
    ?: windows.firstOrNull { it.hasWindowFocus && it.focused != null }?.focused
    ?: activityFocus

internal fun androidCapabilities(sdkInt: Int): PasskeyCapabilities {
    val supported = sdkInt >= Build.VERSION_CODES.P
    return PasskeyCapabilities(
        isSupported = supported,
        immediateSignIn = supported,
        unifiedPasswordSignIn = supported,
        conditionalCreate = supported,
        signals = supported,
        autofill = supported && autofillUnsupported(sdkInt) == null,
    )
}

internal class AndroidPasskeys(
    private val tracker: ActivityTracker = ActivityTracker,
    // Seams for host tests, which have no real Build values.
    private val sdkInt: () -> Int = { Build.VERSION.SDK_INT },
    private val gate: ModalGate = ModalGate.shared,
) : Passkeys {

    private val manager: CredentialManager by lazy { CredentialManager.create(requireApplication()) }

    private val supported: Boolean get() = androidCapabilities(sdkInt()).isSupported

    override suspend fun capabilities(): PasskeyCapabilities = androidCapabilities(sdkInt())

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        signIn(optionsJson, mode, anchor = null)

    /** [signIn]; in [SignInMode.Autofill] the request goes to [anchor]'s focused view when given. */
    internal suspend fun signIn(optionsJson: String, mode: SignInMode, anchor: View?): PasskeyResult<PasskeyAssertion> {
        var rpId: String? = null
        return passkeyCatching({ it.toPasskeyError(context = errorContext(rpId)) }) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            val optionsRpId = WebAuthnOptions.requireRpId(options.model.rpId, "rpId").also { rpId = it }
            val autofill = mode == SignInMode.Autofill
            if (autofill) autofillUnsupported(sdkInt())?.let { throw PasskeyException(it) }
            val immediate = mode == SignInMode.ImmediateOnly
            val response = if (autofill) {
                gate.awaitIdle() // autofill waits out a modal sheet instead of competing with it
                awaitAutofill(passkeyRequest(options.json, immediate), anchor)
            } else {
                gate.exclusive { manager.getCredential(requireActivity(), passkeyRequest(options.json, immediate)) }
            }
            when (val credential = response.credential) {
                is PublicKeyCredential -> parseAssertion(credential.authenticationResponseJson, optionsRpId)
                else -> throw unexpected(credential)
            }
        }
    }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> {
        var rpId: String? = null
        return passkeyCatching({ it.toPasskeyError(context = errorContext(rpId)) }) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            val optionsRpId = WebAuthnOptions.requireRpId(options.model.rpId, "rpId").also { rpId = it }
            val response = gate.exclusive {
                val request = GetCredentialRequest(
                    credentialOptions = listOf(
                        GetPublicKeyCredentialOption(requestJson = options.json),
                        GetPasswordOption(),
                    ),
                )
                manager.getCredential(requireActivity(), request)
            }
            when (val credential = response.credential) {
                is PublicKeyCredential ->
                    SignInCredential.Passkey(parseAssertion(credential.authenticationResponseJson, optionsRpId))
                is PasswordCredential -> SignInCredential.Password(credential.id, credential.password)
                else -> throw unexpected(credential)
            }
        }
    }

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<SignalOutcome> =
            signal(rpId) { SignalUnknownCredentialRequest(SignalRequests.unknownCredential(rpId, credentialId)) }

        override suspend fun acceptedCredentials(
            rpId: String,
            userHandle: String,
            credentialIds: List<String>,
        ): PasskeyResult<SignalOutcome> = signal(rpId) {
            SignalAllAcceptedCredentialIdsRequest(SignalRequests.acceptedCredentials(rpId, userHandle, credentialIds))
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userHandle: String,
            name: String,
            displayName: String,
        ): PasskeyResult<SignalOutcome> = signal(rpId) {
            SignalCurrentUserDetailsRequest(SignalRequests.currentUserDetails(rpId, userHandle, name, displayName))
        }
    }

    private fun passkeyRequest(requestJson: String, preferImmediatelyAvailable: Boolean) = GetCredentialRequest(
        credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson = requestJson)),
        preferImmediatelyAvailableCredentials = preferImmediatelyAvailable,
    )

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> {
        var rpId: String? = null
        return passkeyCatching({ it.toPasskeyError(conditional, errorContext(rpId)) }) {
            requireSupported()
            val options = WebAuthnOptions.parseCreation(optionsJson)
            val optionsRpId = WebAuthnOptions.requireRpId(options.model.rp.id, "rp.id").also { rpId = it }
            val response = gate.exclusive {
                val request = CreatePublicKeyCredentialRequest(requestJson = options.json, isConditional = conditional)
                manager.createCredential(requireActivity(), request)
            }
            when (response) {
                is CreatePublicKeyCredentialResponse -> parseRegistration(response.registrationResponseJson, optionsRpId)
                else -> throw unexpected(response)
            }
        }
    }

    /**
     * Attaches [request] to the focused view ([chooseAutofillTarget]; androidx `View.pendingGetCredentialRequest`,
     * API 35+) so the IME / autofill offers passkeys, and waits for the user to pick one. Errors and dismissals are
     * not delivered by the framework, so this only completes on success or cancellation; the pending request is
     * cleared (main thread) in both cases.
     *
     * If the view's window doesn't have window focus (a dialog, bottom sheet or the passkey sheet itself is on top),
     * the request is attached only once it gains focus. Restarting the autofill session cancels the session of
     * every window of the activity, so attaching earlier would drop a passkey the user is picking in the window on
     * top (seen on an API 36 emulator: a field behind a dialog re-armed during the PIN prompt, and GPM's result was
     * lost with "onAuthenticationResult(): sessionId=… not active").
     */
    private suspend fun awaitAutofill(request: GetCredentialRequest, anchor: View?): GetCredentialResponse =
        suspendCancellableCoroutine { continuation ->
            val main = Handler(Looper.getMainLooper())
            var cleanup: () -> Unit = {} // main thread only
            continuation.invokeOnCancellation { main.post { cleanup() } }
            main.post {
                if (!continuation.isActive) return@post
                val view = autofillTarget(anchor)
                if (view == null) {
                    continuation.resumeWithException(PasskeyException(NO_FOCUSED_FIELD))
                    return@post
                }

                fun attach() {
                    lateinit var pending: PendingGetCredentialRequest
                    fun clear() {
                        if (view.pendingGetCredentialRequest === pending) view.pendingGetCredentialRequest = null
                    }
                    pending = PendingGetCredentialRequest(request) { response ->
                        main.post(::clear)
                        if (continuation.isActive) continuation.resume(response)
                    }
                    view.pendingGetCredentialRequest = pending
                    cleanup = ::clear
                    // The autofill session of the already-focused field started before the request was attached,
                    // so restart it: without this the suggestion only appears after re-focusing.
                    // requestAutofill(view) is not enough: the chip shows, but the picked credential is dropped
                    // ("no responses") — the session must be restarted (verified on an API 36 emulator with GPM,
                    // EditText and Compose fields).
                    view.context.getSystemService(AutofillManager::class.java)?.let { autofill ->
                        autofill.cancel()
                        autofill.notifyViewEntered(view)
                    }
                }

                if (view.hasWindowFocus()) {
                    attach()
                } else {
                    val observer = view.viewTreeObserver
                    val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
                        override fun onWindowFocusChanged(hasFocus: Boolean) {
                            if (!hasFocus) return
                            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(this)
                            if (continuation.isActive) attach()
                        }
                    }
                    observer.addOnWindowFocusChangeListener(listener)
                    cleanup = { if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener) }
                }
            }
        }

    /** Main thread only. */
    private fun autofillTarget(anchor: View?): View? = chooseAutofillTarget(
        anchor = anchor,
        anchorFocus = anchor?.findFocus(),
        windows = if (anchor != null) emptyList() else appWindows(),
        activityFocus = tracker.currentActivity?.currentFocus,
    )

    /** Root views of this process's windows (activities, dialogs, popups), API 29+. */
    private fun appWindows(): List<WindowFocus<View>> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            emptyList()
        } else {
            WindowInspector.getGlobalWindowViews().map { root -> WindowFocus(root.hasWindowFocus(), root.findFocus()) }
        }

    private suspend fun signal(rpId: String, build: () -> SignalCredentialStateRequest): PasskeyResult<SignalOutcome> =
        passkeyCatching({ it.toPasskeyError(context = errorContext(rpId)) }) {
            requireSupported() // signals need the same Android 9+ as everything else; providers may still decline
            manager.signalCredentialState(build())
            Unit
        }.toSignalOutcome()

    // Only built when a request failed, so the signing certificates are read lazily (and cached by AppSigning).
    private fun errorContext(rpId: String?): AndroidErrorContext {
        val app = tracker.application
        return AndroidErrorContext(
            rpId = rpId,
            packageName = app?.packageName,
            signingFingerprints = app?.let(AppSigning::fingerprints).orEmpty(),
            isEmulator = AppSigning.isEmulator,
        )
    }

    private fun requireSupported() {
        if (!supported) throw PasskeyException(PasskeyError.NotSupported("Passkeys require Android 9 (API 28) or newer."))
    }

    private fun requireActivity(): Activity = tracker.currentActivity
        ?: throw PasskeyException(NO_FOREGROUND_ACTIVITY)

    private fun requireApplication(): Application = tracker.application
        ?: throw PasskeyException(NOT_INITIALISED)

    private fun unexpected(value: Any): PasskeyException =
        PasskeyException(PasskeyError.Unknown("Unexpected credential type ${value::class.simpleName}.", null))
}
