# Quickstart: Verifying Detekt Rules Upgrade

## Verification Steps

### 1. Run Static Analysis
Execute the local CI pipeline to check for violations of the new rules:
```powershell
./tools/local-ci.ps1
```
Or run Detekt specifically:
```powershell
./gradlew detekt
```

### 2. Verify Rule Application
Check `config/detekt/detekt.yml` to ensure the rules from the **Configuration Reference** are correctly applied with the specified thresholds.

### 3. Check for Suppressions
Verify that no target rules are suppressed in the codebase:
```powershell
# Example: Search for MagicNumber suppressions
grep -r "@Suppress(\"MagicNumber\")" .
```

## Remediation Workflow
If new violations are found:
1. **MagicNumber**: Extract to a constant in a companion object or file level.
2. **LongMethod**: Use IDE "Extract Function" (Ctrl+Alt+M / Cmd+Opt+M) to decompose the method.
3. **LargeClass**: Decompose into smaller classes or components.
