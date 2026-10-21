# Hamyar English v0.1

Android-first English learning app. The first milestone embeds an on-device Kokoro TTS engine so lesson HTML can request natural English speech from `127.0.0.1:8766` without cloud TTS.

## Locked architecture

- App: **همیار انگلیسی / Hamyar English**
- Android package: `com.hamyareman.english`
- Android 7.0+ (`minSdk 24`)
- `arm64-v8a`
- Kotlin
- Kokoro Android AAR `0.1.0`
- Kokoro v1.0 FP16 ONNX model bundled in the APK
- Model stored in GitHub using Git LFS
- SHA-256 validation before first use
- Foreground service
- HTTP server bound to `127.0.0.1:8766` only
- OpenAI-style endpoint: `POST /v1/audio/speech`
- WAV, mono, 24 kHz
- On-device persistent WAV cache
- CORS + Private Network Access response headers

## User first-run flow

1. Install and open the APK.
2. Tap **آماده‌سازی موتور آفلاین**.
3. The bundled FP16 model is copied from APK assets into app-private storage and SHA-256 verified.
4. The local foreground service starts.
5. Tap **Test Voice**.
6. The app calls its own `127.0.0.1` API and plays `Are they at the office?`.
7. The same test must work in Airplane Mode. No model download is required on the phone.

## API

```http
GET http://127.0.0.1:8766/health
```

```http
POST http://127.0.0.1:8766/v1/audio/speech
Content-Type: application/json

{
  "input": "Are they at the office?",
  "voice": "af_heart",
  "speed": 1.0,
  "response_format": "wav"
}
```

`speed`: 0.5 to 2.0. v0.1 exposes the bundled `af_heart` voice.

## Model integrity

- Asset path: `app/src/main/assets/models/kokoro-v1.0.fp16.onnx`
- Exact size: `163527961` bytes
- SHA-256: `f3a290d384fbb27966d462905c71a46cef9e5fd00516b40df32a0b4afe77ac96`

GitHub normal Git rejects files above 100 MB, therefore `*.onnx` is tracked with Git LFS.

## CI

`Build Android APK` checks out LFS objects. If the model has not been imported into LFS yet, CI downloads the pinned model, verifies exact size + SHA-256, and then builds the APK. This guarantees the APK is self-contained.

Artifact: `hamyar-english-debug`
