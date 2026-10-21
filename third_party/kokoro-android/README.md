# Kokoro TTS for Android — On-Device Neural Text-to-Speech AAR

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Website](https://img.shields.io/badge/website-jokobee.com-blue.svg)](https://www.jokobee.com)

**Natural neural speech on Android with one Gradle line.** No cloud, no API key, no
per-character billing. Your users' text never leaves the phone.

A prebuilt [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) AAR (StyleTTS 2 +
ISTFTNet, #1 on TTS Arena) with a clean Kotlin coroutine API. Speech runs via
[ONNX Runtime](https://onnxruntime.ai/) with [espeak-ng](https://github.com/espeak-ng/espeak-ng)
for phonemization. 24 kHz output, Apache-2.0.

## Install

```kotlin
dependencies {
    implementation("dev.ffmpegkit-maintained:kokoro-android:0.1.0")
}
```

## Quick start

```kotlin
import dev.ffmpegkit.kokoro.KokoroTTS

lifecycleScope.launch {
    // 1. Load the Kokoro ONNX model you downloaded (see "Model" below).
    KokoroTTS.initialize(context, File(getExternalFilesDir(null), "kokoro.onnx").absolutePath)

    // 2. Synthesize — returns a WAV byte array.
    val result = KokoroTTS.speak("Hello from Kokoro, speaking on-device.")
    // play result.audioData with AudioTrack, or save it.

    // 3. Free native memory.
    KokoroTTS.release()
}
```

All heavy calls are `suspend` functions — call them from a coroutine.

## Model (not bundled — you download it)

The Kokoro model is **~300 MB** (ONNX), far too large for an AAR. Download it once
(ship it or fetch on first run) from
[**hexgrad/Kokoro-82M**](https://huggingface.co/hexgrad/Kokoro-82M) and place it in
`getExternalFilesDir(null)/kokoro.onnx`.

The **voicepack** (per-voice style embedding) is bundled: Free ships `af_heart`
(American English female). See the [wiki](../../wiki) for adding voices.

## Pipeline

```
Text → espeak-ng (G2P: text → IPA phonemes) → tokens → Kokoro ONNX → PCM 24 kHz → WAV
```

espeak-ng runs natively (`libespeak-ng.so`); the Kokoro model runs via ONNX Runtime
in Kotlin. Its phoneme data (`espeak-ng-data/`) is bundled and extracted on first run.

## What's inside

| | |
|---|---|
| Engine | Kokoro-82M (StyleTTS 2 + ISTFTNet) · ONNX Runtime · espeak-ng 1.52.0 |
| Audio | 24 kHz mono, natural quality |
| Voice (Free) | `af_heart` (1 English voice) |
| ABI | `arm64-v8a` |
| Min SDK | API 24 (Android 7.0) · 16 KB pages (Android 15 ready) |

## Free vs Pro

| | **Free** (this) | **Pro** |
|---|:---:|:---:|
| Kokoro TTS, WAV export | ✅ | ✅ |
| Bundled voices | 1 (af_heart) | 54 voices |
| Streaming audio (Flow) | ✗ | ✅ |
| IPA / pronunciation control | ✗ | ✅ |
| SSML (pauses, emphasis, rate) | ✗ | ✅ |
| Multi-voice dialogue | ✗ | ✅ |
| Long-form auto-chunking | ✗ | ✅ |
| Audio cache | ✗ | ✅ |
| Export | WAV | WAV, MP3, OGG, PCM |
| ABI | arm64-v8a | arm64-v8a + x86_64 |
| Channel | Maven Central + JitPack | Gumroad |

Hit one of these limits in Free? **→ [Get Kokoro Pro](https://www.jokobee.com)** — 54 voices, streaming, SSML, and more, at [jokobee.com](https://www.jokobee.com).

## Completes the on-device AI stack

**[Whisper](https://github.com/ffmpegkit-maintained/whisper)** (hear) →
**[llama.cpp](https://github.com/ffmpegkit-maintained/llama-android)** (think) →
**Kokoro** (speak). A fully private voice assistant — no data leaves the device.

## Building from source

Requires NDK r27c and CMake 3.22.1. espeak-ng is a pinned submodule (1.52.0). The
espeak-ng phoneme data is staged at build time:

```bash
git clone --recursive https://github.com/ffmpegkit-maintained/kokoro-android.git
cd kokoro-android
./scripts/fetch-espeak-data.sh      # populate assets/espeak-ng-data/
./gradlew :library:assembleRelease
```

## License

Apache-2.0 — see [LICENSE](LICENSE). Kokoro-82M is Apache-2.0 (© hexgrad).
espeak-ng is GPL-3.0 (used as a separate `.so`).

---

Maintained by **[Jokobee](https://www.jokobee.com)** · contact@jokobee.com
