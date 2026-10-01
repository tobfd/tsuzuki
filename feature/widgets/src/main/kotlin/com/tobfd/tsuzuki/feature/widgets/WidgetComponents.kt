package com.tobfd.tsuzuki.feature.widgets

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.theme.ShapeTokens
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTypography
import com.tobfd.tsuzuki.core.ui.coverColorOrNull

/** Text styles of the widgets, sized from the app's type scale (Glance draws the system font). */
internal object WidgetText {
    val title: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = TsuzukiTypography.titleSmall.fontSize,
            fontWeight = FontWeight.Medium
        )

    val body: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = TsuzukiTypography.bodyMedium.fontSize
        )

    val meta: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = TsuzukiTypography.labelMedium.fontSize
        )

    /** The countdown and other numbers that lead a row. */
    val emphasis: TextStyle
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = TsuzukiTypography.titleMedium.fontSize,
            fontWeight = FontWeight.Medium
        )
}

/**
 * The frame of every widget: the widget background with the system's corner radius and, in the list
 * layout, a title bar with the 続 glyph that opens [titleAction]. The one-row layout keeps its
 * padding small so a 2 × 1 widget still fits a 48 dp button.
 */
@Composable
internal fun WidgetFrame(
    layout: WidgetLayout,
    title: String,
    titleAction: Action,
    content: @Composable ColumnScope.() -> Unit
) {
    val showTitle = layout == WidgetLayout.List
    Scaffold(
        titleBar = if (showTitle) {
            {
                TitleBar(
                    startIcon = ImageProvider(TsuzukiIcons.LogoGlyph),
                    title = title,
                    iconColor = GlanceTheme.colors.primary,
                    textColor = GlanceTheme.colors.onSurface,
                    modifier = GlanceModifier.clickable(titleAction)
                )
            }
        } else {
            null
        },
        backgroundColor = GlanceTheme.colors.widgetBackground,
        horizontalPadding = TsuzukiSpacing.medium,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        val vertical = if (layout == WidgetLayout.Row) TsuzukiSpacing.extraSmall else TsuzukiSpacing.medium
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(top = if (showTitle) 0.dp else vertical, bottom = vertical),
            verticalAlignment = if (layout == WidgetLayout.Row) Alignment.CenterVertically else Alignment.Top,
            content = content
        )
    }
}

/** A cover with AniList's `coverImage.color` behind it while there is no image (e.g. offline). */
@Composable
internal fun WidgetCover(bitmap: Bitmap?, coverColor: String?, size: DpSize) {
    val placeholder = coverColorOrNull(coverColor)?.let { ColorProvider(it) } ?: GlanceTheme.colors.surfaceVariant
    Box(
        modifier = GlanceModifier
            .size(size.width, size.height)
            .cornerRadius(ShapeTokens.small)
            .background(placeholder)
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
    }
}

/** A round avatar; the person icon on the surface variant while there is none. */
@Composable
internal fun WidgetAvatar(bitmap: Bitmap?) {
    Box(
        modifier = GlanceModifier
            .size(TsuzukiSizes.widgetAvatar)
            .cornerRadius(TsuzukiSizes.widgetAvatar / 2)
            .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            Image(
                provider = ImageProvider(TsuzukiIcons.Person),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }
}

/**
 * The widget version of the app's +1 button: primary, 48 dp, the medium corner radius (RemoteViews
 * can't morph shapes). [contentDescription] names the title, since several rows show one each.
 */
@Composable
internal fun WidgetPlusOneButton(action: Action, contentDescription: String) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .size(TsuzukiSizes.plusOneButton)
            .cornerRadius(ShapeTokens.medium)
            .background(GlanceTheme.colors.primary)
            .clickable(action)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(com.tobfd.tsuzuki.core.designsystem.R.string.designsystem_plus_one_label),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = TsuzukiTypography.labelLarge.fontSize,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** Empty, logged-out and loading states: a centred message; tapping it runs [action]. */
@Composable
internal fun WidgetMessage(title: String, body: String?, action: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(action).padding(horizontal = TsuzukiSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = WidgetText.title.copy(textAlign = TextAlign.Center), maxLines = 2)
        if (body != null) {
            Spacer(GlanceModifier.height(TsuzukiSpacing.extraSmall))
            Text(text = body, style = WidgetText.meta.copy(textAlign = TextAlign.Center), maxLines = 3)
        }
    }
}

/** A line under the content, e.g. "Offline · updated 14:05", with the cloud-off icon when offline. */
@Composable
internal fun WidgetFooter(text: String, offline: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = TsuzukiSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (offline) {
            Image(
                provider = ImageProvider(TsuzukiIcons.CloudOff),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(TsuzukiSpacing.large)
            )
            Spacer(GlanceModifier.width(TsuzukiSpacing.extraSmall))
        }
        Text(text = text, style = WidgetText.meta, maxLines = 1)
    }
}
