# Changelog — kokoro-android

On-device neural TTS for Android. Wraps
[Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) (StyleTTS 2 + ISTFTNet) via
ONNX Runtime + espeak-ng.

## 0.1.0 — unreleased

First release.

### Added
- `KokoroTTS` API: `initialize`, `speak`, `setVoice`, `getAvailableVoices`,
  `addVoice`, `release`.
- Pipeline: espeak-ng 1.52.0 G2P (native) → tokens → Kokoro ONNX (ONNX Runtime) →
  24 kHz WAV.
- Bundled voice: `af_heart` (American English female).
- `arm64-v8a`, minSdk 24, 16 KB page aligned (Android 15 ready).
