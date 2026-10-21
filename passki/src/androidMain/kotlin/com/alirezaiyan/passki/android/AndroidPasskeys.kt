package com.alirezaiyan.passki.android

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.alirezaiyan.passki.internal.SignalRequests
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.passkeyCatching
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val INIT_HINT: String =
    "Call PassKi while an Activity is in the foreground. If you removed PassKi's androidx.startup " +
        "initializer, call PasskiAndroid.init(application) in Application.onCreate()."

/** Autofill (View.setPendingCredentialRequest) needs Android 15 (API 35). Null when available. */
internal fun autofillUnsupported(sdkInt: Int): PasskeyError.NotSupported? =
    if (sdkInt >= 35) null else PasskeyError.NotSupported("Passkey autofill requires Android 15 (API 35) or newer.")

internal val NO_FOCUSED_FIELD: PasskeyError.Misconfigured = PasskeyError.Misconfigured(
    "Autofill needs a focused text field",
    "Use PasskeyUsernameField from passki-compose, or focus your username text field before calling " +
        "signIn(options, SignInMode.Autofill).",
)

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

internal class AndroidPasskeys(private val tracker: ActivityTracker = ActivityTracker) : Passkeys {

    private val manager: CredentialManager by lazy { CredentialManager.create(requireApplication()) }

    private val supported: Boolean get() = androidCapabilities(Build.VERSION.SDK_INT).isSupported

    override suspend fun capabilities(): PasskeyCapabilities = androidCapabilities(Build.VERSION.SDK_INT)

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
        var context = errorContext(rpId = null)
        return passkeyCatching({ it.toPasskeyError(context = context) }) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            context = errorContext(options.model.rpId)
            val autofill = mode == SignInMode.Autofill
            if (autofill) autofillUnsupported(Build.VERSION.SDK_INT)?.let { throw PasskeyException(it) }
            val request = GetCredentialRequest(
                credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson = options.json)),
                preferImmediatelyAvailableCredentials = mode == SignInMode.ImmediateOnly,
            )
            val response = if (autofill) awaitAutofill(request) else manager.getCredential(requireActivity(), request)
            when (val credential = response.credential) {
                is PublicKeyCredential -> parseAssertion(credential.authenticationResponseJson)
                else -> throw unexpected(credential)
            }
        }
    }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> {
        var context = errorContext(rpId = null)
        return passkeyCatching({ it.toPasskeyError(context = context) }) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            context = errorContext(options.model.rpId)
            val request = GetCredentialRequest(
                credentialOptions = listOf(
                    GetPublicKeyCredentialOption(requestJson = options.json),
                    GetPasswordOption(),
                ),
            )
            when (val credential = manager.getCredential(requireActivity(), request).credential) {
                is PublicKeyCredential ->
                    SignInCredential.Passkey(parseAssertion(credential.authenticationResponseJson))
                is PasswordCredential -> SignInCredential.Password(credential.id, credential.password)
                else -> throw unexpected(credential)
            }
        }
    }

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> =
            signal(rpId) { SignalUnknownCredentialRequest(SignalRequests.unknownCredential(rpId, credentialId)) }

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = signal(rpId) {
            SignalAllAcceptedCredentialIdsRequest(SignalRequests.acceptedCredentials(rpId, userId, credentialIds))
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = signal(rpId) {
            SignalCurrentUserDetailsRequest(SignalRequests.currentUserDetails(rpId, userId, name, displayName))
        }
    }

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> {
        var context = errorContext(rpId = null)
        return passkeyCatching({ it.toPasskeyError(conditional, context) }) {
            requireSupported()
            val options = WebAuthnOptions.parseCreation(optionsJson)
            context = errorContext(options.model.rp.id)
            val request = CreatePublicKeyCredentialRequest(requestJson = options.json, isConditional = conditional)
            when (val response = manager.createCredential(requireActivity(), request)) {
                is CreatePublicKeyCredentialResponse -> parseRegistration(response.registrationResponseJson)
                else -> throw unexpected(response)
            }
        }
    }

    /**
     * Attaches [request] to the focused view (androidx `View.pendingGetCredentialRequest`, API 35+) so the IME /
     * autofill offers passkeys, and waits for the user to pick one. Errors and dismissals are not delivered by
     * the framework, so this only completes on success or cancellation; the pending request is cleared (main
     * thread) in both cases.
     */
    private suspend fun awaitAutofill(request: GetCredentialRequest): GetCredentialResponse =
        suspendCancellableCoroutine { continuation ->
            val main = Handler(Looper.getMainLooper())
            main.post {
                if (!continuation.isActive) return@post
                val view = tracker.currentActivity?.currentFocus
                if (view == null) {
                    continuation.resumeWithException(PasskeyException(NO_FOCUSED_FIELD))
                    return@post
                }
                lateinit var pending: PendingGetCredentialRequest
                fun clear() {
                    if (view.pendingGetCredentialRequest === pending) view.pendingGetCredentialRequest = null
                }
                pending = PendingGetCredentialRequest(request) { response ->
                    main.post(::clear)
                    if (continuation.isActive) continuation.resume(response)
                }
                view.pendingGetCredentialRequest = pending
                continuation.invokeOnCancellation { main.post(::clear) }
            }
        }

    private suspend fun signal(rpId: String, build: () -> SignalCredentialStateRequest): PasskeyResult<Unit> =
        passkeyCatching({ it.toPasskeyError(context = errorContext(rpId)) }) {
            requireSupported()
            manager.signalCredentialState(build())
        }

    private fun errorContext(rpId: String?): AndroidErrorContext =
        AndroidErrorContext(rpId = rpId, packageName = tracker.application?.packageName)

    private fun requireSupported() {
        if (!supported) throw PasskeyException(PasskeyError.NotSupported("Passkeys require Android 9 (API 28) or newer."))
    }

    private fun requireActivity(): Activity = tracker.currentActivity
        ?: throw PasskeyException(PasskeyError.Misconfigured("No foreground Activity to show the passkey sheet.", INIT_HINT))

    private fun requireApplication(): Application = tracker.application
        ?: throw PasskeyException(PasskeyError.Misconfigured("PassKi is not initialised.", INIT_HINT))

    private fun unexpected(value: Any): PasskeyException =
        PasskeyException(PasskeyError.Unknown("Unexpected credential type ${value::class.simpleName}.", null))
}
