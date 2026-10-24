# Cadence

An offline music player and DJ app for Android, built with Jetpack Compose and Media3.
Your music never leaves your phone. The app has no ads, no account and no tracking.

**Current version: 1.6.0**

---

## Contents
- [Music player](#music-player)
- [DJ decks](#dj-decks)
- [Auto DJ](#auto-dj)
- [Listening modes](#listening-modes)
- [Visualizer](#visualizer)
- [Radio](#radio)
- [Make it yours](#make-it-yours)
- [Android Auto](#android-auto)
- [Get the APK](#get-the-apk)
- [Community themes](#community-themes)
- [Build and signing](#build-and-signing)

---

## Music player
- **Library:** songs, albums, artists, genres and a folder tree, with sorting and a fast-scroll thumb.
- **Now playing:** a modular player screen (see [Make it yours](#make-it-yours)), precise seek bar, song info, and favourites.
- **Equalizer:** a 5+ band EQ with a live curve, 12 presets, bass boost, surround (virtualizer) and loudness.
- **Playlists:** create, rename, delete and drag to reorder; add songs with a searchable picker; save the queue as a playlist.
- **Smart lists:** Favorites, Recently added, Recently played, Most played and Not played.
- **Queue:** drag to reorder, remove songs, or jump to any song. You can sort it by title, artist, album, length, random or **harmonic order** (by key and tempo).
- **Search** across songs, albums and artists. It ignores accents.
- **Other playback tools:** a sleep timer with fade-out, playback speed, play next and add to queue.
- **Volume levelling:** optional loudness normalisation keeps every song at a similar volume.
- **System controls:** notification, lock-screen and Bluetooth controls. The app resumes your last queue.
- **Formats:** plays MP3, AAC/M4A, **FLAC**, **WAV**, OGG, Opus, AIFF and more. It also handles "Open with" from file managers.

## DJ decks
A full-screen, landscape two-deck setup in a **Neon Club** style. Open it from **More → DJ decks**.

| Section | What you get |
|---|---|
| Decks | Jog wheels with spinning artwork and a position ring. Turn a jog while playing to nudge the tempo; turn it while paused to scrub. Play, CUE (CDJ-style), pitch fader (±4 / 8 / 16 %) and SYNC (tempo + beat phase) |
| Pads | **Hot cues** (4 per deck, saved per song, set automatically from the song's sections), **loop rolls** (¼ to 2 beats) and **beat jump** (±4 / ±16 beats) |
| Loops | 1 / 2 / 4 / 8-beat loops, with halve, double and exit |
| Modern features | **Key sync** and key shift (♭ / ♯) without changing tempo, **slip mode**, **quantize**, a **smart crossfader** (swaps bass and filters the outgoing deck) and a **cut** curve for scratch-style mixing |
| Mixer | 3-band EQ with full kill, filter (low-pass ↔ high-pass), channel faders, level meters, beat-synced **echo** (¼ to 1 beat), and quick **INST / VOX** stems (an approximation based on stereo placement and frequency, not AI separation) |
| Waveforms | Scrolling 3-band waveforms with a beat grid, cue and loop markers, plus a whole-track overview coloured by section (intro, build, drop, breakdown, outro). Tap the overview to jump |
| Loading | Tap a deck's title. **MATCHES** suggests songs whose key and tempo fit the other deck |

When you leave the decks, a song playing on deck B moves to the main player without a gap.

## Auto DJ
Mixes your queue like a DJ:
- **Beat-matched transitions:** tempo-matched (pitch kept), beat-aligned and phrase-aware (starts on 8-bar boundaries).
- **Analysis on the phone:** BPM, musical key (Camelot), intro and outro, sections and a waveform for each song, computed offline.
- **Transition styles:** Auto, Blend, Bass swap, Filter sweep, Echo out and Cut.
- **Manual control:** mix now, extend by 8 bars, skip, or take over the crossfader.

## Listening modes
One tap changes the queue, the screen layout and how playback behaves. Tap the mode again to get your own settings back. Nothing is based on location or sensors.

| Mode | What it does |
|---|---|
| Sleep | Calm songs, the volume slowly lowers, then playback stops |
| Walk | Steady tempo, big controls, a long queue that never runs out |
| Gym | High energy, intros skipped, no gaps |
| Drive | Familiar songs, huge buttons, even loudness, Android Auto |
| Focus | Smooth energy, no loudness jumps, long blends |
| Party | DJ mixing in harmonic order; the music never stops |

## Visualizer
**MilkDrop**, the classic Winamp visualizer, rendered by [Butterchurn](https://github.com/jberg/butterchurn) (MIT) with its community preset packs, hundreds of presets in all.
- **Reacts to the music:** it uses the audio the app is playing, with no microphone needed.
- **Preset changes:** presets change automatically every 15 s to 2 min, or with every new song. Double-tap for a random preset.
- **Choosing presets:** mark favourites, show favourites only, or search the full list.
- **Quality:** Battery saver, Balanced or Sharpest.

## Radio
- **DI.fm:** all channels, using your own DI.fm Premium listen key (enter it in the Radio tab).
- **SomaFM:** all channels.
- **World:** search thousands of stations through radio-browser.info.

## Make it yours
- **Modular player:** move, resize (S / M / L / XL), hide and configure each part of the player: artwork, title, BPM & key, live spectrum, action buttons, seek bar, playback buttons, up next and modes.
- **Playback buttons you choose:** pick which buttons appear and in what order (previous, next, ±10/30 s, shuffle, repeat, EQ, sleep, DJ, visualizer, speed…).
- **Animated artwork:** still, breathes with the beat, slow zoom, or spinning vinyl.
- **Home dashboard:** rearrange and resize widgets: now playing, modes, shortcuts, recent, favorites, on repeat, recently added and stats.
- **Ready-made layouts:** Standard, Headphones, Home listening and Driving.
- **Tab bar:** choose which tabs show and their order.
- **Gestures:** assign swipes, double-tap and long-press on the player.
- **Themes:**
  - **AMOLED black** or dark grey background.
  - **Dynamic accent colour:** a fixed colour, from the album art, or from your wallpaper (Android 12+).
  - Share or import a theme, or a whole layout, as text.

## Android Auto
Browse Listening modes, Shuffle all, Favorites, Recently played, Playlists, Albums and Artists, and search by voice, from your car's screen.
Because the APK isn't installed from the Play Store, turn on **Unknown sources** in Android Auto's developer settings first. To find them, open Android Auto settings and tap *Version* 10 times; the developer settings are then in the ⋮ menu.

## Get the APK
Every push to `main` builds a signed release APK with GitHub Actions.
1. Open the [**Releases**](../../releases) page and download the latest `Cadence-N.apk`.
2. Open it on your phone and allow installs from your browser when asked.
3. New versions install over the old one, and your playlists and settings are kept.

To try it on a PC, run the same APK in an Android emulator (Android Studio's emulator, or BlueStacks). Drag the APK onto the emulator window to install it.

## Community themes
Themes in [`themes/index.json`](themes/index.json) appear in the app under **More → Themes & layout → Community themes**.
To add yours, tap **Share theme** in the app, add the JSON to that file and open a pull request. The [themes README](themes/README.md) lists the fields.

## Build and signing
- **Requirements:** Kotlin 2.0, Android Gradle Plugin 8.5, compile/target SDK 34, minimum SDK 26 (Android 8.0).
- **Building:** open the project in Android Studio and press **Run**, or run `./gradlew assembleRelease`.
- **Signing:** release builds are signed with a key kept in two repository secrets, never in the code: `CADENCE_KEYSTORE_BASE64` (the keystore, base64-encoded) and `CADENCE_KEYSTORE_PASSWORD`. Local builds without them are signed with the Android debug key.
