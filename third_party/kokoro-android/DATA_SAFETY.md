# Data Safety — kokoro-android

For your Google Play **Data safety** form.

## What this library does

kokoro-android performs **100% on-device** text-to-speech. It takes text your app
provides and produces audio locally using a neural model (Kokoro-82M, **not bundled —
you provide it**, see README) run via ONNX Runtime, with espeak-ng for phonemization.

## Data collection & sharing

- **No data collected.** The library has no analytics, no telemetry, no identifiers.
- **No data shared.** Nothing is transmitted off the device — there is **no network
  code** in the library. Text, phonemes, and audio never leave the phone.
- **No internet permission** is declared or required by the library.

## Data processed (on-device only, not stored by the library)

| Data | Purpose | Leaves device? | Stored? |
|---|---|---|---|
| Text you pass to `speak()` | synthesize audio | No | No (processed in memory) |
| Generated audio | returned to your app | No | No (your app decides) |

The Kokoro ONNX model and voicepacks are files **you** provide/ship; the library only
reads them locally. `espeak-ng-data` is extracted from the AAR into your app's private
storage on first run.

## Permissions

The library declares **no permissions**. Your app controls audio playback (e.g.
`AudioTrack`) and any file/network access for downloading the model.
