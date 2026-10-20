# Karaoke Party — Native Android MVP

Native Kotlin Android app. Paste a YouTube video link, play the embedded YouTube player, and enable phone microphone passthrough using AudioRecord/AudioTrack. AudioTrack uses Android media routing, which can send to a paired Bluetooth A2DP speaker depending on device routing.

## Build APK (no local Android setup)
1. Create a GitHub repository and upload the **contents** of this folder, including `.github/workflows/build-apk.yml`.
2. Open GitHub **Actions** > **Build Android APK** > **Run workflow**.
3. Download `karaoke-party-debug-apk` from the successful workflow run. Extract the ZIP and install `app-debug.apk` on Android.

Or build locally with JDK 17, Android SDK 35, and Gradle 8.9: `gradle :app:assembleDebug`.

## Usage
1. Pair a Bluetooth speaker in Android Settings and select it as media output.
2. Open the app, paste a YouTube link, and tap Load. Tap Play in the video.
3. Tap MIC ON, allow permission, and adjust microphone gain.
4. Set music loudness using Android volume buttons.

## Important limitations
- **Not yet compiled or device-tested.** No APK is included in this source archive. The GitHub workflow is supplied to generate one.
- Bluetooth A2DP adds substantial latency. The microphone can sound delayed; the app cannot remove speaker codec/buffering delay. A wired speaker is preferable for live monitoring.
- Some phones will mute, reroute, or duck YouTube audio when the mic starts. Device behavior varies. Embedded YouTube playback may be restricted for some videos.
- This MVP has **no echo, recording, queue, or latency correction** yet. Avoid loud gain or placing the phone near the speaker because acoustic feedback can occur.
- Microphone monitoring only runs while the activity is foregrounded. No background capture.
