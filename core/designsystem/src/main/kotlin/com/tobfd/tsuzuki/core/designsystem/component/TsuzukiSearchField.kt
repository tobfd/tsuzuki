package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.tobfd.tsuzuki.core.designsystem.R
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview

/**
 * Pill-shaped search field with a leading search icon and a trailing close button (Lists search,
 * Browse).
 *
 * @param onClose the trailing button; null hides it (Browse shows it only while there is text).
 * @param closeLabel what TalkBack says for the trailing button.
 * @param focusOnStart moves the focus (and the keyboard) into the field when it first appears.
 */
@Composable
fun TsuzukiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    closeLabel: String = stringResource(R.string.designsystem_search_close),
    focusOnStart: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(TsuzukiIcons.Search), contentDescription = null) },
        trailingIcon = onClose?.let {
            {
                IconButton(onClick = onClose) {
                    Icon(painterResource(TsuzukiIcons.Close), contentDescription = closeLabel)
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
    if (focusOnStart) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@ThemePreviews
@Composable
private fun TsuzukiSearchFieldPreview() {
    TsuzukiPreview {
        TsuzukiSearchField(query = "fri", onQueryChange = {}, placeholder = "Search your list", onClose = {})
    }
}
