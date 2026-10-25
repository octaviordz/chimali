# Handoff: Enforce Coverage Targets (Feature 058)

**Branch**: `codex/058-enforce-coverage-targets`  
**Latest Commit**: `aa1408dd`  
**Date Written**: 2026-10-25  
**Status**: Implementation ~95% complete — one hard-floor failure remains.

---

## 1. Current State

### Coverage Verification — Last Run

| Run ID | `42bb76a8-47c1-49ba-9c8b-9afce9c281df` |
|--------|---------------------------------------|
| Bundle | `D:\temp\chimali-coverage-runs\42bb76a8-47c1-49ba-9c8b-9afce9c281df` |
| Assessment | `D:\temp\chimali-coverage-runs\assessments\42bb76a8-47c1-49ba-9c8b-9afce9c281df\decision.json` |
| Collection | Complete |
| Tests | Passed |
| Overall | **FAIL** |

### Module Results (Tier 1 floor: 60% instructions AND 60% branches)

| Module | Tier | Instructions | Branches | Status |
|--------|------|-------------|----------|--------|
| `:core:security` | 1 | 76.03% (3385/4452) | 61.92% (239/386) | ✅ Pass |
| `:core:domain` | 1 | 71.23% (4923/6911) | 71.51% (399/558) | ✅ Pass |
| `:core:data` | 1 | 86.87% (1158/1333) | 73.33% (44/60) | ✅ Pass |
| `:feature:fido2` | 1 | 61.57% (53008/86100) | **58.02% (3206/5526)** | ❌ **FAIL** |
| `:feature:authenticator` | 1 | 0/0 (`AnalyzedZeroProofV1`) | N/A | ✅ Pass |
| `:core:database` | 2 | 65.65% | Reported | ✅ Pass |
| `:core:common` | 2 | 21.31% | Reported | ✅ Pass |
| `:app` | 2 | 36.97% | Reported | ✅ Pass |
| **Repository aggregate** | — | **54.60%** | — | ✅ Pass |

### What Remains

**One failing gate**: `:feature:fido2` **branch coverage 58.02%** vs floor **60.00%**.

- Missed branches: **2,320**; Covered: **3,206**; Total: **5,526**
- To reach 60%: need **3,316 covered** → need **≥ 110 more branches covered**
- As a percentage gap: ~**1.98 pp** to close

> [!IMPORTANT]
> Only `:feature:fido2` branches are failing. All other gates (instructions, repository aggregate, Tier 2 modules) are passing.

---

## 2. Remaining Work

### Immediate Action: Close `:feature:fido2` Branch Coverage Gap

The Jacoco XML report is at:
```
D:\temp\chimali-coverage-runs\42bb76a8-47c1-49ba-9c8b-9afce9c281df\reports\modules\feature_fido2.xml
```

**Step 1**: Identify the highest-missed-branch classes by parsing this XML:

```powershell
[xml]$xml = Get-Content 'D:\temp\chimali-coverage-runs\42bb76a8-47c1-49ba-9c8b-9afce9c281df\reports\modules\feature_fido2.xml'
$xml.report.package.class | ForEach-Object {
    $bc = $_.counter | Where-Object { $_.type -eq 'BRANCH' }
    if ($bc) {
        $missed = [int]$bc.missed; $covered = [int]$bc.covered; $total = $missed + $covered
        if ($total -gt 0) { [PSCustomObject]@{ Class = $_.name; Missed = $missed; Pct = [Math]::Round($covered*100/$total,1) } }
    }
} | Sort-Object Missed -Descending | Select-Object -First 25 | Format-Table -AutoSize
```

**Step 2**: Add targeted tests to the files most likely to cover untested branches. Based on prior analysis, focus on:
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/` — host test directory
- High-value targets are typically ViewModel, UseCase, and service classes with many conditional branches

**Step 3**: Verify locally:
```powershell
.\gradlew.bat --no-configuration-cache :feature:fido2:testAndroidHostTest
```

**Step 4**: Run fresh capture and verify:
```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/capture.ps1 `
  -RepositoryRoot $PWD `
  -SourceRevision (git rev-parse HEAD).Trim() `
  -Purpose Initial `
  -HarnessRoot $PWD `
  -DeviceSerial emulator-5554 `
  -OutputRoot D:\temp\chimali-coverage-runs

pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/verify.ps1 `
  -RunBundle <new-run-bundle-path> `
  -PolicyPath "D:\octav\source\repos\Chimali\tools\coverage\policy.json"
```

### After All Floors Pass: Final Documentation

Once `:feature:fido2` branches reach ≥60%, complete these tasks:

1. **Update `specs/058-enforce-coverage-targets/test-selection.md`** with:
   - Final test cases `COV-007` through `COV-009` (or higher) for the new tests added this session
   - `Verified` status for `CredentialEncryptionServiceTest`, `Fido2DomainModelsTest`, `Fido2ErrorHandlerTest`
   - Final measured deficits and verification outcomes

2. **Update `specs/058-enforce-coverage-targets/implementation-evidence.md`** with:
   - New run bundle ID and path
   - Final raw counters and percentages for all modules
   - All ten exact TRD evidence keys for each record

3. **Update `specs/058-enforce-coverage-targets/tasks.md`**:
   - Check `T088`, `T104`, `T105`, `T107`, `T108`
   - Keep `T046`, `T047`, `T048`, `T049` unchecked (external GitHub App/runner prerequisites — unmet blockers)

4. **Run local CI and commit final documentation**:
   ```powershell
   pwsh -NoProfile -ExecutionPolicy Bypass -File tools/local-ci.ps1
   git add specs/058-enforce-coverage-targets/
   git commit -m "docs(coverage): finalize test-selection and implementation evidence for T088, T104-T108"
   ```

---

## 3. What Was Done This Session

### Commits (this session)

| Commit | Message |
|--------|---------|
| `aa1408dd` | `test(fido2): remediate feature:fido2 coverage deficits via encryption service, domain models, and error handling tests (T104, T105)` |
| `7896703e` | `fix(coverage): fix suite outcome evaluation in capture.ps1 and coverage-common.ps1` |
| `9ad6fb94` | `fix(coverage): replace invalid elvis operator in JUnit XML parsing in capture.ps1` |
| `5ca29ea8` | `test(coverage): remediate branch and instruction deficits in Tier 1 modules (T104, T105)` |

### Files Modified This Session

| File | Change |
|------|--------|
| [`feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionService.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionService.kt) | Added `cachedMasterKey` to fix per-call key regeneration defect |
| [`feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionServiceTest.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionServiceTest.kt) | **New** — 12 tests covering AES-GCM, HKDF, rotation, data class contracts |
| [`feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/model/Fido2DomainModelsTest.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/model/Fido2DomainModelsTest.kt) | **New** — Comprehensive domain model tests (`AuthenticatorSelectionCriteria`, `PublicKeyCredentialDescriptor`, `PublicKeyCredentialUserEntity`, `PublicKeyCredentialRpEntity`, `AssertionObject`) |
| [`feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/error/Fido2ErrorHandlerTest.kt`](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/error/Fido2ErrorHandlerTest.kt) | Extended — All `DomainError` and `Fido2Exception` mappings, `isSilentRetryable` |
| [`tools/coverage/capture.ps1`](file:///d:/octav/source/repos/Chimali/tools/coverage/capture.ps1) | Fixed `if ($eval.Status -ne "Passed")` check; fixed JUnit XML `?:` parser error |
| [`tools/coverage/coverage-common.ps1`](file:///d:/octav/source/repos/Chimali/tools/coverage/coverage-common.ps1) | Added `Passed = ($status -eq "Passed")` to `Test-SuiteOutcome` return value |

---

## 4. Key Domain Invariants (Don't Break These)

These were discovered through test failures — do NOT violate them in new tests:

| Invariant | Rule |
|-----------|------|
| `CredentialId.fromByteArray` | Requires `bytes.size in 16..1023`; shorter arrays throw `IllegalArgumentException` |
| `SensitiveCredentialText.erase()` | Fills chars with `\u0000` — check `text.all { it == '\u0000' }`, NOT `text.length == 0` |
| `PublicKeyCredentialUserEntity` | Requires non-blank `name` AND `displayName` |
| `PublicKeyCredentialRpEntity.toString()` | Returns `"PublicKeyCredentialRpEntity([sensitive RP fields redacted])"` |
| `CredentialEncryptionService.getMasterKey()` | Now caches key in `cachedMasterKey`; decrypt with same instance as encrypt |

---

## 5. Environment and Devices

| Resource | Detail |
|----------|--------|
| Branch | `codex/058-enforce-coverage-targets` |
| HEAD | `aa1408dd` |
| Emulator | `emulator-5554` — `dev35_aosp_atd_x86_64_Pixel_2` (API 35 ATD x86_64) — daemon running |
| Physical device | `R7AIB7002901AZ3` — ASUS_AI2302, API 35 |
| Coverage output root | `D:\temp\chimali-coverage-runs\` |
| Last run bundle | `D:\temp\chimali-coverage-runs\42bb76a8-47c1-49ba-9c8b-9afce9c281df` |
| Last assessment | `D:\temp\chimali-coverage-runs\assessments\42bb76a8-47c1-49ba-9c8b-9afce9c281df\` |

**Start the emulator** if not running:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd dev35_aosp_atd_x86_64_Pixel_2 -no-window -no-audio
```

**Check devices**:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

---

## 6. Unmet External Prerequisites (Explicitly Blocked — Do Not Simulate)

These tasks (`T046`, `T047`, `T048`, `T049`) remain permanently blocked pending external provisioning. They are NOT implementation work; never simulate or forge passes:

| Task | Blocker |
|------|---------|
| T046 | Disposable Windows/device GitHub Actions runner provisioning |
| T047 | Protected `codex/coverage-evidence` branch + App/publication permissions |
| T048 | Branch protection rule requiring `coverage-integration` check from dedicated App |
| T049 | Live rejection demonstration using the dedicated App reporter |

---

## 7. Key Commands Quick Reference

```powershell
# Run fido2 host tests
.\gradlew.bat --no-configuration-cache :feature:fido2:testAndroidHostTest

# Run local CI (ktlint, detekt, lint, unit tests, host tests)
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/local-ci.ps1

# Run coverage capture
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/capture.ps1 `
  -RepositoryRoot $PWD `
  -SourceRevision (git rev-parse HEAD).Trim() `
  -Purpose Initial -HarnessRoot $PWD `
  -DeviceSerial emulator-5554 `
  -OutputRoot D:\temp\chimali-coverage-runs

# Run coverage verify (use absolute path for PolicyPath)
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/verify.ps1 `
  -RunBundle "D:\temp\chimali-coverage-runs\<run-id>" `
  -PolicyPath "D:\octav\source\repos\Chimali\tools\coverage\policy.json"

# Run fixture test suite
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1
```

---

## 8. Tasks Checklist Status

| Range | Status |
|-------|--------|
| T001–T084 | ✅ All complete |
| T085–T103 | ✅ All complete |
| **T104, T105** | ✅ Complete (this session — fido2 instruction floor closed) |
| **T106** | ✅ Complete |
| **T107** | ✅ Capture run complete — verification fails on fido2 branches |
| **T088, T108** | ⏳ Pending — requires all floors passing before final doc update |
| T046–T049 | 🚫 Blocked — external prerequisites (GitHub App, runner, branch protection) |

---

> [!NOTE]
> The emulator daemon (`task-656`) launched at the start of this session may still be running. In a new session, start the emulator explicitly before running the capture script.

> [!CAUTION]
> The last coverage run bundle at `D:\temp\chimali-coverage-runs\42bb76a8-...` is sealed and immutable. Never edit files inside it. All new assessments go to `D:\temp\chimali-coverage-runs\assessments\<run-id>\`.
