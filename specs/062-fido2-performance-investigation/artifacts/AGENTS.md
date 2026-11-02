# Feature 062 artifact handling

These artifacts preserve the FIDO2 performance investigation, including successful, failed and interrupted runs. Treat frozen identities, schedules, protocol snapshots, per-attempt inputs and historical analysis results as evidence. A later analysis or passing run does not replace an earlier run's authoritative disposition. Consult `../implementation-evidence.md`, `../calibration-protocol.md` and the relevant run report before changing or excluding evidence.

## Compressed session observations

The 24 originals listed in `f062-session-observations-archives.json` are stored as individual `.csv.7z` archives. The manifest records their original and archive filenames, byte lengths and SHA-256 values, plus the 7-Zip format, settings and version used. The archives must extract to the exact original bytes; existing scripts and reports may expect the original CSV paths.

From the repository root, restore the files beside their archives with:

```powershell
.\specs\062-fido2-performance-investigation\artifacts\restore-f062-session-observations.ps1
```

The helper defaults to `C:\Program Files\7-Zip\7z.exe`; pass `-SevenZip <path>` when it is installed elsewhere. To verify without writing into this directory, create an empty destination directory and pass `-Destination <directory>`. The helper verifies archive sizes and SHA-256 values, extracts each archive to a temporary directory, verifies the original size and SHA-256, and refuses to overwrite different existing content. It accepts existing originals only when they match the manifest.

The scoped `.gitignore` lists the 24 verified originals by exact filename. Their local presence is expected; commit the corresponding archives and manifest instead. Do not add a wildcard for all raw observations: a new file needs its own retention and verification decision. If an ignored original is changed, its existing archive and manifest no longer represent it. Preserve the frozen original, investigate the change, and verify a deliberate new archive and manifest entry before relying on it.

## Git and LFS

The scoped `.gitattributes` sends artifact payloads, including `.7z` archives, to Git LFS. `AGENTS.md`, `.gitignore` and `.gitattributes` are ordinary Git text so instructions and ignore rules remain readable before an LFS download. When adding a new evidence archive, verify its effective filter with `git check-attr filter -- <path>` and ensure the LFS remote can retain the payload before committing it.

Do not broadly ignore or delete `*-thread-states.csv`, `*-scheduler.csv`, `*-health.csv`, `*-markers.csv`, `*-clocks.csv`, `*-events.csv`, `*-prints.csv`, old analysis versions or frozen identities. Baseline analyzers directly consume several of these files, and historical outputs may be authoritative for a stopped or earlier run. Review exact references, dynamic script inputs and identity hashes before excluding an individual file. Git ignore rules do not remove existing index entries or historical objects.

Keep raw system-wide traces and sensitive account or credential material out of retained artifacts. Preserve the investigation's documented synthetic, secret-free evidence and cleanup records. Review the existing Git index and stage only intended changes; do not modify unrelated staged work.
