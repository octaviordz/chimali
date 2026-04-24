# Research: Detekt MagicNumber Enforcement

## Findings

### 1. Detekt Configuration Tuning
The current configuration is very strict. To balance quality with noise reduction (as decided in the clarification session), the following settings will be applied:

- **`ignoreAnnotation: true`**: Allows numeric literals in annotations (e.g., `@ColorInt`, `@Size`).
- **`ignoreEnums: true`**: Allows numeric IDs in enum constructors.
- **`ignoreRanges: true`**: Allows ranges like `1..10`.
- **`ignoreNumbers` Expansion**: Common Android/JVM version numbers (17, 21, 24, 30, 31, 33, 34, 35) will be added to the ignore list to avoid excessive refactoring in build scripts.

### 2. Magic Numbers in build.gradle.kts
Build scripts currently use literals for:
- `compileSdk = 35`
- `minSdk = 28`
- `jvmTarget = 17`

**Decision**: These will be ignored via `ignoreNumbers` to keep build scripts clean and standard.

### 3. Refactoring Patterns
For magic numbers that *must* be refactored (e.g., timeouts, mock data in tests), the following patterns will be used:

- **Local Constants**: `private const val ...` at the top of the file for values used only within that file.
- **Named Arguments**: Using named arguments can sometimes satisfy Detekt if `ignoreNamedArgument` is true (which it is), but for complex logic, a constant is preferred.
- **Shared Constants**: For values used across modules, we will evaluate if a shared `Constants` or `TestConstants` object is appropriate.

## Alternatives Considered

- **Alternative 1: Global `Versions.kt`**: Moving all SDK versions to a central file. 
  - *Rationale*: While clean, it deviates slightly from standard "new" Gradle patterns unless using Version Catalogs. Since the project uses Version Catalogs for plugins/libs, we will stick to standard property assignments and ignore them in Detekt to avoid over-engineering build script configuration.
- **Alternative 2: Strict Enforcement (No ignores)**: 
  - *Rationale*: Rejected due to high noise-to-value ratio for standard language constructs (enums, ranges).

## Decision Matrix

| Constraint | Chosen Approach | Rationale |
|------------|-----------------|-----------|
| .kts files | Enforce + Ignore SDKs | Ensures logic in build scripts is clean while allowing standard config. |
| Generated Code | Exclude | Impossible to refactor third-party/tool-generated code. |
| Annotations | Ignore | Common metadata pattern; constants don't add much value here. |
