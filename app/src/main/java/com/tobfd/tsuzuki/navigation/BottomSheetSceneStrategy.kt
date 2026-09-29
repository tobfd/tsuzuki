package com.tobfd.tsuzuki.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Shows entries marked with [bottomSheet] metadata in a `ModalBottomSheet` over the screen below
 * (docs/DESIGN.md, "Bottom sheets"), e.g. the list editor. The sheet is an ordinary back stack
 * entry, so it survives rotation and any feature can open it by its route key. Back and swiping
 * down go through the sheet's own predictive back; popping it any other way slides it down first.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[BOTTOM_SHEET_KEY] != true) return null
        return BottomSheetScene(
            key = last.contentKey,
            entry = last,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            onBack = onBack
        )
    }

    companion object {
        private const val BOTTOM_SHEET_KEY = "tsuzuki.bottomSheet"

        /** Metadata for an entry that shows as a bottom sheet. */
        fun bottomSheet(): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    /** Set while the sheet is shown, so [onRemove] can slide it down before it leaves. */
    private var sheetState: SheetState? = null

    override val content: @Composable () -> Unit = {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        SideEffect { sheetState = state }
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = state,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            entry.Content()
        }
    }

    override suspend fun onRemove() {
        sheetState?.hide()
    }

    override fun equals(other: Any?): Boolean =
        other is BottomSheetScene<*> && key == other.key && entry == other.entry &&
            previousEntries == other.previousEntries

    override fun hashCode(): Int = (key.hashCode() * 31 + entry.hashCode()) * 31 + previousEntries.hashCode()
}
