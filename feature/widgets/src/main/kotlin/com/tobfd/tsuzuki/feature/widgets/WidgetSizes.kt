package com.tobfd.tsuzuki.feature.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.SizeMode
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing

/**
 * The three layouts every widget has (docs/DESIGN.md, Widgets). Glance draws each and the launcher
 * shows the largest that fits: one row from 2 × 1, one item with room to breathe from 2 × 2, and the
 * list with a title bar from 3 × 2.
 */
internal object WidgetSizes {
    val Row = DpSize(110.dp, 50.dp)
    val Single = DpSize(110.dp, 110.dp)
    val List = DpSize(200.dp, 130.dp)

    val mode = SizeMode.Responsive(setOf(Row, Single, List))

    /** Rows show their cover from this width on; narrower ones keep the title readable instead. */
    val rowCoverMinWidth = 180.dp

    /** Glance's title bar plus the frame's bottom padding. */
    private val listChromeHeight = TsuzukiSizes.minTouchTarget + TsuzukiSpacing.medium

    /** A row's two title lines and meta line at font scale 1 (titleSmall + labelMedium line heights). */
    private val listRowTextHeight = 56.dp

    /**
     * How many whole rows a list without scrolling shows at [size], at least one. A row is as tall as
     * its cover or, with large fonts (up to 200 %), its text.
     */
    fun listRows(size: DpSize, footer: Boolean, fontScale: Float = 1f): Int {
        val row = maxOf(TsuzukiSizes.widgetCover.height, listRowTextHeight * fontScale) + TsuzukiSpacing.small
        val footerHeight = if (footer) TsuzukiSpacing.extraLarge * fontScale else 0.dp
        val available = size.height - listChromeHeight - footerHeight
        return (available / row).toInt().coerceAtLeast(1)
    }
}

internal enum class WidgetLayout {
    Row,
    Single,
    List;

    companion object {
        /** Wide but low widgets get the row: the single layout is for narrow, square-ish ones. */
        fun of(size: DpSize): WidgetLayout = when {
            size.width >= WidgetSizes.List.width && size.height >= WidgetSizes.List.height -> List
            size.width < WidgetSizes.List.width && size.height >= WidgetSizes.Single.height -> Single
            else -> Row
        }
    }
}

/** How many items a widget keeps at most, so its bitmaps stay within the RemoteViews limit. */
internal const val WIDGET_MAX_ITEMS = 10
