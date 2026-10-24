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
