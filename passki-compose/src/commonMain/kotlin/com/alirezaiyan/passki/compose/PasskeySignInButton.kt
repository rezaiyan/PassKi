package com.alirezaiyan.passki.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import kotlinx.coroutines.launch

/**
 * A Material 3 button that signs in with a passkey.
 *
 * Tapping it calls [PasskeySignInState.signIn]. While the request runs the button is disabled and shows a small
 * progress indicator before [content].
 *
 * @param state from [rememberPasskeySignIn].
 * @param onSignedIn called with the assertion to send to your server.
 * @param modifier applied to the button.
 * @param onError called for every failure except [PasskeyError.Cancelled] (the user dismissing the sheet).
 *   The same error is also available as [PasskeySignInState.lastError].
 * @param enabled whether the button can be tapped; it is also disabled while a sign-in is running.
 * @param content the button label; defaults to "Sign in with a passkey".
 */
@Composable
public fun PasskeySignInButton(
    state: PasskeySignInState,
    onSignedIn: (PasskeyAssertion) -> Unit,
    modifier: Modifier = Modifier,
    onError: (PasskeyError) -> Unit = {},
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = { Text("Sign in with a passkey") },
) {
    val scope = rememberCoroutineScope()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    val currentOnError by rememberUpdatedState(onError)
    Button(
        onClick = {
            scope.launch {
                when (val result = state.signIn()) {
                    is PasskeyResult.Success -> currentOnSignedIn(result.value)
                    is PasskeyResult.Failure ->
                        if (result.error != PasskeyError.Cancelled) currentOnError(result.error)
                }
            }
        },
        modifier = modifier,
        enabled = enabled && !state.isRunning,
    ) {
        if (state.isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
}
