# Grammar audio rebuild — IN PROGRESS

Original: english-grammar-complete-final-v2.zip from the repository's files branch.
Previous session outputs were not recoverable; this rebuild starts from zero.

- source/: original HTML, JSON audio manifests, and images (unchanged).
- batches/: grouped generation plans, source recordings and alignment reports.
- audio/: individual WebM/Opus clips.
- partial/: generated, incomplete HTML output; do not publish as final.
- progress.json: counts only clips that exist and match an unambiguous HTML ID.

Every completed stage must be committed and pushed to the fixed session branch
arena/cabd7584-hamyar-english. Verify local HEAD equals the remote branch SHA.
Never report remote persistence unless the push and SHA comparison succeed.

## Pipeline
Install `imageio-ffmpeg` in a Python virtual environment. Unique clips are indexed
by exact JSON text in `recordings.json`; identical text can reuse a recording.
Original batch samples are preserved losslessly as FLAC (PCM comparison checked).
To rebuild incomplete HTML: `python tools/grammar_audio.py build`.
After reviewing pause boundaries, import a batch using the explicit groups:
`python tools/grammar_audio.py import rebuild-001 --groups 0,1,2,3,4,5,6,7,8,9`.
The importer only auto-cuts groups whose number of detected pauses matches the
number of items. Other groups stay pending, with sources retained for alignment.
Duplicate/ambiguous HTML IDs remain pending. No ASR or manual listening is claimed.

Next-stage planning must skip exact texts already present in recordings.json.
Do not reset the progress counter or regenerate existing text without a reason.

## Session recovery
At the start of each continuation, fetch the fixed remote branch and ensure
local HEAD is up to date before planning new audio. The remote branch is the
source of truth if Arena restores the Git checkout at an earlier commit.
Preserve any local edits before a fast-forward; never discard existing audio.
Batch rebuild-002 keeps groups 8 and 9 pending for additional alignment.

For explicitly reviewed groups with five internal pauses but no >=200ms trailing
pause, use `--no-trailing-groups` alongside `--groups`. This is opt-in, never
inferred automatically. Rebuild-005 groups 0 and 1 remain pending because they
contain additional internal pauses; all source FLACs are retained.
