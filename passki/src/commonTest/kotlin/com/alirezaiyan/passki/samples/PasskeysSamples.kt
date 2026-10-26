package com.alirezaiyan.passki.samples

import com.alirezaiyan.passki.PasskeyErrorCategory
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.fold
import com.alirezaiyan.passki.onFailure
import com.alirezaiyan.passki.onSuccess

// Samples linked from the KDoc with @sample. They live in commonTest so they compile with every build.

/** Your server's passkey endpoints, as the samples call them. */
interface SampleServer {
    suspend fun registrationOptions(): String
    suspend fun finishRegistration(responseJson: String)
    suspend fun authenticationOptions(): String
    suspend fun finishSignIn(responseJson: String)
}

suspend fun registerAndSignIn(server: SampleServer, showError: (String) -> Unit) {
    val passkeys = Passkeys() // no Activity, no UIWindow

    passkeys.register(server.registrationOptions())
        .onSuccess { server.finishRegistration(it.json) } // your server verifies the response
        .onFailure { error ->
            when (error.category) {
                PasskeyErrorCategory.UserCancelled -> Unit // the user closed the sheet
                PasskeyErrorCategory.Setup -> showError("Passkeys aren't working right now.") // PassKi logs the fix
                else -> showError(error.message)
            }
        }

    passkeys.signIn(server.authenticationOptions(), SignInMode.ImmediateOnly)
        .onSuccess { server.finishSignIn(it.json) }
}

sealed interface SignInState {
    data object Idle : SignInState
    data object SignedIn : SignInState
    data class Failed(val message: String) : SignInState
}

suspend fun foldIntoYourOwnType(passkeys: Passkeys, server: SampleServer): SignInState =
    passkeys.signIn(server.authenticationOptions()).fold(
        onSuccess = { server.finishSignIn(it.json); SignInState.SignedIn },
        onFailure = { if (it.isUserCancellation) SignInState.Idle else SignInState.Failed(it.message) },
    )
