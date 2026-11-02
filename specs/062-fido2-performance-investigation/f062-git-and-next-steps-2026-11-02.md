# F062 Git retention and next steps — 2026-11-02

**Updated:** 2026-11-02, America/Mexico_City  
**Purpose:** Minimize unnecessary files and bytes in Git history while preserving the evidence required to review and reproduce Feature 062.  
**Scope:** Retention assessment and owner-approved lossless 7-Zip conversion. No staging, unstaging, commit, push, or branch switch was performed.

## Decision

Do not apply the previous blanket artifact `.gitignore`. It would exclude inputs that the baseline analyzers require and authoritative records of stopped runs. A final analysis JSON does not replace the inputs needed to recompute or audit it.

The 24 calibration and diagnostic session-observation CSVs have been losslessly compressed into individual `.7z` archives. The [manifest](artifacts/f062-session-observations-archives.json) records original and archive sizes and SHA-256 values; the [restore helper](artifacts/restore-f062-session-observations.ps1) extracted and verified all 24 originals in an isolated directory. The 1,074,060,021-byte originals occupy 32,283,051 bytes in the archives, a 96.99% reduction. The originals remain locally and are excluded by exact filename in `artifacts/.gitignore`; the archives remain eligible for Git LFS. A separate durable evidence archive could keep large evidence entirely outside Git, but no such destination was verified in this assessment.

Keep local scratch output outside Git. Keep reviewed source changes, current documentation, reproducible tools and authoritative evidence. Treat historical scripts, identities, failed-run outputs and older analyses as evidence until their dependencies and retention obligations have been reviewed individually. Age, file size and version suffix alone do not establish that a file is unnecessary.

## Corrected investigation findings

All 28 Feature 062 task rows are checked complete under the owner-approved bounded comparative disposition. The absolute 20 ms comparison remains inconclusive at both sensitivity scales. Completion does not establish physical-device threshold compliance, a population effect, or a cause for the entire historical gap.

| Condition | Before sampled median | After sampled median | Supported finding |
|---|---:|---:|---|
| Cold | 183.979 ms | 186.929 ms | +1.60%; material at both sensitivity scales; transferred to follow-up |
| Warm | 149.195 ms | 33.032 ms | -77.86% sampled median; sensitivity-expanded median intervals disjoint at 1x and 2x; absolute classification remains inconclusive |

The accepted groups are:

- Before cold: Baseline 03 cold-01 through cold-10.
- Before warm: Baseline 03 warm-01 through warm-07 plus Baseline 07 warm-01 through warm-03.
- After cold: Baseline 09 cold-02 through cold-10 plus Baseline 13 cold-01.
- After warm: Baseline 09 warm-01 through warm-07 plus Baseline 13 warm-01 through warm-03.

Baselines 02 and 04 do not contribute to those pools. Baseline 09 cold-01/warm-08 and Baseline 11/12 cold-01 remain excluded. Failed runs and uncounted warmups remain retained evidence. See the [authoritative comparison](artifacts/f062-asus35-baseline-13-t017-comparison.md), [tasks](tasks.md), [specification amendment](spec.md), and [closure audits](implementation-evidence.md).

Calibration results apply to their own frozen identities:

| Calibration | Frozen C / H / u (ns) | Actual maximum C / H (ns) | Result |
|---|---|---|---|
| 24 | 530,677 / 17,051,771 / 17,582,448 | 57,605 / 15,814,376 | 874 sessions passed |
| 26 | 530,677 / 19,442,969 / 19,973,646 | 155,885 / 8,589,584 | 874 sessions passed; subsequently rebuilt target is a different identity |
| 27 | 530,677 / 19,442,969 / 19,973,646 | 138,073 / 18,484,687 | 874 sessions passed on the aligned identity |

Sources: [Cal 24 report](artifacts/f062-asus35-calibration-24-execution-report.md), [Cal 26 report](artifacts/f062-asus35-calibration-26-execution-report.md), [Cal 27 report](artifacts/f062-asus35-calibration-27-execution-report.md). These passes do not guarantee future parser health or transfer to the proposed lower-overhead method. Exploration 07's CPU-load retry failed with 81 parser errors and supplies no accepted timing distribution; see the [failure review](artifacts/f062-overhead-explore-07-cpu-parser-failure-review.md).

The retained full local CI run passed in 1,222.48 seconds. Its scope and the four diagnostic assumption outcomes are documented in the [retained CI report](artifacts/local-ci-20261101-r02/README.md). No CI or device workload was rerun for this Git-retention assessment.

## Measured artifact inventory

Inventory taken before archive conversion on 2026-11-02. Sizes are bytes; MB below means decimal MB. The working directory now also contains the 24 archives, manifest, restore helper and scoped ignore file; originals remain locally.

| Extension | Files | Bytes |
|---|---:|---:|
| `.csv` | 1,576 | 1,267,607,942 |
| `.json` | 566 | 20,676,202 |
| `.md` | 261 | 876,791 |
| `.ps1` | 164 | 2,165,365 |
| `.psm1` | 2 | 4,401 |
| `.sql` | 27 | 72,054 |
| `.textproto` | 6 | 7,220 |
| `.txt` | 81 | 274,379 |
| `.xml` | 8 | 45,394 |
| `.gitattributes` | 1 | 77 |
| **Total** | **2,692** | **1,291,729,825** |

The 24 `*-raw-session-observations.csv` files contain **1,074,060,021 bytes**, about **83.1%** of all artifact bytes. They contain calibration observations, warmups, session metadata and 1,000 diagnostic clock increments per session. The [calibration protocol](calibration-protocol.md) distinguishes those increments from independent statistical observations. Calling these files raw database dumps is inaccurate; they are retained instrumentation CSV exports.

Content hashing found 58 groups with identical SHA-256 values. Copies beyond the first total only **281,516 bytes**. Identical bytes can still have distinct run/path roles, and Git/LFS content addressing already avoids storing the same content once per filename. Duplicate removal is therefore a minor storage opportunity and requires path-reference review.

### Lossless compression feasibility

One largest-file example was compressed in the ignored `.agent/tmp/f062-retention-review/` directory using both .NET `GZipStream` with `CompressionLevel.Optimal` and the installed 7-Zip executable with `.7z` format and `-mx=5`. Each output was decompressed and the restored content's SHA-256 matched the source.

| Item | Measurement |
|---|---:|
| Source | `artifacts/f062-asus35-calibration-27-raw-session-observations.csv` |
| Original bytes | 164,838,552 |
| Gzip bytes | 8,392,011 |
| Gzip reduction | 94.91% |
| 7-Zip bytes | 5,000,789 |
| 7-Zip reduction | 96.97% |
| Original and decompressed SHA-256 | `19A9C2A67EF5138944D99FDD43A76D9B1AD5FA361480CF031AEF72E41FAF7CAC` |

This is a feasibility result for one file, not a measured total for all 24 files. The tested 7-Zip archive is about 40.4% smaller than the tested gzip output; the tools used different compression settings. No artifact was replaced by a temporary compressed sample. The temporary archives and assessment JSON are already excluded by the root `.agent/tmp/` ignore rule.

## File retention assessment

| Files | Why retain them | Git storage recommendation |
|---|---|---|
| Reviewed product/test/build changes | Implemented behavior and verification | Commit only reviewed changes attributable to this work |
| Current spec, contract, plan, tasks, evidence and follow-up backlogs | Approved scope, findings, limitations and closure | Commit |
| Current runners, queries, configurations, classifiers and analyzers | Reproduction and verification | Commit; preserve historical frozen versions where referenced |
| Identities, schedules, protocol snapshots, approval/disposition records | Bind every run to its method and preserve stopped-run history | Retain; use LFS under the existing scoped policy |
| Accepted and failed attempt input exports | Permit eligibility, health and partition checks | Retain in LFS, or a verified lossless archive with restoration instructions |
| Final and historically authoritative analyses, reports and comparisons | Record the result actually reached under each frozen policy | Retain; a newer version does not erase an earlier run's authoritative result |
| Calibration session observations | Complete session record and diagnostic context | Prefer verified lossless compressed storage; do not simply discard |
| CI counters, source inventories, host assessments and connected XML | Support verification claims and retained failures | Retain |
| Local caches, scratch archives and regenerated convenience views with no evidence role | Rebuildable working material | Ignore outside the evidence directory; the current scratch directory is already ignored |
| Proven unused drafts or convenience copies | Potentially unnecessary | Exclude only after reference, identity-input and dependency review; none newly approved for exclusion here |

### Why the earlier ignore rules were unsafe

Both [analyzer v4](artifacts/analyze-f062-baseline-attempt.ps1) and [analyzer v5](artifacts/analyze-f062-baseline-attempt-v5.ps1) directly open the attempt's `-result.json`, `-markers.csv`, `-prints.csv`, `-scheduler.csv`, `-thread-states.csv`, `-health.csv`, `-events.csv`, and `-log-events.txt`. Several are mandatory presence checks. Excluding them would prevent the documented analyses from running in a fresh checkout. Other runners/summarizers use clock, health and marker tables for their own gates.

Do not install broad exclusions for these patterns:

```text
*-thread-states.csv
*-scheduler.csv
*-health.csv
*-markers.csv
*-clocks.csv
*-events.csv
*-prints.csv
*-analysis-v2.json
*-analysis-v3.json
*-identity-v*.json
*-raw-session-observations.csv
```

The first group includes analyzer inputs. The latter group can include authoritative historical outcomes or exact identity snapshots. The `*-raw-session-observations.csv` wildcard must not be used: only the 24 explicitly enumerated, verified originals are now ignored. Some individual files may eventually be unnecessary, but these suffixes are insufficient evidence for exclusion. In particular, Baseline 02's v3 outcome remains authoritative for that frozen run.

## Recommended storage preparation before the first artifact commit

1. Produce a reviewed file manifest separating source/documentation, executable analysis tools, primary evidence, authoritative results, and proven convenience files. Follow dynamic filename construction in scripts as well as literal Markdown links; absence from a text search does not prove a file is unused.
2. **Done:** Compressed the 24 session-observation CSVs with 7-Zip 26.05 x64, `.7z` format, `-mx=5`; the manifest records each filename, size and SHA-256 for the original and archive. Each archive contains one original CSV.
3. **Done:** The restore helper verified the archive hashes and extracted all 24 into an isolated directory; each restored size and SHA-256 matched its original. It requires 7-Zip (default `C:\Program Files\7-Zip\7z.exe`; use `-SevenZip` to select another executable). It refuses to overwrite a different existing file. To restore the ignored CSVs to their expected paths in a fresh checkout, run:

   ```powershell
   .\specs\062-fido2-performance-investigation\artifacts\restore-f062-session-observations.ps1
   ```

   For isolated verification, create a destination directory and pass `-Destination <existing-directory>`. Keep frozen identities and historical result files unchanged. If any locally ignored original is altered, do not assume its archive represents the new bytes; the helper rejects a mismatch.
4. **Done locally:** After the round-trip checks passed, added exact filename entries for the 24 originals in `artifacts/.gitignore`; checked that none were in the Git index before doing so. At the last index inspection, `.gitignore` and `.gitattributes` were staged, while the archives, manifest, restore helper and `AGENTS.md` were untracked. This is an incomplete commit set: review and include the archives, manifest, helper and instructions together with the ignore rules before committing. No staging commands were run as part of this work. An ignore rule does not remove objects from existing history or unstage a previously staged file.
5. Keep the rest of the evidence until its dependencies are reviewed. The remaining files are approximately 217.7 MB before additional compression; no blanket exclusion saving another 170 MB is justified by this assessment.
6. Review staged changes by explicit path and diff. Do not run broad `git add feature/fido2/`, `git add specs/055-migrate-hdk-draft/`, or `git add .` commands in this shared worktree. Existing staged changes may include unrelated work. Do not switch branches, commit or push as a side effect of this document.

### Existing scoped LFS policy

`artifacts/.gitattributes` currently contains:

```gitattributes
* filter=lfs diff=lfs merge=lfs -text
*.7z filter=lfs diff=lfs merge=lfs -text
.gitattributes -filter diff merge text
.gitignore -filter diff merge text
AGENTS.md -filter diff merge text
```

Preserve this owner-requested scoped policy. It applies to artifact scripts, most Markdown, the manifest and archives; `AGENTS.md`, the attributes file and the ignore file are ordinary text so instructions and rules are readable without an LFS download. The explicit `.7z` rule confirms archive handling. LFS moves payloads outside ordinary Git blobs but still requires storage, upload and retention of those payloads. Moving an unnecessary file to LFS does not make it necessary. See the [artifact instructions](artifacts/AGENTS.md).

Before a commit, verify LFS is available and the configured remote supports the required payloads. No remote-capacity or upload check was performed here. Do not infer remote readiness from the presence of `.gitattributes`.

### Alternative: separate durable evidence archive

If keeping evidence payloads out of Git/LFS history is the priority, store the complete evidence bundle in a durable, access-controlled archive and commit only the reviewed source/docs/tools, small authoritative results, a checksum manifest, an immutable archive identifier and restoration instructions. The archive must preserve failed runs and frozen identities, remain accessible to reviewers, and restore expected relative paths. Verify the complete round trip before ignoring any evidence path.

A local temporary folder, an unverified URL, or the ability to rerun device sessions is insufficient. Until a durable destination is established, the verified compressed archives in LFS are the concrete storage option supported by the current repository setup.

## Subsequent engineering work

The existing [cold lookup follow-up](../../docs/backlog/fido2-cold-settings-lookup-performance.md) calls for further component attribution and a decision on whether another optimization is worthwhile. DataStore prewarming is a hypothesis; it was not selected or validated by Feature 062. Profile 04 identified encrypted-preference construction as the dominant substep of one cold observation. Any proposal should address that evidence and preserve startup behavior, migration and failure semantics.

The separate [registration reliability backlog](../../docs/backlog/fido2-warm-registration-security-key-failure.md) records recurrence in Baselines 02, 03 and 09. The failing transport boundary and root cause remain unknown. Timeout or session cleanup changes need diagnosis first.

Feature 058's plan is repository context, not an instruction to switch branches automatically. If that work is resumed, inspect its current tasks and evidence before choosing work. Its Tier 1 hard floors are 60% instruction and 60% applicable branch coverage, with approximately 65% branch coverage as the remediation goal and 50% repository instruction coverage. Preserve the owner-authorized hosted-execution exceptions; see the [coverage plan](../058-enforce-coverage-targets/plan.md).

## Assessment outcome

The previous recommendation to exclude about 920 files was unsupported and has been removed. The 24 largest session-observation exports are now archived and individually verified; the exact originals are locally ignored and remain on disk. No Git index changes were made. Review the archive set and restoration helper before staging, and review any further exclusion candidates individually.
