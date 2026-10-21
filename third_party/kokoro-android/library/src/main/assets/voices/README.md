# Bundled voicepacks

The Free tier bundles **one** voice: `af_heart.bin` (American English female, ~5 MB).

**Format:** raw little-endian `float32`, shape `[rows, 256]` (the Kokoro style
embedding; `rows` indexed by token length, 256 = style dim). Convert from the
official Kokoro voicepack (`af_heart.pt` / `.npy` on
[Hugging Face — hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M)) to
flat float32.

Place `af_heart.bin` in this directory before building. The Pro tier bundles all 54
voices (fetched by a build script — see the private repo).
