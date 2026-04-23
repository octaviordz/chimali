# Research: KtLint Configuration and Usage

## Findings

### Current Status
- **AGP Migration Impact**: The project recently migrated to AGP 9.2.0 and structural changes (moving code to `androidMain`) have introduced style violations, specifically in import ordering.
- **Pre-commit Failures**: The `pre-commit` hook (calling `local-ci.ps1`) runs `./gradlew ktlintCheck`, which is currently failing for several test files in the `feature:fido2` module.
- **Plugin Application**:
  - `ktlint` plugin (version `12.1.0`) is defined in `libs.versions.toml`.
  - Applied in `feature:fido2/build.gradle.kts`.
  - Root `build.gradle.kts` defines it with `apply false`.
  - Core modules (`core:common`, `core:security`, `core:ui`) do **not** currently apply the plugin, violating Principle III of the Constitution.

### Configuration
- **.editorconfig**: No `.editorconfig` exists in the root directory. `ktlint` is likely using default Kotlin coding standards.
- **Custom Rules**: No custom `ktlint` configuration blocks found in `build.gradle.kts` files.

### Identified Violations
- `/feature/fido2/src/androidTest/kotlin/com/chimali/fido2/presentation/management/CredentialListScreenTest.kt` (Import ordering)
- `/feature/fido2/src/test/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDaoTest.kt` (Import ordering)

## Decisions
- **Decision**: Integrate `ktlint` at the root level to ensure all modules are covered.
- **Rationale**: Principle III of the Constitution makes `ktlint` mandatory for the entire project. Centralizing configuration ensures consistency.
- **Decision**: Create a `.editorconfig` file.
- **Rationale**: Explicitly defining rules (like import ordering) prevents "it works on my machine" issues and allows fine-tuning for KMP.
- **Decision**: Use `./gradlew ktlintFormat` to fix the current blockers.
- **Rationale**: Immediate requirement to unblock the KMP stabilization commit.

## Alternatives Considered
- **Fixing manually**: Rejected as too slow and error-prone.
- **Disabling pre-commit hooks**: Rejected as it violates the project's quality governance (Principle VIII).
