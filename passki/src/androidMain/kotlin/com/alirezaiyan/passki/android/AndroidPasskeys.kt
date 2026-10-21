package com.alirezaiyan.passki.android

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.SignalAllAcceptedCredentialIdsRequest
import androidx.credentials.SignalCredentialStateRequest
import androidx.credentials.SignalCurrentUserDetailsRequest
import androidx.credentials.SignalUnknownCredentialRequest
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

private const val INIT_HINT: String =
    "Call PassKi while an Activity is in the foreground. If you removed PassKi's androidx.startup " +
        "initializer, call PasskiAndroid.init(application) in Application.onCreate()."

internal class AndroidPasskeys(private val tracker: ActivityTracker = ActivityTracker) : Passkeys {

    private val manager: CredentialManager by lazy { CredentialManager.create(requireApplication()) }

    private val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override suspend fun capabilities(): PasskeyCapabilities = PasskeyCapabilities(
        isSupported = supported,
        immediateSignIn = supported,
        unifiedPasswordSignIn = supported,
        conditionalCreate = supported,
        signals = supported,
    )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            val request = GetCredentialRequest(
                credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson = options.json)),
                preferImmediatelyAvailableCredentials = mode == SignInMode.ImmediateOnly,
            )
            when (val credential = manager.getCredential(requireActivity(), request).credential) {
                is PublicKeyCredential -> parseAssertion(credential.authenticationResponseJson)
                else -> throw unexpected(credential)
            }
        }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
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

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> =
            signal { SignalUnknownCredentialRequest(SignalRequests.unknownCredential(rpId, credentialId)) }

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = signal {
            SignalAllAcceptedCredentialIdsRequest(SignalRequests.acceptedCredentials(rpId, userId, credentialIds))
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = signal {
            SignalCurrentUserDetailsRequest(SignalRequests.currentUserDetails(rpId, userId, name, displayName))
        }
    }

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> =
        passkeyCatching({ it.toPasskeyError(conditional) }) {
            requireSupported()
            val options = WebAuthnOptions.parseCreation(optionsJson)
            val request = CreatePublicKeyCredentialRequest(requestJson = options.json, isConditional = conditional)
            when (val response = manager.createCredential(requireActivity(), request)) {
                is CreatePublicKeyCredentialResponse -> parseRegistration(response.registrationResponseJson)
                else -> throw unexpected(response)
            }
        }

    private suspend fun signal(build: () -> SignalCredentialStateRequest): PasskeyResult<Unit> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            manager.signalCredentialState(build())
        }

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
