# F062 FIDO2 Performance Investigation — Git Commit & Next Steps Analysis

**Created:** 2026-11-01T19:21:00-06:00 (2026-11-02T01:21:00 UTC)  
**Scope:** Read-only analysis of `specs/062-fido2-performance-investigation/` and its `artifacts/` subdirectory.  
**Purpose:** Determine which files should be committed to Git and document current investigation state for continuity.

---

## Part 1 — Investigation state snapshot

### Overall status

| Gate | State |
|---|---|
| T027 Coroutine lifecycle | ✅ Complete |
| T028 Empirical calibration | ✅ Complete — `u = 17,582,448 ns` frozen (Calibration 24) |
| T008 APK fingerprint + probe | ✅ Complete |
| T009 Baseline capture | ⚠️ **Incomplete** — warm quota unreachable (7/10 warm verified; warm-08 through warm-11 failed at RP/security-key exchange) |
| T010 Attempt validation | ✅ Complete as recorded |
| T011 Partition computation | ✅ Complete as recorded |
| T012 Lead disposition | ✅ Complete as recorded — **disposition: inconclusive/open** |
| T013–T017 Optimization branch | ❌ Not started — gated on conclusive T011 |
| T024 Backlog deletion | ❌ Open — gated on T013–T017 and final closure |

### Frozen allowance

**Calibration 24** passed all 874 sessions (2026-11-01):
- Candidate: `C = 530,677 ns`, `H = 17,051,771 ns`, `u = 17,582,448 ns`
- Max observed C: 57,605 ns (quiet). Max observed H: 15,814,376 ns (quiet).
- `2u = 35,164,896 ns` exceeds the 20 ms not-material threshold — not-material classification structurally unavailable at 2× sensitivity.

### Calibration history (all runs)

| Run | Outcome | Stop reason |
|---|---|---|
| Cal 01–05 | Failed | Early harness defects |
| Cal 06–07 | Failed | Run-ID / validator defects |
| Cal 08 | Failed | H exceedance |
| Cal 09 | Failed | H exceedance |
| Cal 10 | Failed at session 29 | Parser failures (36) |
| Cal 11 | Failed preflight | Schedule-hash mismatch |
| Cal 12 | Failed at session 28 | Parser failures (3) |
| Cal 13 | Failed at session 17 | Parser failures (17) |
| Cal 14 | Failed at session 2 | Parser failures (36) |
| Cal 15 | Failed at session 268 | Parser failures (21) |
| Cal 16 | Failed preflight | Duplicate identity paths |
| Cal 17 | Failed at session 105 | Parser failures (33) |
| Cal 18 | Failed at session 106 | Parser failures (28) |
| Cal 19 | Failed at session 37 | H exceedance (H=17,027,031 ns) |
| Cal 20 | Failed at session 1 | Missing stdout / runner defect |
| Cal 21 | Failed at session 784/874 | C exceedance (C=530,677 ns > 481,407 ns) |
| **Cal 22** | ✅ **PASSED** | 874/874, max C=60,625 ns, max H=6,128,281 ns, u=17,557,708 ns |
| Cal 23 | Failed at session 726/874 | H exceedance (H=17,051,771 ns > 17,027,031 ns) |
| **Cal 24** | ✅ **PASSED** | 874/874, max C=57,605 ns, max H=15,814,376 ns, **u=17,582,448 ns (frozen)** |
| Cal 25 | Failed at session 78/874 | H exceedance (H=19,442,969 ns; new target APK) |
| Cal 26 | 🔄 **Running** | 333/874 PASS as of snapshot, max C=68,073 ns, max H=8,589,584 ns |

### Baseline history

| Run | Cold attempts | Warm attempts | Outcome |
|---|---|---|---|
| Baseline 01 | 2 cold | — | Both ineligible; stopped |
| Baseline 02 | 10 cold | 7 warm verified | **Incomplete** — warm-08 through warm-11 failed RP/key exchange; 10-warm quota unreachable; every verified partition has nonzero unknown time |
| Baseline 03 | 10 cold | 8 warm | Supplement attempts; see composite analysis |
| Baseline 04 | 4 cold | — | Supplement |
| Baseline 05 | 1 warmup | — | Supplement; stopped |
| Baseline 06 | 1 warmup | — | Supplement; stopped |
| Baseline 07 | 3 warm | — | Supplement |

**Baseline 02 disposition:** Inconclusive/open. All ten cold attempts and seven warm attempts are RP-verified; warm quota incomplete (8–11 failed). Every verified partition has nonzero unknown duration under the frozen contract. Contract v2 and analyzer v4 approved; `thread-sleeping` classification applied prospectively to fully observed `S` intervals. No Baseline 02 result is reclassified retroactively.

### Investigation conclusion (current)

T012 disposition: **inconclusive/open**. The warm baseline quota was not completed; unknown partition time is present in every verified attempt. No material/not-material attribution is possible. T013–T017 (optimization branch) are not started. T024 (backlog deletion) remains gated.

---

## Part 2 — Git commit analysis

### Directory size overview (as of 2026-11-02T01:00 UTC)

| File type | Count | Total size |
|---|---|---|
| CSV | 1,344 | **~856 MB** |
| JSON | 432 | 10.7 MB |
| Markdown (.md) | 219 | 0.6 MB |
| PowerShell (.ps1) | 138 | 1.7 MB |
| SQL (.sql) | 27 | 0.07 MB |
| Trace config (.textproto) | 6 | 0.01 MB |
| Text logs (.txt) | 56 | 0.18 MB |
| Module / gitattributes | 2 | < 0.01 MB |
| **Total** | **2,224** | **~869 MB** |

### Existing `.gitattributes`

`artifacts/.gitattributes` already contains:
```
* filter=lfs diff=lfs merge=lfs -text
.gitattributes -filter diff merge text
```
All files committed to `artifacts/` are already routed through **Git LFS**. This is correct and should not be changed. The question is which files to `git add` at all.

### CSV families by size

| Family | Files | Size |
|---|---|---|
| Calibration CSVs (all runs, 01–26) | ~280 | ~720 MB |
| — of which `*-raw-session-observations.csv` | ~26 | ~680 MB dominant |
| Baseline per-session detail CSVs | ~387 | ~86 MB |
| — of which `*-thread-states.csv` | ~50 | ~50 MB |
| — of which `*-scheduler.csv` (per attempt) | ~50 | ~15 MB |
| Pilot / exploratory CSVs | ~783 | ~39 MB |
| Parser attribution CSVs | ~14 | ~1 MB |

---

### ✅ COMMIT — irreplaceable provenance (all small)

| Pattern | What it is | Notes |
|---|---|---|
| `specs/062-fido2-performance-investigation/*.md` | All top-level docs: spec, plan, tasks, handoffs, protocol, contract, evidence | All small |
| `specs/062-fido2-performance-investigation/checklists/` | Requirements checklist | Small |
| `specs/062-fido2-performance-investigation/contracts/` | Investigation contract | Small |
| `artifacts/*.md` | Reports, dispositions, predeclarations, proposals, protocols | All small |
| `artifacts/*.ps1` | Runners, identity recorders, schedulers, analyzers, summarizers | All small |
| `artifacts/*.sql` | Calibration and analysis queries — define what was computed | All small |
| `artifacts/*.textproto` | Trace config files — part of every frozen identity | All small |
| `artifacts/*.psm1` | Shared module | Small |
| `artifacts/.gitattributes` | LFS policy — already committed | |
| `artifacts/*.txt` | Fixed-label diagnostic log events per probe/baseline attempt | All ~3 KB |
| `artifacts/*-identity.json` | Frozen run identities — immutable provenance | All small |
| `artifacts/*-result.json` | Authoritative per-attempt probe/baseline outcomes | All ~1–5 KB |
| `artifacts/*-schedule.csv` | Immutable randomized session schedules | ~50 KB each |
| `artifacts/*-session-summary.csv` | Core statistical record: one row/session, C, H, PASS/FAIL | Small |
| `artifacts/*-clock-observations.csv` | Per-session clock envelope values | Small |
| `artifacts/*-control-observations.csv` | Per-session H (paired control) values | Small |
| `artifacts/*-trace-health.csv` | Proves zero parser failures per session | Small–medium |
| `artifacts/*-failures.csv` | Failure records on stopped runs | Small |
| `artifacts/*-print-categories.csv` | Per-session print aggregate (parser health detail) | Small |
| `artifacts/*-scheduler-counts.csv` | Proves all 8 scheduler families present per session | Small |
| `artifacts/*-clock-snapshots.csv` | BOOTTIME snapshots per session | Small |
| `artifacts/*-analysis.json` (**v1 only**, baseline) | Small authoritative per-attempt summary (2 KB) | Keep v1 |
| `artifacts/*-analysis-v4.json` (baseline, latest) | Most recent per-attempt analysis (150–200 KB) | Keep latest |

**Estimated total committed (excluding raw-session-observations): ~25–30 MB via LFS.**

---

### ✅ COMMIT VIA LFS — large but contractually required

| Pattern | Why required | Size |
|---|---|---|
| `artifacts/*-raw-session-observations.csv` | Full measurement record for all calibration sessions — the investigation contract requires retention | **~680 MB** across all runs |

> If repository size is a hard constraint, these are the primary candidates for an external evidence store (e.g., a shared drive or object storage), with only their SHA-256 checksums committed to Git. However, the investigation contract explicitly requires retention — confirm before excluding from Git.

---

### ❌ DO NOT COMMIT — reproducible or superseded

| Pattern | Reason | Size saved |
|---|---|---|
| `artifacts/*-analysis-v2.json`, `*-analysis-v3.json` | **Superseded** — v1 (authoritative, small) and v4 (latest) are sufficient | ~40 MB |
| `artifacts/*-thread-states.csv` (per baseline attempt) | Large raw scheduler table per attempt — derivable from raw trace (already deleted). The partition is captured in the analysis JSON | ~50 MB |
| `artifacts/*-scheduler.csv` (per baseline attempt, e.g. `baseline-02-cold-01-scheduler.csv`) | Large raw sched-switch events per attempt — same reasoning as thread-states | ~15 MB |
| `artifacts/*-clocks.csv` (per baseline attempt, tiny) | Per-attempt clock rows already in analysis JSON | Negligible |
| `artifacts/*-events.csv` (per baseline attempt, tiny) | Per-attempt event rows already in analysis JSON | Negligible |
| `artifacts/*-health.csv` (per baseline attempt, tiny) | Per-attempt health already in analysis JSON | Negligible |
| `artifacts/*-markers.csv` (per baseline attempt, tiny) | Per-attempt marker rows already in analysis JSON | Negligible |
| `artifacts/*-prints.csv` (per baseline attempt, tiny) | Per-attempt print rows already in analysis JSON | Negligible |

> **Important distinction:** The calibration `*-scheduler-counts.csv`, `*-trace-health.csv` etc. are **aggregate summaries** (one row per session) and must be committed. Only the per-attempt baseline raw-query dumps listed above are excluded.

> **Do NOT add a blanket `*.csv` to `.gitignore`** — that would exclude the required calibration session summaries. Exclusions must be scoped to the specific per-attempt baseline suffixes above.

---

### Recommended `git add` commands

```powershell
cd d:\octav\source\repos\Chimali

# ── Top-level spec documents ─────────────────────────────────────────────────
git add specs/062-fido2-performance-investigation/*.md
git add specs/062-fido2-performance-investigation/checklists/
git add specs/062-fido2-performance-investigation/contracts/

# ── All non-CSV artifacts (all safe) ─────────────────────────────────────────
git add "specs/062-fido2-performance-investigation/artifacts/*.md"
git add "specs/062-fido2-performance-investigation/artifacts/*.ps1"
git add "specs/062-fido2-performance-investigation/artifacts/*.sql"
git add "specs/062-fido2-performance-investigation/artifacts/*.textproto"
git add "specs/062-fido2-performance-investigation/artifacts/*.psm1"
git add "specs/062-fido2-performance-investigation/artifacts/*.txt"
git add "specs/062-fido2-performance-investigation/artifacts/.gitattributes"
git add "specs/062-fido2-performance-investigation/artifacts/*.json"

# ── Required calibration + baseline summary CSVs (by suffix) ─────────────────
git add "specs/062-fido2-performance-investigation/artifacts/*-schedule.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-session-summary.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-clock-observations.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-control-observations.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-trace-health.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-failures.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-print-categories.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-scheduler-counts.csv"
git add "specs/062-fido2-performance-investigation/artifacts/*-clock-snapshots.csv"

# ── Large contractually required calibration data (goes to LFS) ──────────────
git add "specs/062-fido2-performance-investigation/artifacts/*-raw-session-observations.csv"

# ── DO NOT add (superseded / derivable from analysis JSON) ───────────────────
# *-analysis-v2.json
# *-analysis-v3.json
# *-thread-states.csv    (per baseline attempt)
# *-scheduler.csv        (per baseline attempt)
# *-clocks.csv           (per baseline attempt)
# *-events.csv           (per baseline attempt)
# *-health.csv           (per baseline attempt)
# *-markers.csv          (per baseline attempt)
# *-prints.csv           (per baseline attempt)
```

### Alternative: add a scoped `.gitignore`

Add `specs/062-fido2-performance-investigation/artifacts/.gitignore`:
```gitignore
# Superseded baseline analysis versions
*-analysis-v2.json
*-analysis-v3.json

# Large per-attempt raw query dumps (data is captured in analysis JSON)
*-thread-states.csv
*-scheduler.csv
*-clocks.csv
*-events.csv
*-health.csv
*-markers.csv
*-prints.csv
```
Then `git add specs/062-fido2-performance-investigation/artifacts/` commits everything except the excluded patterns. Confirm with the third-party agent before adding this file.

---

### Size estimate after applying recommendations

| Category | Approx. committed size |
|---|---|
| All .md, .ps1, .sql, .txt, .textproto, .json | ~15 MB |
| Calibration schedules + summary + observation CSVs | ~10 MB |
| Calibration trace-health + print + scheduler + snapshot CSVs | ~70 MB |
| Calibration `*-raw-session-observations.csv` (LFS) | ~680 MB |
| **Total** | **~775 MB via LFS** |
| **Excluded** (thread-states, per-attempt scheduler, v2/v3 analysis) | **~105 MB saved** |

---

## Part 3 — Next steps

### Active work (do not interrupt)

- **Calibration 26 is running** (333/874 sessions PASS as of snapshot). Candidate: C = 530,677 ns, H = 17,051,771 ns (same as Cal 24). Max C observed = 68,073 ns; max H = 8,589,584 ns. Zero exceedances so far. Wait for it to complete before touching device or output files.

### After Calibration 26 completes

**If PASS:**
1. Write `f062-asus35-calibration-26-execution-report.md` and `*-run-disposition.md`.
2. Update `calibration-protocol.md` with the PASS result.
3. Assess whether Cal 26's frozen identity covers the current target APK identity. If yes, determine what task it unlocks (per the Cal 26 approval summary and policy amendment).
4. Proceed according to the approved policy amendment (`f062-asus35-calibration-26-policy-amendment-proposal.md`).

**If FAIL:**
1. Record execution report, disposition, protocol update.
2. New candidate = stopping exceedance value.
3. Fresh owner approval required before any new holdout.

### Warm baseline supplement

T009 remains open — the 10-warm quota was not completed in Baseline 02 (7/10 verified; warm-08 through warm-11 failed). Baselines 03–07 produced supplementary warm attempts. Assess whether the composite analysis (`f062-asus35-baseline03-baseline07-composite-analysis.md`) and the supplement dispositions are sufficient to close T009, or whether additional eligible warm captures are needed.

The frozen contract requires:
- Each warm attempt: unique mapping, `valid=true complete=true`, all stage markers, all scheduler families, BOOTTIME snapshots, zero health failures, verified raw-trace cleanup.
- Every verified partition must have zero unknown duration. (Currently all attempts have nonzero unknown time — this was the basis for the inconclusive T012 disposition.)

### T012 disposition path

The current disposition is **inconclusive/open** because:
1. Warm baseline quota incomplete.
2. Every verified partition contains nonzero unknown duration.

To move toward a conclusive disposition, either:
- Obtain eligible warm captures with zero unknown partition time, or
- Formally close T009 as infeasible and record a final open disposition in T012.

Either path requires owner decision before proceeding to T013–T017 or T024.

### Commit timing

**Do not commit** while Calibration 26 is running — the active holdout is writing to output CSVs in `artifacts/`. Commit after the holdout completes and its execution report and disposition have been written.

---

## Part 4 — Key artifact references

| Artifact | Purpose |
|---|---|
| [`calibration-protocol.md`](calibration-protocol.md) | Authoritative calibration history and frozen allowance |
| [`tasks.md`](tasks.md) | Task checklist with current `[X]` / `[ ]` state |
| [`handoff-2026-10-31-b.md`](handoff-2026-10-31-b.md) | Detailed handoff from session B (Cal 22 PASS) |
| [`artifacts/f062-asus35-calibration-24-execution-report.md`](artifacts/f062-asus35-calibration-24-execution-report.md) | Cal 24 PASS — frozen u = 17,582,448 ns |
| [`artifacts/f062-asus35-calibration-26-approval-summary.md`](artifacts/f062-asus35-calibration-26-approval-summary.md) | Cal 26 policy amendment and approval |
| [`artifacts/f062-asus35-baseline-02-run-disposition.md`](artifacts/f062-asus35-baseline-02-run-disposition.md) | Baseline 02 disposition (incomplete, inconclusive) |
| [`artifacts/f062-asus35-baseline-02-run-progress.md`](artifacts/f062-asus35-baseline-02-run-progress.md) | Baseline 02 attempt-by-attempt progress record |
| [`artifacts/f062-asus35-baseline03-baseline07-composite-analysis.md`](artifacts/f062-asus35-baseline03-baseline07-composite-analysis.md) | Baseline 03–07 composite warm supplement analysis |
| [`contracts/investigation-contract.md`](contracts/investigation-contract.md) | Governing retention and evidence requirements |
