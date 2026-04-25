# Quickstart: Detekt Rule Hardening

## Overview
This feature hardens the project's static analysis by enforcing strict rules across all modules and removing legacy exclusions.

## How to Verify Changes
1. **Run Detekt**: Execute `./gradlew detekt` from the root directory.
2. **Run Local CI**: Execute `./tools/local-ci.ps1` to ensure all checks pass.

## Common Fixes
- **WildcardImport**: Replace `import x.y.*` with explicit imports.
- **UnusedImports**: Remove unused imports (IDE "Optimize Imports" usually handles this).
- **NewLineAtEndOfFile**: Ensure every `.kt` file ends with an empty line.
- **UnsafeCallOnNullableType**: Replace `!!` with `checkNotNull()`, `requireNotNull()`, or safe calls.
- **LateinitUsage**: Replace `lateinit var` with nullable types or initialize immediately.
- **EmptyDefaultConstructor**: Remove explicit `constructor()` if it's empty and the default.
