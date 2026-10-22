package com.alirezaiyan.passki.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alirezaiyan.passki.compose.internal.AutofillSignIn
import com.alirezaiyan.passki.compose.internal.PlainAutofillSignIn

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

@Composable
internal actual fun rememberAutofillSignIn(): AutofillSignIn = PlainAutofillSignIn
