# Quickstart: Detekt MaxLineLength Enforcement

This guide explains how to verify the new line length enforcement.

## Verification Steps

### 1. Apply Configuration Changes
The primary change is in `config/detekt/detekt.yml` and the root `build.gradle.kts`.

### 2. Run Project-Wide Check
To check the entire project (including tests and all modules):
```powershell
./gradlew detekt
```

### 3. Target Specific Modules
If you are working on a specific feature (e.g., Vault):
```powershell
./gradlew :feature:vault:detekt
```

## Troubleshooting

### Violations in Tests
If you see `MaxLineLength` violations in test files, refactor them using the wrapping patterns defined in `data-model.md`. **Do not add new excludes.**

### Raw Strings
If a long string is a constant and shouldn't be broken, ensure it is defined as a Raw String (`"""..."""`) as these are excluded from the check.

## Integration with Local CI
The `tools/local-ci.ps1` script will now catch these violations automatically. Always run it before committing:
```powershell
./tools/local-ci.ps1
```
