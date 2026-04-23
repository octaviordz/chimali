# Quickstart: Kotlin Formatting & Quality Checks

## Common Commands

### 1. Format Code
Automatically fix all style violations that can be auto-corrected.
```bash
./gradlew ktlintFormat
```

### 2. Check Code Style
Verify that all files comply with the style guide without making changes.
```bash
./gradlew ktlintCheck
```

### 3. Run Local CI
Run the full quality pipeline (Clean, Lint, Tests).
```bash
./tools/local-ci.ps1
```

## Troubleshooting

### Import Ordering Failures
If `ktlintCheck` fails on import ordering even after running `ktlintFormat`, ensure your IDE (IntelliJ/Android Studio) is configured to use the project's `.editorconfig` (Settings > Editor > Code Style > Kotlin).

### Generated Code Violations
If you see violations in generated directories (e.g., `build/generated/`), check the root `build.gradle.kts` to ensure the exclusion paths are correctly defined in the `ktlint` configuration block.
