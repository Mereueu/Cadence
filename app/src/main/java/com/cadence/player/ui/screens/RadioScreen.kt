package com.cadence.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.cadence.player.MusicViewModel
import com.cadence.player.data.RadioRepository
import com.cadence.player.data.RadioSource
import com.cadence.player.data.RadioStation
import com.cadence.player.ui.LocalActions
import com.cadence.player.ui.components.MinimalButton
import com.cadence.player.ui.components.PlayingBars
import com.cadence.player.ui.components.normalizeForSearch
import com.cadence.player.ui.theme.CadenceColors
import com.cadence.player.ui.theme.LineIcons
import kotlinx.coroutines.delay

/** Station lists are kept for the session so switching tabs does not re-download them. */
private object RadioCache {
    var di: List<RadioStation>? = null
    var soma: List<RadioStation>? = null
    val world = HashMap<String, List<RadioStation>>()
}

private data class Loaded(val loading: Boolean = true, val items: List<RadioStation> = emptyList(), val error: String? = null)

@Composable
fun RadioScreen(vm: MusicViewModel) {
    val actions = LocalActions.current
    var source by rememberSaveable { mutableStateOf(RadioSource.DIFM) }
    val message by vm.radioMessages.collectAsStateWithLifecycle()
    val state by vm.player.state.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            actions.showMessage(it)
            vm.consumeRadioMessage()
        }
    }
    LaunchedEffect(state.error, state.isRadio) {
        val err = state.error
        if (state.isRadio && err != null) {
            actions.showMessage(
                if (source == RadioSource.DIFM) "DI.fm refused the stream. Check that your listen key is correct and Premium is active."
                else "This station isn't responding right now. Try another one."
            )
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Radio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            RadioSource.entries.forEach { s ->
                val selected = s == source
                Column(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { source = s }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CadenceColors.TextPrimary else Color(0xFF6E6E6E),
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(14.dp).height(1.5.dp).background(if (selected) CadenceColors.TextPrimary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F1F)))

        when (source) {
            RadioSource.DIFM -> DiPage(vm)
            RadioSource.SOMAFM -> SomaPage(vm)
            RadioSource.WORLD -> WorldPage(vm)
        }
    }
}

// ------------------------------------------------------------------------------------- DI.fm

@Composable
private fun DiPage(vm: MusicViewModel) {
    val actions = LocalActions.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(RadioCache.di?.let { Loaded(false, it) } ?: Loaded()) }
    var filter by rememberSaveable { mutableStateOf("") }
    var editingKey by rememberSaveable { mutableStateOf(false) }
    var keyDraft by rememberSaveable { mutableStateOf("") }
    val hasKey = settings.diListenKey.isNotBlank()

    LaunchedEffect(reload) {
        if (RadioCache.di != null && reload == 0) return@LaunchedEffect
        loaded = Loaded(true)
        loaded = try {
            Loaded(false, RadioRepository.diChannels().also { RadioCache.di = it })
        } catch (e: Exception) {
            Loaded(false, error = "Couldn't load DI.fm channels. Check your internet connection.")
        }
    }

    val shown = remember(loaded, filter) {
        val q = normalizeForSearch(filter)
        if (q.isBlank()) loaded.items else loaded.items.filter { normalizeForSearch(it.name).contains(q) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "key") {
            if (!hasKey || editingKey) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .border(1.dp, CadenceColors.Outline, RoundedCornerShape(6.dp))
                        .padding(16.dp),
                ) {
                    Text("DI.fm Premium listen key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "DI.fm lets other apps play its channels with a Premium account. Paste the listen key from di.fm → Settings → Listen key.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Light,
                        color = CadenceColors.TextSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    MinimalField(keyDraft, { keyDraft = it.trim() }, "Listen key")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MinimalButton("Save key", null, {
                            if (keyDraft.length >= 8) {
                                vm.updateSettings { it.copy(diListenKey = keyDraft) }
                                editingKey = false
                                actions.showMessage("Listen key saved")
                            } else {
                                actions.showMessage("That doesn't look like a listen key")
                            }
                        }, primary = true)
                        MinimalButton("Find my key", null, {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.di.fm/settings")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        })
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Premium listen key saved",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Light,
                        color = CadenceColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "CHANGE",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                keyDraft = settings.diListenKey
                                editingKey = true
                            }
                            .padding(8.dp),
                    )
                }
            }
        }
        item(key = "filter") {
            MinimalField(filter, { filter = it }, "Search DI.fm channels…", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        stationListBody(
            vm = vm,
            loaded = loaded,
            shown = shown,
            onRetry = { reload++ },
            onPlay = { st ->
                if (settings.diListenKey.isBlank()) {
                    actions.showMessage("Add your DI.fm listen key first")
                    editingKey = true
                } else {
                    vm.playRadio(st, shown)
                }
            },
        )
    }
}

// ------------------------------------------------------------------------------------- SomaFM

@Composable
private fun SomaPage(vm: MusicViewModel) {
    var reload by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(RadioCache.soma?.let { Loaded(false, it) } ?: Loaded()) }
    LaunchedEffect(reload) {
        if (RadioCache.soma != null && reload == 0) return@LaunchedEffect
        loaded = Loaded(true)
        loaded = try {
            Loaded(false, RadioRepository.somaChannels().also { RadioCache.soma = it })
        } catch (e: Exception) {
            Loaded(false, error = "Couldn't load SomaFM stations. Check your internet connection.")
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "about") {
            Text(
                "Commercial-free, listener-supported radio from San Francisco. Free to listen; support them at somafm.com.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                color = CadenceColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        stationListBody(vm, loaded, loaded.items, onRetry = { reload++ }, onPlay = { vm.playRadio(it, loaded.items) })
    }
}

// ------------------------------------------------------------------------------------- World

private val WorldTags = listOf(
    "Top" to null,
    "Electronic" to "electronic",
    "House" to "house",
    "Techno" to "techno",
    "Trance" to "trance",
    "Drum & bass" to "drum and bass",
    "Ambient" to "ambient",
    "Chillout" to "chillout",
    "Lounge" to "lounge",
    "Hip-hop" to "hip-hop",
    "Jazz" to "jazz",
    "Rock" to "rock",
    "Pop" to "pop",
    "Romania" to "country:RO",
)

@Composable
private fun WorldPage(vm: MusicViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var tagIndex by rememberSaveable { mutableIntStateOf(1) }
    var reload by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(Loaded()) }
    val tag = WorldTags[tagIndex].second

    LaunchedEffect(query, tagIndex, reload) {
        val cacheKey = "$query|$tag"
        RadioCache.world[cacheKey]?.let {
            loaded = Loaded(false, it)
            return@LaunchedEffect
        }
        loaded = loaded.copy(loading = true, error = null)
        if (query.isNotBlank()) delay(450)
        loaded = try {
            val country = tag?.takeIf { it.startsWith("country:") }?.removePrefix("country:")
            val list = RadioRepository.worldStations(
                query = query,
                tag = tag?.takeIf { !it.startsWith("country:") }?.takeIf { query.isBlank() },
                countryCode = country,
            )
            RadioCache.world[cacheKey] = list
            Loaded(false, list)
        } catch (e: Exception) {
            Loaded(false, error = "Couldn't reach the station directory. Check your internet connection.")
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "search") {
            MinimalField(query, { query = it }, "Search stations worldwide…", Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
        item(key = "tags") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorldTags.forEachIndexed { i, (label, _) ->
                    val selected = i == tagIndex && query.isBlank()
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CadenceColors.Background else CadenceColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) CadenceColors.TextPrimary else Color.Transparent)
                            .border(1.dp, if (selected) CadenceColors.TextPrimary else CadenceColors.Outline, RoundedCornerShape(50))
                            .clickable {
                                query = ""
                                tagIndex = i
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        stationListBody(vm, loaded, loaded.items, onRetry = { reload++ }, onPlay = { vm.playRadio(it, loaded.items) })
    }
}

// ------------------------------------------------------------------------------------- Shared

private fun androidx.compose.foundation.lazy.LazyListScope.stationListBody(
    vm: MusicViewModel,
    loaded: Loaded,
    shown: List<RadioStation>,
    onRetry: () -> Unit,
    onPlay: (RadioStation) -> Unit,
) {
    when {
        loaded.loading && shown.isEmpty() -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CadenceColors.TextPrimary, strokeWidth = 1.dp)
            }
        }
        loaded.error != null -> item(key = "error") {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loaded.error ?: "", color = CadenceColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                MinimalButton("Try again", null, onRetry, primary = true)
            }
        }
        shown.isEmpty() -> item(key = "none") {
            Text(
                "No stations found.",
                color = CadenceColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
        else -> items(shown, key = { it.id }) { station ->
            StationRow(vm, station, onPlay)
        }
    }
}

@Composable
private fun StationRow(vm: MusicViewModel, station: RadioStation, onPlay: (RadioStation) -> Unit) {
    val current by vm.currentRadio.collectAsStateWithLifecycle()
    val state by vm.player.state.collectAsStateWithLifecycle()
    val loading by vm.radioLoading.collectAsStateWithLifecycle()
    val isCurrent = current?.id == station.id
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .then(if (isCurrent) Modifier.border(1.dp, CadenceColors.TextPrimary, RoundedCornerShape(6.dp)) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onPlay(station) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationArt(station, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isCurrent) state.streamTitle?.takeIf { it != station.name } ?: station.subtitle else station.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = CadenceColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when {
                loading == station.id -> CircularProgressIndicator(Modifier.size(18.dp), color = CadenceColors.TextPrimary, strokeWidth = 1.dp)
                isCurrent -> PlayingBars(state.isPlaying, CadenceColors.TextPrimary)
                else -> Icon(LineIcons.Play, contentDescription = "Play ${station.name}", tint = Color(0xFF6E6E6E), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Station logo, or a generated monogram tile when the station has none. */
@Composable
fun StationArt(station: RadioStation, modifier: Modifier = Modifier, corner: Dp = 2.dp, textSize: TextUnit = 18.sp) {
    val hue = ((station.name.hashCode() and 0x7fffffff) % 360).toFloat()
    val c1 = Color.hsl(hue, 0.45f, 0.32f)
    val c2 = Color.hsl((hue + 40f) % 360f, 0.5f, 0.14f)
    val initials = station.name
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "♪" }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White.copy(alpha = 0.9f), fontSize = textSize, fontWeight = FontWeight.Light)
        if (!station.imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = station.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = {},
            )
        }
    }
}

/** Hairline-underlined text field of the Minimal theme. */
@Composable
private fun MinimalField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Light, color = CadenceColors.TextTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = CadenceColors.TextPrimary),
                cursorBrush = SolidColor(CadenceColors.TextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CadenceColors.Outline))
    }
}
