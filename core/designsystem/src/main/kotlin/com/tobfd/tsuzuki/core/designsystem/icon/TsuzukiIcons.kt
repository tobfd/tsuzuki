package com.tobfd.tsuzuki.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.tobfd.tsuzuki.core.designsystem.R

/**
 * Material Symbols Rounded, 24 dp, weight 400 (Apache 2.0, assets/licenses/material_symbols_LICENSE.txt).
 * Use with `painterResource`. Add new icons from google/material-design-icons
 * (`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`) without the `android:tint` line.
 */
object TsuzukiIcons {
    @DrawableRes val CloudOff: Int = R.drawable.ic_cloud_off

    @DrawableRes val Inbox: Int = R.drawable.ic_inbox

    @DrawableRes val Info: Int = R.drawable.ic_info

    /** The 続 logo glyph (not a Material Symbol); use through `TsuzukiLogo`. */
    @DrawableRes val LogoGlyph: Int = R.drawable.ic_logo_glyph

    @DrawableRes val Notifications: Int = R.drawable.ic_notifications

    @DrawableRes val SentimentDissatisfied: Int = R.drawable.ic_sentiment_dissatisfied

    @DrawableRes val SentimentNeutral: Int = R.drawable.ic_sentiment_neutral

    @DrawableRes val SentimentSatisfied: Int = R.drawable.ic_sentiment_satisfied
}
