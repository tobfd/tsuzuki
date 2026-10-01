package com.tobfd.tsuzuki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.component.ColorChoiceCard
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.component.colorSourceSwatches
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.centeredMaxWidth
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.ui.UserAvatar
import com.tobfd.tsuzuki.core.ui.message
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/** App and AniList settings, opened from the Profile tab. */
@Serializable
data object SettingsRoute : NavKey

/** The title languages the setting offers; AniList's stylised variants show as their plain language. */
private val TitleLanguages = listOf(TitleLanguage.ROMAJI, TitleLanguage.ENGLISH, TitleLanguage.NATIVE)

/**
 * Settings (docs/DESIGN.md, Settings). [onLogOut] also leads guests to the login screen, like every
 * "Log in" in the app. [onOpenCatalog] is set in debug builds only: tapping the version
 * [CATALOG_TAPS] times opens the component catalog.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogOut: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: (() -> Unit)? = null
) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languageController = rememberAppLanguageController()
    SettingsContent(
        state = state,
        events = viewModel.eventFlow,
        appVersion = rememberAppVersion(),
        appLanguage = languageController?.current,
        onBack = onBack,
        actions = SettingsActions(
            onColorsChange = viewModel::onColorsChange,
            onThemeModeChange = viewModel::onThemeModeChange,
            onPureBlackChange = viewModel::onPureBlackChange,
            onAppLanguageChange = { languageController?.set(it) },
            onTitleLanguageChange = viewModel::onTitleLanguageChange,
            onScoreFormatChange = viewModel::onScoreFormatChange,
            onAdultContentChange = viewModel::onAdultContentChange,
            onLogOut = onLogOut,
            onOpenLicenses = onOpenLicenses,
            onOpenCatalog = onOpenCatalog
        ),
        modifier = modifier
    )
}

/** Everything the settings screen can do, so the content stays previewable. */
internal class SettingsActions(
    val onColorsChange: (AppColors) -> Unit = {},
    val onThemeModeChange: (AppThemeMode) -> Unit = {},
    val onPureBlackChange: (Boolean) -> Unit = {},
    val onAppLanguageChange: (AppLanguage) -> Unit = {},
    val onTitleLanguageChange: (TitleLanguage) -> Unit = {},
    val onScoreFormatChange: (ScoreFormat) -> Unit = {},
    val onAdultContentChange: (Boolean) -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onOpenCatalog: (() -> Unit)? = null
)

/** Taps on the version that open the debug catalog. */
internal const val CATALOG_TAPS = 7

@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    events: Flow<SettingsEvent>,
    appVersion: String,
    appLanguage: AppLanguage?,
    onBack: () -> Unit,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SettingsEvent.SaveFailed -> failure = event.error
            }
        }
    }
    failure?.let { error ->
        val message = stringResource(R.string.settings_save_failed, error.message())
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            failure = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TsuzukiBackTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = TsuzukiSpacing.large)
        ) {
            Column(modifier = Modifier.centeredMaxWidth()) {
                AppearanceSection(state, actions)
                LanguageSection(state, appLanguage, actions)
                if (state.options != null) ContentSection(state, actions)
                AccountSection(state.viewer, actions.onLogOut)
                AboutSection(appVersion, actions.onOpenLicenses, actions.onOpenCatalog)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TsuzukiSpacing.screenMargin,
                end = TsuzukiSpacing.screenMargin,
                top = TsuzukiSpacing.extraLarge,
                bottom = TsuzukiSpacing.small
            )
            .semantics { heading() }
    )
    Column(content = content)
}

@Composable
private fun AppearanceSection(state: SettingsUiState, actions: SettingsActions) {
    val appearance = state.appearance
    // Swatches in the mode the app shows right now.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SettingsSection(stringResource(R.string.settings_section_appearance)) {
        Text(
            text = stringResource(R.string.settings_colors),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            ColorChoiceCard(
                label = stringResource(R.string.settings_colors_material_you),
                swatches = colorSourceSwatches(ColorSource.Dynamic, darkTheme),
                selected = appearance.colors == AppColors.MaterialYou,
                onClick = { actions.onColorsChange(AppColors.MaterialYou) },
                modifier = Modifier.weight(1f)
            )
            ColorChoiceCard(
                label = stringResource(R.string.settings_colors_anilist_blue),
                swatches = colorSourceSwatches(ColorSource.AniListBlue, darkTheme),
                selected = appearance.colors == AppColors.AniListBlue,
                onClick = { actions.onColorsChange(AppColors.AniListBlue) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(
                start = TsuzukiSpacing.screenMargin,
                end = TsuzukiSpacing.screenMargin,
                top = TsuzukiSpacing.small
            )
        )
        SegmentedToggle(
            options = persistentListOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark)
            ),
            selectedIndex = appearance.themeMode.ordinal,
            onSelect = { actions.onThemeModeChange(AppThemeMode.entries[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.small)
        )
        SwitchRow(
            title = stringResource(R.string.settings_pure_black),
            supporting = stringResource(R.string.settings_pure_black_summary),
            checked = appearance.pureBlack,
            onCheckedChange = actions.onPureBlackChange
        )
    }
}

@Composable
private fun LanguageSection(state: SettingsUiState, appLanguage: AppLanguage?, actions: SettingsActions) {
    val options = state.options
    if (appLanguage == null && options == null) return
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_section_language)) {
        if (appLanguage != null) {
            ClickableRow(
                title = stringResource(R.string.settings_app_language),
                supporting = stringResource(appLanguage.labelRes()),
                onClick = { choosingLanguage = true }
            )
        }
        if (options != null) {
            val selected = TitleLanguages.indexOf(options.titleLanguage.plain())
            Text(
                text = stringResource(R.string.settings_title_language),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(
                    start = TsuzukiSpacing.screenMargin,
                    end = TsuzukiSpacing.screenMargin,
                    top = TsuzukiSpacing.small
                )
            )
            SegmentedToggle(
                options = TitleLanguages.map { stringResource(it.labelRes()) }.toImmutableList(),
                selectedIndex = selected,
                onSelect = { index ->
                    if (index != selected && !state.savingOptions) actions.onTitleLanguageChange(TitleLanguages[index])
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.small)
            )
            Text(
                text = stringResource(
                    R.string.settings_title_language_example,
                    stringResource(options.titleLanguage.plain().exampleRes())
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin)
            )
            Text(
                text = stringResource(R.string.settings_synced_with_anilist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = TsuzukiSpacing.screenMargin,
                    vertical = TsuzukiSpacing.extraSmall
                )
            )
        }
    }
    if (choosingLanguage && appLanguage != null) {
        ChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = AppLanguage.entries.toImmutableList(),
            selected = appLanguage,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                choosingLanguage = false
                if (it != appLanguage) actions.onAppLanguageChange(it)
            },
            onDismiss = { choosingLanguage = false }
        )
    }
}

@Composable
private fun ContentSection(state: SettingsUiState, actions: SettingsActions) {
    val options = state.options ?: return
    var choosingFormat by rememberSaveable { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_section_content)) {
        ClickableRow(
            title = stringResource(R.string.settings_score_format),
            supporting = stringResource(options.scoreFormat.labelRes()),
            enabled = !state.savingOptions,
            onClick = { choosingFormat = true }
        )
        SwitchRow(
            title = stringResource(R.string.settings_adult_content),
            supporting = stringResource(
                if (options.displayAdultContent) {
                    R.string.settings_adult_content_summary_on
                } else {
                    R.string.settings_adult_content_summary
                }
            ),
            checked = options.displayAdultContent,
            enabled = !state.savingOptions,
            onCheckedChange = actions.onAdultContentChange
        )
        Text(
            text = stringResource(R.string.settings_synced_with_anilist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin)
        )
    }
    if (choosingFormat) {
        ChoiceDialog(
            title = stringResource(R.string.settings_score_format),
            options = ScoreFormat.entries.toImmutableList(),
            selected = options.scoreFormat,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                choosingFormat = false
                if (it != options.scoreFormat) actions.onScoreFormatChange(it)
            },
            onDismiss = { choosingFormat = false }
        )
    }
}

@Composable
private fun AccountSection(viewer: Viewer?, onLogOut: () -> Unit) {
    var confirmingLogOut by rememberSaveable { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_section_account)) {
        if (viewer != null) {
            ListItem(
                headlineContent = { Text(viewer.name) },
                supportingContent = { Text(stringResource(R.string.settings_logged_in_with_anilist)) },
                leadingContent = {
                    UserAvatar(avatarUrl = viewer.avatarUrl, name = viewer.name, size = TsuzukiSizes.minTouchTarget)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
            OutlinedButton(
                onClick = { confirmingLogOut = true },
                modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin)
            ) {
                Icon(painter = painterResource(TsuzukiIcons.Logout), contentDescription = null)
                Text(
                    text = stringResource(R.string.settings_log_out),
                    modifier = Modifier.padding(start = TsuzukiSpacing.small)
                )
            }
        } else {
            Text(
                text = stringResource(R.string.settings_guest),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin)
            )
            Button(
                onClick = onLogOut,
                modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.small)
            ) {
                Text(stringResource(R.string.settings_log_in))
            }
        }
    }
    if (confirmingLogOut) {
        AlertDialog(
            onDismissRequest = { confirmingLogOut = false },
            title = { Text(stringResource(R.string.settings_log_out_title)) },
            text = { Text(stringResource(R.string.settings_log_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingLogOut = false
                    onLogOut()
                }) { Text(stringResource(R.string.settings_log_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLogOut = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
}

@Composable
private fun AboutSection(appVersion: String, onOpenLicenses: () -> Unit, onOpenCatalog: (() -> Unit)?) {
    SettingsSection(stringResource(R.string.settings_section_about)) {
        var taps by remember { mutableIntStateOf(0) }
        val hiddenCatalog = if (onOpenCatalog != null) {
            Modifier.clickable(interactionSource = null, indication = null) {
                taps += 1
                if (taps >= CATALOG_TAPS) {
                    taps = 0
                    onOpenCatalog()
                }
            }
        } else {
            Modifier
        }
        Text(
            text = stringResource(R.string.settings_about_app, appVersion),
            style = MaterialTheme.typography.bodyLarge,
            modifier = hiddenCatalog.padding(horizontal = TsuzukiSpacing.screenMargin)
        )
        Text(
            text = stringResource(R.string.settings_about_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.extraSmall)
        )
        HorizontalDivider(modifier = Modifier.padding(top = TsuzukiSpacing.small))
        ClickableRow(title = stringResource(R.string.settings_licenses), supporting = null, onClick = onOpenLicenses)
    }
}

@Composable
private fun ClickableRow(title: String, supporting: String?, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.selectable(
            selected = checked,
            enabled = enabled,
            role = Role.Switch,
            onClick = { onCheckedChange(!checked) }
        )
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: ImmutableList<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            )
                            .padding(vertical = TsuzukiSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(text = label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

/** AniList's stylised title languages count as their plain language here. */
internal fun TitleLanguage.plain(): TitleLanguage = when (this) {
    TitleLanguage.ROMAJI_STYLISED -> TitleLanguage.ROMAJI
    TitleLanguage.ENGLISH_STYLISED -> TitleLanguage.ENGLISH
    TitleLanguage.NATIVE_STYLISED -> TitleLanguage.NATIVE
    else -> this
}

private fun TitleLanguage.labelRes(): Int = when (plain()) {
    TitleLanguage.ENGLISH -> R.string.settings_title_language_english
    TitleLanguage.NATIVE -> R.string.settings_title_language_native
    else -> R.string.settings_title_language_romaji
}

private fun TitleLanguage.exampleRes(): Int = when (plain()) {
    TitleLanguage.ENGLISH -> R.string.settings_title_example_english
    TitleLanguage.NATIVE -> R.string.settings_title_example_native
    else -> R.string.settings_title_example_romaji
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.System -> R.string.settings_app_language_system
    AppLanguage.English -> R.string.settings_app_language_english
    AppLanguage.German -> R.string.settings_app_language_german
}

private fun ScoreFormat.labelRes(): Int = when (this) {
    ScoreFormat.POINT_100 -> R.string.settings_score_format_100
    ScoreFormat.POINT_10_DECIMAL -> R.string.settings_score_format_10_decimal
    ScoreFormat.POINT_10 -> R.string.settings_score_format_10
    ScoreFormat.POINT_5 -> R.string.settings_score_format_5
    ScoreFormat.POINT_3 -> R.string.settings_score_format_3
}

@ThemePreviews
@Composable
private fun SettingsContentPreview() {
    val viewer = Viewer(
        id = 1,
        name = "tobfd",
        avatarUrl = null,
        options = com.tobfd.tsuzuki.core.model.ViewerOptions(
            titleLanguage = TitleLanguage.ROMAJI,
            staffNameLanguage = com.tobfd.tsuzuki.core.model.StaffNameLanguage.ROMAJI_WESTERN,
            displayAdultContent = false,
            scoreFormat = ScoreFormat.POINT_10_DECIMAL
        )
    )
    TsuzukiPreview {
        SettingsContent(
            state = SettingsUiState(viewer = viewer, options = viewer.options),
            events = emptyFlow(),
            appVersion = "0.1.0",
            appLanguage = AppLanguage.System,
            onBack = {},
            actions = SettingsActions()
        )
    }
}

@ThemePreviews
@Composable
private fun SettingsGuestPreview() {
    TsuzukiPreview {
        SettingsContent(
            state = SettingsUiState(),
            events = emptyFlow(),
            appVersion = "0.1.0",
            appLanguage = null,
            onBack = {},
            actions = SettingsActions()
        )
    }
}
