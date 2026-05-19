# Research: KMP Resource Migration (BIP39 Wordlist)

**Feature**: 044-kmp-resource-migration  
**Date**: 2026-05-18

## Research Question 1: KMP Resource Loading Strategy

**Context**: The `bip39_english.txt` file currently resides in `androidMain/resources/` and is loaded via `javaClass.classLoader?.getResourceAsStream("bip39_english.txt")`. We need to determine the correct approach for making this resource available from `commonMain` while maintaining platform-specific loading.

### Decision: `expect`/`actual` function with `commonMain/resources/` placement

### Rationale

1. **JVM/Android classpath resolution**: When a resource file is placed in `commonMain/resources/`, the Kotlin Multiplatform Gradle plugin packages it into the JVM classpath for Android and JVM targets. This means `ClassLoader.getResourceAsStream("bip39_english.txt")` resolves the file identically whether it lives in `androidMain/resources/` or `commonMain/resources/`. No code change is needed on the Android side for classpath resolution — only the file location changes.

2. **`expect`/`actual` pattern**: The project already uses `expect`/`actual` for platform-specific functionality (see `generateSecureRandomBytes` in `core/domain`). A simple `expect fun loadResourceLines(name: String): List<String>` in `commonMain` with an `actual` in `androidMain` (using `ClassLoader`) and a stub in `iosMain` follows established conventions.

3. **Why not Compose Multiplatform Resources / moko-resources**: These libraries add third-party dependency weight and build-time complexity. The project constitution (§XI.1 YAGNI, §XI.2 Simplest Solution) prohibits speculative abstractions. A single `expect`/`actual` function is the simplest sufficient solution for loading one text file.

### Alternatives Considered

| Alternative | Rejected Because |
|-------------|-----------------|
| **moko-resources** | Adds a Gradle plugin and runtime dependency for a single text file. Violates §XI.1 YAGNI. |
| **Compose Multiplatform Resources** | Requires Compose Multiplatform plugin which is not currently used in `core:security`. Overkill for a single static asset. |
| **Hardcode wordlist as a Kotlin constant** | 15 KB string constant in source code degrades readability and increases class file size. The wordlist is a canonical external artifact and should remain a resource file. |
| **Keep in androidMain, add copy to iosMain** | Violates FR-006 (no duplication) and defeats the purpose of the migration. |

---

## Research Question 2: iOS Stub Strategy

**Context**: The iOS targets (`iosArm64`, `iosSimulatorArm64`) are placeholder stubs per `NFR-ARCH-050 / T192`. We need an `actual` implementation that compiles but defers real functionality.

### Decision: Throw `NotImplementedError` in the iOS `actual`

### Rationale

The iOS targets have no functional code today (only `.gitkeep` files in `iosMain/`). Providing a `TODO("iOS resource loading not yet implemented")` is the established Kotlin pattern for deferred platform implementations. This compiles, satisfies the `expect`/`actual` contract, and fails explicitly if accidentally invoked.

### Alternatives Considered

| Alternative | Rejected Because |
|-------------|-----------------|
| **Return empty list** | Silent failure violates FR-004 (fail immediately with descriptive error). |
| **Use `NSBundle`** | Premature; no iOS functional code exists. Would require adding iOS test infrastructure. |

---

## Research Question 3: Test Refactoring Approach

**Context**: The current test (`Bip39MasterSeedGeneratorTest.kt`) is in `src/test/` and mocks `android.content.Context` and `AssetManager` to feed a synthetic wordlist. After migration, the generator should load the resource from the classpath without needing Android mocks.

### Decision: Move test to `androidHostTest/` and eliminate Context mocking

### Rationale

1. **Source set alignment**: The `test/` directory is a legacy source set. The project uses `withHostTest {}` in the build configuration, which creates the `androidHostTest` source set. This is the canonical location for JVM-hosted unit tests in a KMP-Android module.

2. **Mock elimination**: After migration, `Bip39MasterSeedGenerator` will call `loadResourceLines("bip39_english.txt")` which, on the JVM/Android host test, resolves via `ClassLoader`. The test no longer needs to mock `Context` or `AssetManager` at all.

3. **Test wordlist**: The test currently uses a synthetic sequential wordlist (`word0000`..`word2047`). After migration, the test can either:
   - **(a)** Use the real `bip39_english.txt` from the classpath (integration-style), or
   - **(b)** Keep the synthetic wordlist by placing a test resource in `androidHostTest/resources/`.

   **Decision**: Use approach **(a)** — test against the real wordlist. This validates both the loading mechanism and the correctness of the packaged resource. The synthetic wordlist is only needed for deterministic index assertions; those tests can be refactored to assert against known BIP39 test vectors instead.

### Alternatives Considered

| Alternative | Rejected Because |
|-------------|-----------------|
| **Keep test in `src/test/`** | Legacy source set; `androidHostTest` is the established location per build config. |
| **Continue mocking Context** | Defeats the purpose of the migration (SC-001: no platform-specific mocking). |
