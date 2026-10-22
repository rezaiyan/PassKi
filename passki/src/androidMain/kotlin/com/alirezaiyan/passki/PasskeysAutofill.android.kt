package com.alirezaiyan.passki

import android.view.View
import com.alirezaiyan.passki.android.AndroidPasskeys

/**
 * `signIn(optionsJson, SignInMode.Autofill)` with the request attached to the focused view inside [anchor] (or
 * [anchor] itself), instead of the focused view PassKi finds on its own.
 *
 * Used by passki-compose's `PasskeyUsernameField`, which passes its own `LocalView`: inside a Compose `Dialog` or
 * `ModalBottomSheet` that is the dialog window's view, which the activity's focus doesn't reach. Other
 * [Passkeys] implementations (fakes, wrappers) get a plain `signIn(optionsJson, SignInMode.Autofill)`.
 */
@InternalPasskiApi
public suspend fun Passkeys.autofillSignIn(optionsJson: String, anchor: View): PasskeyResult<PasskeyAssertion> =
    if (this is AndroidPasskeys) {
        signIn(optionsJson, SignInMode.Autofill, anchor)
    } else {
        signIn(optionsJson, SignInMode.Autofill)
    }
