# Research: Detekt Rule Hardening

## Decision: Enforce Rules Project-Wide

We will proceed with enforcing all requested Detekt rules across all modules, including test source sets. The initial analysis revealed approximately 150+ violations project-wide, with the majority concentrated in the `feature:fido2` test suites.

## Rationale

- **WildcardImport**: Enforcing fully qualified imports improves code readability and avoids namespace collisions, especially in large Compose-based UIs and test suites.
- **UnusedImports**: Clean code maintenance.
- **NewLineAtEndOfFile**: POSIX compliance and better tool compatibility.
- **UnsafeCallOnNullableType**: Prevents potential `NullPointerException` at runtime. While common in tests, enforcing it encourages safer testing patterns (e.g., `checkNotNull` or `requireNotNull` with descriptive messages).
- **LateinitUsage**: `lateinit` can hide initialization bugs. In tests, it can be replaced with nullable properties or better dependency injection patterns.
- **EmptyDefaultConstructor**: Reduces unnecessary boilerplate and improves class design clarity.

## Findings by Rule

### WildcardImport
- **Occurrence**: High (especially in Compose UI and `kotlin.test.*`).
- **Path to Resolution**: Bulk replacement of `kotlin.test.*` and explicit expansion of `androidx.compose.*` imports.

### LateinitUsage
- **Occurrence**: High in tests.
- **Path to Resolution**: Refactor tests to use nullable properties with explicit null checks or initialize in `@BeforeTest` methods where appropriate.

### UnusedImports / NewLineAtEndOfFile / EmptyDefaultConstructor
- **Occurrence**: Low.
- **Path to Resolution**: Direct manual fixes.

### UnsafeCallOnNullableType (!!)
- **Occurrence**: Moderate in tests.
- **Path to Resolution**: Replace with `checkNotNull()` or safe calls with default values.

## Exclusions Decision

- **Rule-specific exclusions**: All existing rule-specific exclusions (e.g., for `feature/vault` or `test` paths) will be removed.
- **Generated Code**: We will rely on the global `build.excludes` and `config.excludes` in `detekt.yml` to ignore files in `**/build/**` and `**/generated/**`.
- **Build Scripts (.kts)**: Based on the updated specification, `.kts` files will be excluded from the newly hardened rules if the volume of violations is excessive (e.g., in `MagicNumber` or `WildcardImport` within build scripts).

## Alternatives Considered

- **Alternative**: Keep exclusions for tests.
- **Evaluation**: Rejected. The project constitution (Principle III) emphasizes uncompromising quality. Enforcing these rules in tests ensures that test code is held to the same high standard as production code.
