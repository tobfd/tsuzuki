package com.tobfd.tsuzuki.feature.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiLogo
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.fromRaw
import com.tobfd.tsuzuki.core.ui.UserAvatar
import com.tobfd.tsuzuki.core.ui.coverColorOrNull
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.progressText
import com.tobfd.tsuzuki.core.ui.score.ScoreText
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The share card formats (docs/ROADMAP.md, M6, "Share as image"). */
internal enum class ShareFormat(val ratio: Float) {
    Story(9f / 16f),
    Square(1f)
}

private val CardInk = Color(0xFFF5F5F7)
private val CardBase = Color(0xFF101014)

/**
 * Share button: with an entry on the viewer's list a card image (preview first, 9:16 or 1:1);
 * for guests or media not on the list just the link.
 */
@Composable
internal fun ShareFlow(state: MediaDetailUiState.Content, onDone: () -> Unit) {
    val entry = state.entry
    val viewer = state.viewer
    if (entry == null || viewer == null) {
        val shareLink = rememberShareLink()
        LaunchedEffect(Unit) {
            state.detail.siteUrl?.let { shareLink(state.detail.media.title.userPreferred, it) }
            onDone()
        }
        return
    }
    ShareDialog(state = state, entry = entry, viewer = viewer, onDone = onDone)
}

@Composable
private fun ShareDialog(state: MediaDetailUiState.Content, entry: MediaListEntry, viewer: Viewer, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var format by rememberSaveable { mutableStateOf(ShareFormat.Story) }
    var coverLoaded by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.media_share)

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(TsuzukiSpacing.large)
        ) {
            Column(
                modifier = Modifier.padding(TsuzukiSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.media_share_title), style = MaterialTheme.typography.titleLarge)
                SegmentedToggle(
                    options = persistentListOf(
                        stringResource(R.string.media_share_story),
                        stringResource(R.string.media_share_square)
                    ),
                    selectedIndex = format.ordinal,
                    onSelect = { format = ShareFormat.entries[it] },
                    modifier = Modifier.fillMaxWidth()
                )
                ScaledShareCard(
                    state = state,
                    entry = entry,
                    viewer = viewer,
                    format = format,
                    layer = layer,
                    onCoverLoaded = { coverLoaded = true },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .aspectRatio(format.ratio)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.media_share_cancel)) }
                    Button(
                        enabled = !working && (coverLoaded || state.detail.coverUrl == null),
                        onClick = {
                            working = true
                            scope.launch {
                                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                val uri = withContext(Dispatchers.IO) { writeShareImage(context, bitmap) }
                                context.startActivity(shareImageIntent(uri, state.detail.siteUrl, chooserTitle))
                                working = false
                                onDone()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.media_share))
                    }
                }
            }
        }
    }
}

/** Logical size of the card; the shared image is this size in pixels of the screen's density. */
private val CardWidth = 360.dp

/**
 * The card at its fixed size, scaled down to fit [modifier]'s bounds. [layer] records the card
 * unscaled, so the shared image is always 360 dp wide whatever the preview size.
 */
@Composable
private fun ScaledShareCard(
    state: MediaDetailUiState.Content,
    entry: MediaListEntry,
    viewer: Viewer,
    format: ShareFormat,
    layer: GraphicsLayer?,
    onCoverLoaded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardHeight = CardWidth / format.ratio
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / CardWidth, maxHeight / cardHeight)
        Box(
            modifier = Modifier
                .requiredSize(CardWidth, cardHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (layer == null) {
                        Modifier
                    } else {
                        Modifier.drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                    }
                )
        ) {
            ShareCard(state, entry, viewer, square = format == ShareFormat.Square, onCoverLoaded = onCoverLoaded)
        }
    }
}

/**
 * The card: cover, title, status with progress, the viewer's score, avatar and name, and the
 * Tsuzuki logo, on a gradient from the cover's color. Fixed dark colors, so it looks the same
 * whatever theme the viewer uses.
 */
@Composable
internal fun ShareCard(
    state: MediaDetailUiState.Content,
    entry: MediaListEntry,
    viewer: Viewer,
    square: Boolean,
    onCoverLoaded: () -> Unit
) {
    val media = state.detail.media
    val accent = coverColorOrNull(media.coverColor) ?: MaterialTheme.colorScheme.primary
    val format: ScoreFormat = viewer.options.scoreFormat
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(lerp(accent, CardBase, 0.35f), CardBase)))
            .padding(if (square) 20.dp else 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (square) 10.dp else 16.dp)
        ) {
            // The cover takes the height the text below leaves, keeping 2:3.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = state.detail.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { onCoverLoaded() },
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                )
            }
            Text(
                text = media.title.userPreferred,
                color = CardInk,
                fontSize = if (square) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stringResource(
                    entry.status.labelRes(entry.type)
                )} · ${progressText(entry.progress, media.total)}",
                color = lerp(accent, CardInk, 0.35f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (entry.scoreRaw > 0) {
                ScoreText(
                    score = format.fromRaw(entry.scoreRaw),
                    format = format,
                    color = CardInk,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserAvatar(avatarUrl = viewer.avatarUrl, name = viewer.name, size = 28.dp)
                Text(
                    text = viewer.name,
                    color = CardInk,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TsuzukiLogo(size = 24.dp)
                Text(text = "Tsuzuki", color = CardInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Writes the card to the app's cache (shared through the FileProvider, see the manifest). */
private fun writeShareImage(context: Context, bitmap: Bitmap): Uri {
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(directory, "tsuzuki-card.png")
    // The layer may hand out a hardware bitmap, which can't be compressed directly.
    val software = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    file.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun shareImageIntent(uri: Uri, link: String?, title: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        link?.let { putExtra(Intent.EXTRA_TEXT, it) }
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },
    title
)

/** The share card with a Frieren entry, for the component catalog. */
@androidx.compose.runtime.Composable
fun ShareCardSample(square: Boolean, coverUrl: String?, modifier: Modifier = Modifier) {
    val entry = com.tobfd.tsuzuki.core.ui.PreviewListEntries.frieren
    val viewer = Viewer(
        id = 1,
        name = "tobfd",
        avatarUrl = null,
        options = com.tobfd.tsuzuki.core.model.ViewerOptions(
            titleLanguage = com.tobfd.tsuzuki.core.model.TitleLanguage.ROMAJI,
            staffNameLanguage = com.tobfd.tsuzuki.core.model.StaffNameLanguage.ROMAJI,
            displayAdultContent = false,
            scoreFormat = ScoreFormat.POINT_10_DECIMAL
        )
    )
    val detail = com.tobfd.tsuzuki.core.model.MediaDetail(
        media = entry.media,
        bannerUrl = null,
        coverUrl = coverUrl,
        descriptionHtml = null,
        genres = emptyList(),
        tags = emptyList(),
        info = com.tobfd.tsuzuki.core.model.MediaInfo(null, null, null, null, null, emptyList(), null, null, null),
        isFavourite = false,
        siteUrl = null,
        rankings = emptyList(),
        streamingLinks = emptyList(),
        trailer = null,
        relations = emptyList(),
        characters = emptyList(),
        staff = emptyList(),
        statusDistribution = emptyMap(),
        scoreDistribution = emptyList(),
        recommendations = emptyList(),
        following = emptyList()
    )
    val format = if (square) ShareFormat.Square else ShareFormat.Story
    ScaledShareCard(
        state = MediaDetailUiState.Content(detail = detail, entry = entry, viewer = viewer, isFavourite = false),
        entry = entry,
        viewer = viewer,
        format = format,
        layer = null,
        onCoverLoaded = {},
        modifier = modifier.aspectRatio(format.ratio)
    )
}
