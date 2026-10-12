# Fabula Audio — AI handoff note for 4.29

## Current release
- versionName: 4.29
- versionCode: 29
- GitHub tag: v4.29

## Base
This archive was produced from the last complete project archive available in the working environment: `FabulaAudio_Android_4.27_fixed33.zip`.

The user-provided 4.28 build configuration was applied:
- FFmpeg: `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`
- release `minifyEnabled true`
- release `shrinkResources true`
- `proguard-rules.pro`
- ABI filters: `armeabi-v7a`, `arm64-v8a`

## Requested 4.29 change
`RecordTextActivity.kt` now explicitly requests 2-channel recording on Android 12+:
`MediaRecorder.setAudioChannels(2)`.

The existing recording/merge/save flow was not redesigned. Because the recorded fragments are stereo on supported Android versions and the existing merge uses stream copy, the saved merged M4A remains stereo.

## Do not change
- ×6 / ×144 / ×432 processing logic
- UpdateManager
- SAF/FileProvider
- icons/splash
- gradle.properties
- existing UI unless explicitly requested

## Important source limitation
No separate complete 4.28 project archive was available in the working environment. Therefore features whose 4.28 source was not present were not guessed or reconstructed.
