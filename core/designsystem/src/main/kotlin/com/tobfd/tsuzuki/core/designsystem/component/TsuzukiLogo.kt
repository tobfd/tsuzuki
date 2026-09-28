package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes

/**
 * The 続 logo tile from the login screen: primaryContainer with a 28 dp radius (extraLarge) and the
 * glyph in onPrimaryContainer. Decorative; the screen title names the app.
 */
@Composable
fun TsuzukiLogo(modifier: Modifier = Modifier, size: Dp = TsuzukiSizes.logoTile) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            painter = painterResource(TsuzukiIcons.LogoGlyph),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@ThemePreviews
@Composable
private fun TsuzukiLogoPreview() {
    TsuzukiPreview {
        TsuzukiLogo()
    }
}
