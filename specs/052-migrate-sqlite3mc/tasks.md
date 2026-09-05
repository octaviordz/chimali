# Tasks: Migrate Encrypted Database Layer from SQLCipher to SQLite3MultipleCiphers

**Branch**: `052-migrate-sqlite3mc` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Update the version catalog and build files — the prerequisite for every subsequent change.

- [X] T001 Add `sqlite-mc = "2.1.0-2.2.3-0"` version entry to `gradle/libs.versions.toml`, replacing the `sqlcipher = "4.13.0"` entry
- [X] T002 Add `sqlite-mc-driver` and `sqlite-mc-android-unit-test` library entries to `gradle/libs.versions.toml`, replacing the `sqlcipher` library entry
- [X] T003 Replace `implementation(libs.sqlcipher)` with `implementation(libs.sqliteMcDriver)` in `core/database/build.gradle.kts`
- [X] T004 Add `testImplementation(libs.sqliteMcAndroidUnitTest)` to the test dependencies block in `core/database/build.gradle.kts`
- [X] T005 [P] Remove `implementation(libs.sqlcipher)` from `androidMain.dependencies` in `feature/fido2/build.gradle.kts`
- [X] T006 [P] Add `testImplementation(libs.sqliteMcAndroidUnitTest)` to the `androidHostTest` dependencies block in `feature/fido2/build.gradle.kts`

**Checkpoint**: Run `./gradlew :core:database:dependencies :feature:fido2:dependencies` — verify `net.zetetic` is gone and `io.toxicity.sqlite-mc` is present.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The `EncryptedDriverFactory` rewrite is the single blocking dependency — all other story tasks depend on it being complete and compiling.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T007 Remove the `import net.zetetic.database.sqlcipher.SupportOpenHelperFactory` import from `core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt`
- [X] T008 Remove the `System.loadLibrary("sqlcipher")` call and its surrounding `try/catch (UnsatisfiedLinkError)` block from `EncryptedDriverFactory.kt`
- [X] T009 Add imports for `io.toxicity.sqlite.mc.driver.SQLiteMCDriver`, `io.toxicity.sqlite.mc.driver.config.key.Key`, and `io.toxicity.sqlite.mc.driver.config.databasesDir` to `EncryptedDriverFactory.kt`
- [X] T010 Add the `DB_SALT_BYTES` constant (`"chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)`) alongside the existing `DB_KEY_LENGTH_BITS` constant in `EncryptedDriverFactory.kt`, and rename `SQLCIPHER_KEY_LENGTH_BITS` to `DB_KEY_LENGTH_BITS` throughout the file
- [X] T011 Replace the `createDriver()` method body in `EncryptedDriverFactory.kt`: swap `SupportOpenHelperFactory(derivedKey)` + `AndroidSqliteDriver(...)` with a `SQLiteMCDriver.Factory(dbName = name, schema = schema) { filesystem(context.databasesDir()) {} }.createBlocking(key = Key.raw(key = derivedKey, salt = DB_SALT_BYTES, fillKey = true))` call, preserving the existing `try/finally` key-zeroing block
- [X] T012 Update all KDoc and inline comments in `EncryptedDriverFactory.kt` that reference "SQLCipher" to reference "SQLite3MultipleCiphers"
- [X] T013 Verify `core/database` compiles clean: `./gradlew :core:database:compileDebugKotlin`

**Checkpoint**: `./gradlew :core:database:compileDebugKotlin` passes with zero errors.

---

## Phase 3: User Story 1 — New Encrypted Databases Created with ChaCha20-Poly1305 (Priority: P1) 🎯 MVP

**Goal**: Fresh `vault.db` and `fido2.db` databases are created and reopened successfully using SQLite3MultipleCiphers with ChaCha20-Poly1305.

**Independent Test**: `./gradlew :core:database:test` passes all `EncryptedDriverFactoryTest` assertions with no `UnsatisfiedLinkError`.

### Implementation for User Story 1

- [X] T014a [US1] Add `implementation(project(":core:database"))` to `androidMain.dependencies` in `feature/fido2/build.gradle.kts` so that `EncryptedDriverFactory` is resolvable from the fido2 module *(required: :core:database is not currently a declared dependency of :feature:fido2)*
- [X] T014 [US1] Update `fido2Database()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`: replace the plain `AndroidSqliteDriver(Fido2Database.Schema, context, "fido2.db")` call with `encryptedDriverFactory.createDriver(Fido2Database.Schema, "fido2.db")`, add `EncryptedDriverFactory` as a constructor/Koin parameter, and remove the `import app.cash.sqldelight.driver.android.AndroidSqliteDriver` import
- [X] T015 [US1] Update KDoc on `fido2Database()` in `Fido2Module.kt` to correctly describe SQLite3MultipleCiphers encryption (removing the misleading "backed by SQLCipher" text)
- [X] T016 [P] [US1] Compile-check `feature/fido2`: `./gradlew :feature:fido2:compileDebugKotlin`
- [X] T017a [US1] Run all `core:database` JVM host tests: `./gradlew :core:database:test` — verify all `EncryptedDriverFactoryTest` tests pass (key derivation, null seed error, integrity check)
- [X] T017b [US1] Add a JVM host test in `core/database/src/test/java/com/chimali/core/database/EncryptedDriverFactoryTest.kt` (or a new `SQLiteMCDriverIntegrationTest.kt`) that creates an in-memory `SQLiteMCDriver` with ChaCha20-Poly1305, executes `PRAGMA integrity_check`, and asserts the result is `"ok"` — satisfies US2 AS3

**Checkpoint**: `./gradlew :core:database:test` passes. US1 is independently verified: encrypted driver creates and reopens databases correctly.

---

## Phase 4: User Story 2 — JVM Host Tests Pass Without Device (Priority: P2)

**Goal**: `./gradlew :core:database:test` and `./gradlew :feature:fido2:testDebugUnitTest` both complete on a development workstation with no `UnsatisfiedLinkError` and no `System.loadLibrary` workaround.

**Independent Test**: Both Gradle test tasks complete green on a Windows workstation without an Android device.

### Implementation for User Story 2

- [X] T018 [US2] Run `./gradlew :feature:fido2:testDebugUnitTest` and confirm all tests in `SecurityStorageIntegrityTest` pass (T148d-1, T148d-2, T148d-3) — this validates the `android-unit-test` artifact provides the needed native binaries
- [X] T019 [US2] Confirm no `System.loadLibrary` or `UnsatisfiedLinkError` appears anywhere in the test output (grep output for both strings)

**Checkpoint**: Both test tasks pass green end-to-end on the local workstation. JVM host test support is confirmed without any native library workaround.

---

## Phase 5: User Story 3 — Encryption Integrity Contract Maintained (Priority: P3)

**Goal**: The `SecurityStorageIntegrityTest.T148d` contract is updated for SQLite3MultipleCiphers and continues to assert that encrypted database files do not expose a plain SQLite header.

**Independent Test**: `./gradlew :feature:fido2:testDebugUnitTest` passes with all T148d assertions intact, and test names/KDoc no longer reference SQLCipher.

### Implementation for User Story 3

- [X] T020 [US3] In `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/security/SecurityStorageIntegrityTest.kt`: rename test function `` `T148d production SQLCipher database must NOT have plain SQLite magic header (self-documenting contract)` `` to `` `T148d production SQLite3MultipleCiphers database must NOT have plain SQLite magic header (self-documenting contract)` ``
- [X] T021 [US3] Update the KDoc block on `SecurityStorageIntegrityTest` class: replace "SQLCipher" with "SQLite3MultipleCiphers" in the class-level KDoc, including the description of T148d-2
- [X] T022 [US3] Update the assertion message string in T148d-2 from `"Contract: SQLCipher-encrypted DB header must differ from plain SQLite magic"` to `"Contract: SQLite3MultipleCiphers-encrypted DB header must differ from plain SQLite magic"`
- [X] T023 [US3] Run `./gradlew :feature:fido2:testDebugUnitTest` and confirm all 3 T148d tests pass with updated names

**Checkpoint**: All 3 T148d tests pass. Encryption contract is maintained and correctly attributed to SQLite3MultipleCiphers.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, static analysis, and final CI validation.

- [X] T024 Remove the stale Detekt baseline entry `<ID>UnusedParameter:SqlCipherWrapper.kt$SqlCipherWrapper$password: String</ID>` from `feature/fido2/detekt-baseline.xml`
- [X] T025 [P] Update the comment in `feature/fido2/proguard-rules.pro` line 30 from "Bouncy Castle and SQLCipher rules are now handled by library consumer rules" to "Bouncy Castle and SQLite3MultipleCiphers rules are now handled by library consumer rules"
- [X] T026 [P] Run static analysis: `./gradlew :core:database:detekt :feature:fido2:detekt` — confirm zero new violations
- [X] T027 [P] Run format checks: `./gradlew :core:database:ktlintCheck :feature:fido2:ktlintCheck` — confirm zero violations
- [X] T028 Verify SQLCipher is fully absent from the dependency graph: `./gradlew :core:database:dependencies :feature:fido2:dependencies` — output must contain no `net.zetetic` entries
- [X] T029 Run the full local CI pipeline: `tools/local-ci.ps1` — confirm green end-to-end

**Checkpoint**: All checks pass. SQLCipher is fully replaced. `local-ci.ps1` is green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T001–T002 must complete before T003–T006 (catalog entries needed for build file references).
- **Foundational (Phase 2)**: Depends on T001–T004 (catalog + core build file). **BLOCKS** all user story phases.
- **US1 (Phase 3)**: Depends on Foundational (T007–T013). T014a must precede T014 (module dep before class reference). T014–T015 can start once T011 compiles. T017a/T017b run after T016.
- **US2 (Phase 4)**: Depends on Phase 1 + Foundational + T014 (fido2 module must use encrypted driver). T018–T019 are validation-only.
- **US3 (Phase 5)**: Depends on Phase 1 only (test file changes are independent of driver implementation). Can run in parallel with Phase 3 after T001–T006.
- **Polish (Phase 6)**: Depends on all story phases being complete.

### Parallel Opportunities

- T005 and T006 (`feature/fido2` build file) can run in parallel with T003–T004 (`core:database` build file) after T001–T002.
- T012 (KDoc updates in `EncryptedDriverFactory`) must run after T011 (logic changes); it is executed sequentially within Phase 2.
- T020–T022 (US3 test file updates) can be worked in parallel with T014–T016 (US1 Fido2Module update) — different files.
- T024, T025, T026, T027 in Phase 6 are all independent and can run in parallel.

---

## Parallel Example: Foundational Phase

```text
# These can proceed in sequence within Phase 2 (same file — serial):
T007 → T008 → T009 → T010 → T011 → T012 → T013

# While T005/T006 (feature/fido2 build file) can be done in parallel with T003/T004:
[T003, T004] ∥ [T005, T006]
```

## Parallel Example: After Foundational Complete

```text
# US1 and US3 can proceed concurrently once Phase 2 is done:
[T014a → T014 → T015 → T016 → T017a → T017b]   # US1: fido2 dep + encryption fix + core tests
[T020 → T021 → T022 → T023]                      # US3: integrity test rename (file-independent)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T006)
2. Complete Phase 2: Foundational — `EncryptedDriverFactory` rewrite (T007–T013)
3. Complete Phase 3: US1 — fido2.db encryption fix + core tests (T014a, T014–T017b)
4. **STOP and VALIDATE**: `./gradlew :core:database:test` passes ✅
5. SQLCipher is replaced and the core encryption path works.

### Full Delivery (All Stories)

1. MVP (above) → then continue:
2. Phase 4: US2 — JVM host test validation (T018–T019)
3. Phase 5: US3 — integrity test update (T020–T023)
4. Phase 6: Polish (T024–T029) → `local-ci.ps1` green ✅

---

## Notes

- `[P]` tasks = can run in parallel (different files, no shared state dependencies)
- `[USx]` label maps each task to its user story for traceability
- The entire migration is in existing files — no new source files created (T017b may create `SQLiteMCDriverIntegrationTest.kt` if preferred over extending the existing test file)
- Commit after each phase checkpoint to enable bisect if issues arise
- If `Key.raw()` API signature differs at implementation time, fall back to `Key.passphrase("x'<hex>'")`  — see research.md Decision 4

