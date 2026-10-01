package com.tobfd.tsuzuki.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.common.Dispatcher
import com.tobfd.tsuzuki.core.common.TsuzukiDispatchers
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.centeredMaxWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** The open-source licenses of what the app ships, opened from Settings. */
@Serializable
data object LicensesRoute : NavKey

/** A license and where its full text lies in the assets. */
enum class OpenSourceLicense(val title: String, val asset: String) {
    // The app's own license; the asset is a copy of LICENSE in the repository root.
    Gpl3("GNU General Public License v3.0", "licenses/tsuzuki_GPL3.txt"),

    // The Material Symbols license file is the full Apache License 2.0 text.
    Apache2("Apache License 2.0", "licenses/material_symbols_LICENSE.txt"),
    Mit("MIT License", "licenses/apollo_kotlin_MIT.txt"),
    Bsd3("BSD 3-Clause License", "licenses/protobuf_BSD3.txt"),
    Ofl("SIL Open Font License 1.1", "licenses/google_sans_flex_OFL.txt")
}

/**
 * Libraries and assets the app ships, by license, as found on the release runtime classpath
 * (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`). Test-only libraries are left out.
 */
internal val OpenSourceLibraries: Map<OpenSourceLicense, List<String>> = mapOf(
    OpenSourceLicense.Gpl3 to listOf("Tsuzuki"),
    OpenSourceLicense.Apache2 to listOf(
        "AndroidX (Activity, Browser, Core, DataStore, Glance, Hilt, Lifecycle, Navigation 3, Paging, ProfileInstaller, Room, SQLite, WorkManager)",
        "Jetpack Compose and Material 3",
        "Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx.collections.immutable",
        "Dagger and Hilt",
        "OkHttp and Okio",
        "Coil and Accompanist Drawable Painter",
        "SQLDelight",
        "Tink",
        "Gson",
        "Guava ListenableFuture",
        "Annotations (JetBrains, JSpecify, Error Prone, JSR 305, javax.inject, Jakarta Inject)",
        "Material Symbols"
    ),
    OpenSourceLicense.Mit to listOf("Apollo Kotlin", "Apollo Kotlin normalized cache", "uuid (Ben Asher)"),
    OpenSourceLicense.Bsd3 to listOf("Protocol Buffers (bundled in Tink)"),
    OpenSourceLicense.Ofl to listOf("Google Sans Flex", "Noto Sans JP (the 続 glyph in the app icon)")
)

@HiltViewModel
class LicensesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(TsuzukiDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val texts = MutableStateFlow<ImmutableMap<OpenSourceLicense, String>>(persistentMapOf())

    /** The full texts opened so far. */
    val openTexts: StateFlow<ImmutableMap<OpenSourceLicense, String>> = texts.asStateFlow()

    fun onToggle(license: OpenSourceLicense) {
        if (license in texts.value) {
            texts.update { (it - license).toImmutableMap() }
            return
        }
        viewModelScope.launch {
            val text = withContext(ioDispatcher) {
                context.assets.open(license.asset).bufferedReader().use { it.readText() }
            }
            texts.update { (it + (license to text)).toImmutableMap() }
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<LicensesViewModel>()
    val openTexts by viewModel.openTexts.collectAsStateWithLifecycle()
    LicensesContent(openTexts = openTexts, onToggle = viewModel::onToggle, onBack = onBack, modifier = modifier)
}

@Composable
internal fun LicensesContent(
    openTexts: ImmutableMap<OpenSourceLicense, String>,
    onToggle: (OpenSourceLicense) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TsuzukiBackTopBar(title = stringResource(R.string.settings_licenses), onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.large
            ),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
        ) {
            OpenSourceLibraries.forEach { (license, libraries) ->
                item(key = "title-${license.name}") {
                    Text(
                        text = license.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .centeredMaxWidth()
                            .fillMaxWidth()
                            .padding(
                                start = TsuzukiSpacing.screenMargin,
                                end = TsuzukiSpacing.screenMargin,
                                top = TsuzukiSpacing.large
                            )
                            .semantics { heading() }
                    )
                }
                items(libraries, key = { "${license.name}-$it" }) { library ->
                    Text(
                        text = library,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .centeredMaxWidth()
                            .fillMaxWidth()
                            .padding(horizontal = TsuzukiSpacing.screenMargin)
                    )
                }
                item(key = "text-${license.name}") {
                    val text = openTexts[license]
                    TextButton(
                        onClick = { onToggle(license) },
                        modifier = Modifier
                            .centeredMaxWidth()
                            .padding(horizontal = TsuzukiSpacing.small)
                    ) {
                        Text(
                            stringResource(
                                if (text == null) R.string.settings_license_show else R.string.settings_license_hide
                            )
                        )
                    }
                    if (text != null) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .centeredMaxWidth()
                                .padding(horizontal = TsuzukiSpacing.screenMargin)
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun LicensesContentPreview() {
    TsuzukiPreview {
        LicensesContent(
            openTexts = persistentMapOf(
                OpenSourceLicense.Mit to "The MIT License (MIT)\n\nCopyright (c) Apollo Graph, Inc."
            ),
            onToggle = {},
            onBack = {}
        )
    }
}
