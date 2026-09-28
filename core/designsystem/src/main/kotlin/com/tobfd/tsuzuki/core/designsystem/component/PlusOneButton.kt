package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tobfd.tsuzuki.core.designsystem.R
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.ShapeTokens
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing

private const val DISABLED_CONTAINER_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * The signature "+1" button: filled primary, a rounded square at rest that springs into a circle
 * while pressed. Tapping plays a light tick; pass [completesEntry] when this tap reaches the total so
 * it plays the confirm haptic instead.
 *
 * @param dense 40 dp instead of 48 dp, for dense cards. The touch target stays 48 dp.
 */
@Composable
fun PlusOneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dense: Boolean = false,
    completesEntry: Boolean = false,
    contentDescription: String = stringResource(R.string.designsystem_plus_one_description)
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val size = if (dense) TsuzukiSizes.plusOneButtonDense else TsuzukiSizes.plusOneButton
    val cornerRadius by animateDpAsState(
        targetValue = if (pressed) size / 2 else ShapeTokens.medium,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "plusOneCorner"
    )
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = {
            haptics.performHapticFeedback(
                if (completesEntry) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick
            )
            onClick()
        },
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA),
        contentColor = if (enabled) {
            colorScheme.onPrimary
        } else {
            colorScheme.onSurface.copy(
                alpha = DISABLED_CONTENT_ALPHA
            )
        },
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.designsystem_plus_one_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

@ThemePreviews
@Composable
private fun PlusOneButtonPreview() {
    TsuzukiPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
            PlusOneButton(onClick = {})
            PlusOneButton(onClick = {}, dense = true)
            PlusOneButton(onClick = {}, enabled = false)
        }
    }
}
