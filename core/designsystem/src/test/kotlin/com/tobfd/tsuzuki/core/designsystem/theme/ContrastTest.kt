package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast of the fixed colors (M12 accessibility pass): text roles need 4.5:1 on the
 * backgrounds they are drawn on, the outline 3:1 (non-text). Dynamic color is left out: Material You
 * builds its schemes from tones that keep these ratios by construction.
 */
class ContrastTest {

    private val schemes = mapOf(
        "AniList blue light" to AniListBlueLightColorScheme,
        "AniList blue dark" to AniListBlueDarkColorScheme,
        "AniList blue pure black" to AniListBlueDarkColorScheme.pureBlack()
    )

    private val textPairs: List<Pair<String, (ColorScheme) -> Pair<Color, Color>>> = listOf(
        "onSurface/surface" to { it.onSurface to it.surface },
        "onSurfaceVariant/surface" to { it.onSurfaceVariant to it.surface },
        "onSurface/surfaceContainer" to { it.onSurface to it.surfaceContainer },
        "onSurfaceVariant/surfaceContainer" to { it.onSurfaceVariant to it.surfaceContainer },
        "onSurfaceVariant/surfaceContainerHighest" to { it.onSurfaceVariant to it.surfaceContainerHighest },
        "primary/surface" to { it.primary to it.surface },
        "primary/surfaceContainer" to { it.primary to it.surfaceContainer },
        "onPrimary/primary" to { it.onPrimary to it.primary },
        "onPrimaryContainer/primaryContainer" to { it.onPrimaryContainer to it.primaryContainer },
        "onSecondaryContainer/secondaryContainer" to { it.onSecondaryContainer to it.secondaryContainer },
        "onTertiaryContainer/tertiaryContainer" to { it.onTertiaryContainer to it.tertiaryContainer },
        "error/surface" to { it.error to it.surface },
        "onErrorContainer/errorContainer" to { it.onErrorContainer to it.errorContainer },
        "inverseOnSurface/inverseSurface" to { it.inverseOnSurface to it.inverseSurface }
    )

    @Test
    fun aniListBlue_textRolesReachAA() {
        schemes.forEach { (name, scheme) ->
            textPairs.forEach { (pair, colors) ->
                val (foreground, background) = colors(scheme)
                assertAtLeast(4.5, foreground, background, "$name $pair")
            }
            assertAtLeast(3.0, scheme.outline, scheme.surface, "$name outline/surface")
        }
    }

    @Test
    fun statusColors_reachAAOnTheirContainersAndStandOutFromTheSurface() {
        listOf(
            Triple("light", LightStatusColors, AniListBlueLightColorScheme.surface),
            Triple("dark", DarkStatusColors, AniListBlueDarkColorScheme.surface)
        ).forEach { (mode, colors, surface) ->
            with(colors) {
                listOf(current, planning, completed, paused, dropped, repeating)
            }.forEachIndexed { i, status ->
                assertAtLeast(4.5, status.onContainer, status.container, "$mode status $i on its container")
                assertAtLeast(3.0, status.color, surface, "$mode status $i on the surface")
            }
        }
    }

    private fun assertAtLeast(minimum: Double, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue("$what: ${"%.2f".format(ratio)} < $minimum", ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }
}
