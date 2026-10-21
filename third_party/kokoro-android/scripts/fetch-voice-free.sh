#!/usr/bin/env bash
# Download the single Free-tier voice (af_heart) and convert it to the flat float32
# `.bin` format the library reads. Staged into assets/voices/af_heart.bin.
# Requires python3 + torch. Source: hexgrad/Kokoro-82M (Apache-2.0).
set -euo pipefail

OUT="$(cd "$(dirname "$0")/.." && pwd)/library/src/main/assets/voices"
mkdir -p "$OUT"

python3 - "$OUT" <<'PY'
import sys, os, numpy as np, torch
from huggingface_hub import hf_hub_download

out = sys.argv[1]
# hexgrad/Kokoro-82M keeps voicepacks under voices/<id>.pt
p = hf_hub_download("hexgrad/Kokoro-82M", "voices/af_heart.pt")
arr = torch.load(p, map_location="cpu").numpy().astype("<f4")  # [rows,1,256] -> flat float32
arr.reshape(-1).tofile(os.path.join(out, "af_heart.bin"))
print("staged af_heart.bin (%d floats)" % arr.size)
PY
