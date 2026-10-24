package com.cadence.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.cadence.player.audio.DjFxProcessor
import com.cadence.player.audio.LiveAudio

/** The two decks share the same setup: DJ effects, then a tap feeding the visualizer. */
object Decks {
    val fxA = DjFxProcessor()
    val fxB = DjFxProcessor()
}

@OptIn(UnstableApi::class)
object PlayerFactory {

    /** Builds a player for deck A (main, owns audio focus) or deck B (the incoming track during a mix). */
    fun create(context: Context, deckB: Boolean): ExoPlayer {
        val fx = if (deckB) Decks.fxB else Decks.fxA
        val tap = if (deckB) LiveAudio.deckB else LiveAudio.deckA
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(fx, TeeAudioProcessor(tap)))
                // Smaller output buffer so faders, EQ and pads respond faster (default is 250–750 ms).
                .setAudioTrackBufferSizeProvider(
                    DefaultAudioTrackBufferSizeProvider.Builder()
                        .setMinPcmBufferDurationUs(100_000)
                        .setMaxPcmBufferDurationUs(220_000)
                        .build()
                )
                .build()
        }
        return ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ !deckB,
            )
            .setHandleAudioBecomingNoisy(!deckB)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }
}
