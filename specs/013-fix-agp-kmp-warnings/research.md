# Research: AGP and KMP Plugin Migration

## Decisions & Rationale

### 1. KMP-Android Plugin Migration
- **Decision**: Replace `com.android.library` with `com.android.kotlin.multiplatform.library` in KMP modules.
- **Rationale**: `com.android.library` is deprecated for KMP usage starting with AGP 9.0.0. The new plugin is specifically optimized for KMP source set structures.
- **Impacted Modules**: 
  - `:feature:fido2`
  - `:core:security`
  - `:core:domain`
  - `:core:common`

### 2. Built-in Kotlin Migration
- **Decision**: Remove `org.jetbrains.kotlin.android` from `:feature:vault` and other non-KMP Android modules. Remove `android.builtInKotlin` and `android.newDsl` from `gradle.properties`.
- **Rationale**: AGP 9.0+ provides built-in Kotlin support. The `kotlin.android` plugin is redundant and triggers deprecation warnings.
- **Impacted Modules**: 
  - `:feature:vault`
  - Any other module currently using `libs.plugins.kotlin.android`.

### 3. Build Logic Verification
- **Decision**: Perform a module-by-module dependency analysis before applying changes.
- **Rationale**: Ensure that `com.android.kotlin.multiplatform.library` does not break custom build logic or third-party plugins (like SQLDelight or KSP) that might expect `main` source sets.

## Alternatives Considered

- **Stay on legacy plugins**: Evaluate staying on legacy plugins with suppressed warnings.
  - **Verdict**: Rejected. AGP 9.2.0 is highly recommended for modern KMP features, and warnings indicate future hard breaks in AGP 10.0.
- **Partial Migration**: Migrate only warnings mentioned.
  - **Verdict**: Rejected. Consistency across the project is safer and avoids confusing build states.

## Next Steps
- Verify `libs.versions.toml` contains the required plugin definition (Done: `android-kotlin-multiplatform-library`).
- Systematically update modules.
- Run full build and tests.
