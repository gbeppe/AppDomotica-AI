package com.domopi.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Field for passwords, tokens and API keys. Masked by default, with an explicit "Incolla" button:
 * some keyboards hide clipboard suggestions in password fields, so the system long-press paste is
 * not reliable there. After pasting the clipboard is cleared, so the secret does not linger for
 * other apps. Copying out stays impossible while the value is masked.
 */
@Composable
fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "secret-field",
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row {
                IconButton(enabled = enabled, onClick = { pasteSecret(context)?.let(onValueChange) },
                    modifier = Modifier.testTag("$testTag-paste")) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Incolla")
                }
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Nascondi" else "Mostra")
                }
            }
        },
        modifier = modifier.testTag(testTag),
    )
}

/** Text of the clipboard, trimmed, then the clipboard is cleared (API 28+). */
private fun pasteSecret(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
        ?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { clipboard.clearPrimaryClip() }
    return text
}
