package `fun`.nightshift.launcher.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import `fun`.nightshift.launcher.client.ui.theme.NsColors

/**
 * Branded text field with consistent colours and rounded shape across all
 * launcher screens.
 */
@Composable
fun NsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            enabled = enabled,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NsColors.Accent,
                unfocusedBorderColor = NsColors.Outline,
                focusedLabelColor = NsColors.Accent,
                unfocusedLabelColor = NsColors.TextSecondary,
                cursorColor = NsColors.Accent,
                focusedTextColor = NsColors.TextPrimary,
                unfocusedTextColor = NsColors.TextPrimary,
                disabledTextColor = NsColors.TextDisabled,
                errorBorderColor = NsColors.Error,
                errorLabelColor = NsColors.Error,
            )
        )
        if (isError && !errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                color = NsColors.Error,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}

/** Branded primary action button with brand purple background. */
@Composable
fun NsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NsColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = NsColors.AccentMuted,
            disabledContentColor = NsColors.TextDisabled,
        ),
    ) {
        Text(if (loading) "..." else text)
    }
}

/** Plain text link button for "forgot password" and similar secondary actions. */
@Composable
fun NsLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = NsColors.Accent)
    }
}

/** Inline status bar shown above the action button on auth screens. */
@Composable
fun NsStatusMessage(message: String?, isError: Boolean = true) {
    if (message.isNullOrBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isError) NsColors.Error.copy(alpha = 0.15f) else NsColors.Success.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(message, color = if (isError) NsColors.Error else NsColors.Success)
    }
}

@Composable
fun NsVerticalSpacer(height: Int) {
    Spacer(modifier = Modifier.height(height.dp))
}

@Composable
fun NsHorizontalSpacer(width: Int) {
    Spacer(modifier = Modifier.width(width.dp))
}
