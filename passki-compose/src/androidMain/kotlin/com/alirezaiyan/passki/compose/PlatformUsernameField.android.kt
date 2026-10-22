package com.alirezaiyan.passki.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.autofillSignIn
import com.alirezaiyan.passki.compose.internal.AutofillSignIn

@Composable
internal actual fun PlatformUsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onFocusChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    MaterialUsernameField(value, onValueChange, label, enabled, modifier, onFocusChanged, onTap)
}

/**
 * Anchors the autofill request to this composition's View. Inside a `Dialog` or `ModalBottomSheet` that is the
 * dialog window's view, which the activity's `currentFocus` doesn't reach.
 */
@OptIn(InternalPasskiApi::class)
@Composable
internal actual fun rememberAutofillSignIn(): AutofillSignIn {
    val view = LocalView.current
    return remember<AutofillSignIn>(view) { { passkeys, options -> passkeys.autofillSignIn(options, view) } }
}
