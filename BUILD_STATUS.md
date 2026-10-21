# Build status

Source milestone v0.1:

- Project renamed to Hamyar English.
- Package set to `com.hamyareman.english`.
- Kokoro AAR pinned to `0.1.0`.
- FP16 model metadata pinned to exact size and SHA-256.
- Model is bundled in APK assets; phone-side first run does not download it.
- Service binds only to `127.0.0.1:8766`.
- CORS + Private Network Access headers are implemented.
- Persistent WAV cache is implemented.
- Long English input is chunked before inference and WAV chunks are merged.
- GitHub Actions validates the model before building.

Remaining acceptance tests are the CI compile and real-device Android/Chrome loopback test.
