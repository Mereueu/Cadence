# Cadence

A polished, offline music player for Android, built with Jetpack Compose and Media3.

## Features
- **Library** – songs, albums, artists and folders, with sorting and a fast-scroll thumb
- **Now Playing** – artwork-tinted background, swipe the cover to skip, swipe down to close, precise seek bar
- **Equalizer** – 5+ band EQ with live curve, 12 presets, bass boost, surround (virtualizer) and loudness
- **Playlists** – create, rename, delete, drag to reorder, add songs with a searchable picker
- **Smart playlists** – Favorites, Recently played, Most played, Recently added
- **Queue** – drag to reorder, remove, jump to any song
- **Search** – accent-insensitive across songs, albums and artists
- **Sleep timer** with fade-out, **playback speed**, play next / add to queue
- Notification, lock-screen and Bluetooth controls; resumes your last queue
- Seven accent colours
- Plays MP3, AAC/M4A, FLAC, WAV, OGG, Opus, AIFF and more, and handles "Open with" from file managers

## Get the APK
Every push to `main` builds a release APK with GitHub Actions. Open the **Releases** page of this repo and download the latest `Cadence-N.apk`, then open it on your phone (allow installs from your browser when asked).

## Signing
Release builds are signed with a key kept in two repository secrets, never in the code:
`CADENCE_KEYSTORE_BASE64` (the keystore, base64-encoded) and `CADENCE_KEYSTORE_PASSWORD`.
Local builds without those variables are signed with the Android debug key.

## Build locally
Open the project in Android Studio (Koala or newer) and press **Run**, or run `./gradlew assembleRelease`.
