package com.alirezaiyan.passki.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.compose.internal.AutofillController

/**
 * A username text field that offers the user's passkeys as keyboard suggestions (passkey autofill).
 *
 * While the field is focused and the platform supports it ([PasskeyCapabilities.autofill]: Android 15 / API 35+,
 * iOS 16+), the field keeps a [SignInMode.Autofill] request open:
 * - Android: the passkey appears as a suggestion chip in the keyboard.
 * - iOS: the passkey appears in the QuickType bar above the keyboard. The field is a native `UITextField` with
 *   the username content type, because iOS only offers passkeys for real UIKit username fields.
 * - Other platforms: a plain Material 3 outlined text field; no request is made.
 *
 * Picking a suggestion signs in and calls [onSignedIn]. Dismissing it is not an error: the field keeps offering
 * passkeys, and tapping the field again brings the suggestion back. Losing focus or leaving composition withdraws
 * the offer. Typing a username works as usual through [value] and [onValueChange], so the field also serves the
 * password or "continue with username" path.
 *
 * @param value the text in the field.
 * @param onValueChange called when the user edits the text.
 * @param requestOptions fetches fresh WebAuthn request options JSON from your server (usually with an empty
 *   `allowCredentials`). Called for every new offer; the latest lambda is always used.
 * @param onSignedIn called with the assertion to send to your server.
 * @param modifier applied to the field.
 * @param passkeys the passkeys implementation; defaults to [rememberPasskeys]. Pass a fake in previews and tests.
 * @param label the field label.
 * @param onError called when autofill can't work: [PasskeyError.NotSupported], [PasskeyError.Misconfigured] or
 *   [PasskeyError.InvalidOptions] (reported once, then the field stops offering passkeys), or an unexpected error
 *   such as `requestOptions` throwing (the field retries on the next focus or tap). Dismissals are never reported.
 * @param enabled whether the field can be edited and focused; no passkeys are offered while disabled.
 */
@Composable
public fun PasskeyUsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    requestOptions: suspend () -> String,
    onSignedIn: (PasskeyAssertion) -> Unit,
    modifier: Modifier = Modifier,
    passkeys: Passkeys = rememberPasskeys(),
    label: String = "Username",
    onError: (PasskeyError) -> Unit = {},
    enabled: Boolean = true,
) {
    val currentRequestOptions by rememberUpdatedState(requestOptions)
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    val currentOnError by rememberUpdatedState(onError)
    val controller = remember(passkeys) {
        AutofillController(
            passkeys = passkeys,
            requestOptions = { currentRequestOptions() },
            onSignedIn = { currentOnSignedIn(it) },
            onError = { currentOnError(it) },
        )
    }
    LaunchedEffect(controller, enabled) {
        if (enabled) controller.run()
    }
    PlatformUsernameField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        enabled = enabled,
        modifier = modifier,
        onFocusChanged = controller::onFocusChanged,
        onTap = controller::onTap,
    )
}

/**
 * The platform text field behind [PasskeyUsernameField]. Must report focus changes and taps on the field.
 * iOS hosts a UIKit `UITextField`; the other platforms use [MaterialUsernameField].
 */
@Composable
internal expect fun PlatformUsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onFocusChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
)

/** A Material 3 outlined text field marked as a username field for autofill, reporting focus and taps. */
@Composable
internal fun MaterialUsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onFocusChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnTap by rememberUpdatedState(onTap)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnTap() }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        interactionSource = interactionSource,
        modifier = modifier
            .semantics { contentType = ContentType.Username }
            .onFocusChanged { onFocusChanged(it.isFocused) },
    )
}
