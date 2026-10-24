package com.cadence.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.cadence.player.data.AppStore
import com.cadence.player.playback.ArtFetcher
import com.cadence.player.playback.ArtKeyer
import com.cadence.player.playback.AudioEffects

class CadenceApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppStore.init(this)
        AudioEffects.init(this)
        com.cadence.player.audio.AnalysisManager.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtFetcher.Factory())
            add(ArtKeyer())
        }
        .crossfade(220)
        .build()
}
