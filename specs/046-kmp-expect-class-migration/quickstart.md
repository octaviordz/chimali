# Quickstart: KMP Expect/Actual Class Migration

**Branch**: `046-kmp-expect-class-migration`

## Phase A — KMP migration (complete)

```powershell
rg "@Suppress\(""EXPECT_ACTUAL" --glob "*.kt"
# Expected: no matches

rg "expect class|actual class" feature/fido2/src --glob "*.kt"
# Expected: no matches
```

## Phase B — Green CI (required before merge)

```powershell
# Iterative (fido2 host tests — known blocker)
.\gradlew :feature:fido2:compileAndroidHostTest

# Module static analysis
.\gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck

# Full gate (must exit 0)
.\tools\local-ci.ps1
```

### Constraints (from clarify session)

- **Tests**: Update or remove if obsolete; do not `@Ignore` / `@Disabled` / skip.
- **Detekt/ktlint**: Source fixes only — no baseline or `detekt.yml` edits.
- **Production**: Structural fixes in any module that fails CI; no feature logic changes.

## File mapping (KMP — done)

| Before | After |
|--------|-------|
| `expect class PlatformBluetoothHid` | `interface PlatformBluetoothHid` |
| `actual class` (android/ios) | `AndroidPlatformBluetoothHid` / `IosPlatformBluetoothHid` |
| `expect class PlatformLock` | `interface PlatformLock` |
| `actual class PlatformLock` | `AndroidPlatformLock` / `IosPlatformLock` |

## Next command

`/speckit-tasks` — generate `tasks.md` for Track B (CI remediation).
