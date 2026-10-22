package com.alirezaiyan.passki.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.alirezaiyan.passki.compose.internal.AutofillSignIn
import com.alirezaiyan.passki.compose.internal.PlainAutofillSignIn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIFont
import platform.UIKit.UIKeyboardTypeEmailAddress
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextContentTypeUsername
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.UIKit.UITextSpellCheckingType
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

/**
 * iOS hosts a UIKit `UITextField` with `textContentType = .username`: iOS only offers passkeys in the QuickType bar
 * for such fields, and Compose's `ContentType.Username` semantics don't qualify (verified on iOS 26.3). The field
 * reports first-responder changes as focus; `performAutoFillAssistedRequests` needs it to be first responder.
 * Taps are not reported: the core autofill request re-offers passkeys by itself after a dismissal on iOS.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
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
    var focused by remember { mutableStateOf(false) }
    val bridge = remember { UsernameFieldBridge() }
    SideEffect {
        bridge.onValueChange = onValueChange
        bridge.onFocusChanged = {
            focused = it
            onFocusChanged(it)
        }
    }

    val colors = MaterialTheme.colorScheme
    val borderColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        focused -> colors.primary
        else -> colors.outline
    }
    val labelColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        focused -> colors.primary
        else -> colors.onSurfaceVariant
    }
    val textColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
    val tintColor = colors.primary
    // The UIKit view shows through a hole in the Compose canvas, so it needs an opaque background of its own.
    val containerColor = colors.surface

    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(containerColor, OutlinedTextFieldDefaults.shape)
                .border(if (focused) 2.dp else 1.dp, borderColor, OutlinedTextFieldDefaults.shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            UIKitView(
                factory = {
                    UITextField().apply {
                        textContentType = UITextContentTypeUsername
                        autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
                        autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
                        spellCheckingType = UITextSpellCheckingType.UITextSpellCheckingTypeNo
                        keyboardType = UIKeyboardTypeEmailAddress
                        font = UIFont.systemFontOfSize(17.0)
                        accessibilityLabel = label
                        delegate = bridge
                        addTarget(bridge, NSSelectorFromString("editingChanged:"), UIControlEventEditingChanged)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                update = { field ->
                    if ((field.text ?: "") != value) field.text = value
                    field.enabled = enabled
                    field.accessibilityLabel = label
                    field.textColor = textColor.toUIColor()
                    field.tintColor = tintColor.toUIColor()
                    field.backgroundColor = containerColor.toUIColor()
                },
                onRelease = { field ->
                    if (field.isFirstResponder()) field.resignFirstResponder()
                    field.delegate = null
                },
                properties = UIKitInteropProperties(
                    interactionMode = UIKitInteropInteractionMode.NonCooperative,
                    isNativeAccessibilityEnabled = true,
                ),
            )
        }
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())

/** Target-action and delegate for the hosted `UITextField`. Kept alive by `remember` (`delegate` is weak). */
@OptIn(BetaInteropApi::class)
private class UsernameFieldBridge : NSObject(), UITextFieldDelegateProtocol {
    var onValueChange: (String) -> Unit = {}
    var onFocusChanged: (Boolean) -> Unit = {}

    override fun textFieldDidBeginEditing(textField: UITextField) = onFocusChanged(true)

    override fun textFieldDidEndEditing(textField: UITextField) = onFocusChanged(false)

    override fun textFieldShouldReturn(textField: UITextField): Boolean {
        textField.resignFirstResponder()
        return true
    }

    @ObjCAction
    fun editingChanged(sender: UITextField) = onValueChange(sender.text ?: "")
}

@Composable
internal actual fun rememberAutofillSignIn(): AutofillSignIn = PlainAutofillSignIn
