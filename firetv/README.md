# IPTV Fire TV — first preview

Native Android/Fire OS app for the **Fire TV Stick 4K 1st generation (2018)**,
Fire OS 6 / Android 7.1 API 25 or later. It is separate from the Electron app.
The APK has no native CPU binaries, so it supports the Stick's 32-bit Android
runtime without packaging Windows FFmpeg or a desktop browser.

## Included

- Hardware-decoded playback through AndroidX Media3/ExoPlayer: HLS, DASH, MP4,
  MPEG-TS and other formats supported by the device's decoders.
- M3U loading, cached lists, search by name/group, favorites and failed-channel
  filtering. Rows are virtualized for large playlists.
- Native D-pad focus and remote Play/Pause, rewind/fast-forward where seekable.
- **Last** button and the remote **Menu (hamburger)** button to toggle streams,
  including while fullscreen. Back opens the channel list without stopping video.
- On-device saved volume/mute, favorites, and up to 200 history entries. Movies
  and episodes offer Resume/Start over when the source is seekable. History
  groups S01E03 / 1x03 names and shows the last watched episode per series.
- Supplied subtitle tracks: Auto English prefers a forced foreign-dialogue track;
  CC lets you switch tracks or turn them off. SRT/VTT import uses Android's file
  picker if one is installed on the Stick.
- Playlist and playback timeouts, bounded reconnects, and stream cleanup when
  switching channels or leaving the app. No Google services are required.

## Limits of this preview

The app has **not been tested on your physical Fire Stick**. Build checks and
local JVM/Android-simulation tests do not verify hardware codecs, provider
compatibility, or real remote behavior on that device.

**Local AI translation is not included.** The Windows speech-engine executable
cannot run on ARM/Fire OS. A native ARM engine, memory budgeting and performance
measurements are required before enabling it on the 1.5 GB 2018 Stick. This
preview does not upload audio or use a PC as a translation server. Supplied
English subtitle tracks work independently of AI translation.

Live-TV recording/timeshift and Windows-to-TV library syncing are not included.
Enter the same provider playlist URL on the TV; favorites/history are local to
that device. No provider URLs, account credentials, channels, or ads are bundled.
Unsupported codecs or encrypted/DRM streams may need provider-specific support.

## Build

Open this folder in Android Studio, or install JDK 17 and Android SDK platform
35/build-tools 35.0.0. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest
:app:lintDebug`. On Windows, `./build.ps1` also copies the APK into the repository's
ignored `release/` directory. Gradle 8.11.1 and its distribution hash are pinned.

The preview is signed with your local Android debug key and is for personal
sideloading, not an Amazon Appstore production release. Keep the same signing key
for updates; it is outside this repository in the usual Android debug-key
location. No private signing keys are committed.

## Installation (when you are ready)

1. On the Fire Stick, open **Settings → My Fire TV → About**. If Developer Options
   is hidden, select the device name seven times. Return and open Developer Options.
2. Enable **ADB debugging** and note the IP under **About → Network**.
3. On a computer on the same network with Android platform-tools, run:

   ```text
   adb connect FIRE_STICK_IP:5555
   adb install -r "IPTV-FireTV-1.1.0.apk"
   ```

   Accept the debugging authorization on the TV if prompted. No connection is
   made by the build script; these commands are for you to run when ready.
4. Open **IPTV Fire TV** in Your Apps, choose **Playlist**, and enter your M3U URL.
   Amazon's Fire TV mobile app can help with typing a long URL.
5. Use **Watch** for full screen, **Menu** for Last, **Back** for the list, and
   **CC** for subtitles. Hold Select on a channel or use its star for Favorites.

An APK can alternatively be transferred using a sideload/file-transfer app you
already use; allow installation for that app in the Stick's Developer Options.

## Sources

- [Amazon Fire TV device identification](https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html)
- [Fire OS 6 development](https://developer.amazon.com/docs/fire-tv/fire-os-6.html)
- [Android TV remote controls](https://developer.android.com/training/tv/get-started/controllers)
- [Media3 release documentation](https://developer.android.com/jetpack/androidx/releases/media3)

## 1.0.2 preview

Large playlists now parse directly from the network stream, and the playlist cache reads/writes one channel at a time. This avoids keeping a download byte array, a full playlist string, split lines, and a full JSON array in memory simultaneously. Limits are 256 MB downloaded, 200,000 unique channels, and 64 KB per line. Unused metadata such as logo URLs is discarded. Controls have explicit inner padding and gaps between buttons.

1.0.3 buffers playlist cache output in 64 KB blocks to avoid many tiny storage writes on Fire OS.

1.0.4 includes the new blue TV/play app icon and matching Fire TV launcher banner.

1.0.5 shows a separate playlist loading panel with elapsed time, downloaded MB, parsed channel count, and saving percentage. Download percentage is shown only when the provider supplies a total size; otherwise an animated bar and live counts show activity. Cached startup also shows elapsed time and channels read.

## Automatic releases and in-app updates (1.1)

The sidebar **Updates** button checks GitHub for a newer Fire TV build. Startup checks run at most once every six hours. Accept Download update, wait for verification, and approve the system installer on the TV. On Fire OS 6 enable Apps from Unknown Sources if the system requests it. Later Fire OS versions may require allowing IPTV Fire TV under Install unknown apps. No computer is needed after the updater-enabled build is installed. Cancelling an update leaves the installed app and its library intact.

Every push to main runs `.github/workflows/firetv.yml`; a successful checked build is signed with the original preview key and published with an updater manifest. Fork pull requests only run checks and do not receive signing credentials. See the root README for encrypted key backup and signing-secret details. Public releases are at https://github.com/anthony-baker87/iptv-player/releases .

`./build.ps1 -Release` also makes a locally signed release APK. CI uses environment variables for the keystore path, passwords, alias, and monotonically increasing version. Never commit the keystore, passwords, local preferences, or provider playlist to the public repository.
