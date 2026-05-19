# Tasks: KMP Resource Migration (BIP39 Wordlist)

**Input**: Design documents from `specs/044-kmp-resource-migration/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

**Tests**: Included — the project constitution mandates TDD (§Development Workflow).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- **KMP Module**: `core/security/src/`
- **Common source**: `core/security/src/commonMain/`
- **Android source**: `core/security/src/androidMain/`
- **iOS source**: `core/security/src/iosMain/`
- **Tests (host)**: `core/security/src/androidHostTest/`
- **Tests (legacy)**: `core/security/src/test/` (to be migrated)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Move the resource file and create the platform-abstraction contract

- [x] T001 Move `bip39_english.txt` from `core/security/src/androidMain/resources/bip39_english.txt` to `core/security/src/commonMain/resources/bip39_english.txt`
- [x] T002 Delete the original file at `core/security/src/androidMain/resources/bip39_english.txt` to satisfy FR-006 (no duplication)
- [x] T003 Create `expect fun loadResourceLines(name: String): List<String>` in `core/security/src/commonMain/kotlin/com/chimali/core/security/platform/ResourceLoader.kt` — must throw on missing resource (FR-004), filter blank lines, explicitly trim/normalize line endings (FR-003), use UTF-8 encoding (FR-005)

**Checkpoint**: Resource relocated; `expect` declaration compiles in `commonMain`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Provide `actual` implementations so the project compiles on all targets

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 [P] Create `actual fun loadResourceLines(name: String): List<String>` in `core/security/src/androidMain/kotlin/com/chimali/core/security/platform/ResourceLoader.android.kt` — use `Thread.currentThread().contextClassLoader?.getResourceAsStream(name)` explicitly with `Charsets.UTF_8` and `BufferedReader.readLines()`, map each line to `trim()` to normalize line endings, filter blank lines, throw `IllegalStateException` if resource is missing
- [x] T005 [P] Create `actual fun loadResourceLines(name: String): List<String>` in `core/security/src/iosMain/kotlin/com/chimali/core/security/platform/ResourceLoader.ios.kt` — stub implementation: `TODO("iOS resource loading not yet implemented")`
- [x] T006 Verify project compiles for all targets by running `.\gradlew :core:security:compileKotlinAndroid` and `.\gradlew :core:security:compileKotlinIosArm64`

**Checkpoint**: Foundation ready — all targets compile; `expect`/`actual` contract satisfied

---

## Phase 3: User Story 1 — Share Cryptographic Wordlist Across All Platforms (Priority: P1) 🎯 MVP

**Goal**: Refactor `Bip39MasterSeedGenerator` to load the wordlist via the platform-abstracted `loadResourceLines()` function instead of direct `ClassLoader` access, and validate that the resource resolves correctly from `commonMain/resources/`.

**Independent Test**: Run `.\gradlew :core:security:androidHostTest` and `.\gradlew assembleDebug` — generator loads wordlist from shared location; APK includes the resource.

### Tests for User Story 1

- [x] T007 [US1] Write test `loadResourceLines returns exactly 2048 words for bip39_english` in `core/security/src/androidHostTest/kotlin/com/chimali/core/security/platform/ResourceLoaderTest.kt` — call `loadResourceLines("bip39_english.txt")` and assert result size is 2048, no blank entries, first word is "abandon", last word is "zoo"

### Implementation for User Story 1

- [x] T008 [US1] Modify `Bip39MasterSeedGenerator` in `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGenerator.kt` — replace `javaClass.classLoader?.getResourceAsStream("bip39_english.txt")` with `loadResourceLines("bip39_english.txt")` in the `wordList` lazy initializer; remove direct stream reading, keep the `require(it.size == 2048)` validation (FR-003)
- [x] T009 [US1] Run `.\gradlew :core:security:androidHostTest` to verify `ResourceLoaderTest` passes and the generator still functions correctly
- [x] T010 [US1] Run `.\gradlew assembleDebug` to verify the APK bundles `bip39_english.txt` from the `commonMain/resources/` classpath (SC-002)

**Checkpoint**: User Story 1 complete — `Bip39MasterSeedGenerator` loads the wordlist from the shared KMP resource directory via the platform-abstracted contract

---

## Phase 4: User Story 2 — Eliminate Platform-Specific Test Dependencies (Priority: P2)

**Goal**: Migrate the existing `Bip39MasterSeedGeneratorTest` from the legacy `test/` source set to `androidHostTest/` and remove all `android.content.Context` and `AssetManager` mocking, proving that the resource is accessible via standard JVM classloader in test.

**Independent Test**: Run `.\gradlew :core:security:androidHostTest` — all seed generation tests pass without any `Context` or `AssetManager` mocking.

### Tests for User Story 2

- [x] T011 [US2] Refactor and move `Bip39MasterSeedGeneratorTest` from `core/security/src/test/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGeneratorTest.kt` to `core/security/src/androidHostTest/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGeneratorTest.kt` — remove `android.content.Context` import, remove `AssetManager` import, remove `mockk<Context>()` and `mockk<AssetManager>()` setup, instantiate `Bip39MasterSeedGenerator()` directly (no constructor args), replace synthetic wordlist with real wordlist assertions (use known BIP39 test vectors from the official Trezor/BIP39 specification for deterministic tests), update `entropyToMnemonic` tests to assert against real BIP39 English words

### Implementation for User Story 2

- [x] T012 [US2] Delete the legacy test file at `core/security/src/test/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGeneratorTest.kt`
- [x] T013 [US2] Verify the `test/` source set directory can be removed if empty, or confirm other tests remain in it; clean up empty directories under `core/security/src/test/` if no other files exist
- [x] T014 [US2] Run `.\gradlew :core:security:androidHostTest` to verify all migrated tests pass at 100% without platform-specific mocking (SC-001)

**Checkpoint**: User Story 2 complete — all seed generation tests run on standard JVM without `Context`/`AssetManager` mocking

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and documentation

- [x] T015 Run full local CI pipeline `.\tools\local-ci.ps1` to verify no regressions across the entire project (SC-004)
- [x] T016 Verify no duplicate copies of `bip39_english.txt` exist in any platform-specific resource directory (SC-005) — run `Get-ChildItem -Path "core/security/src" -Recurse -Filter "bip39_english.txt" | Select-Object FullName` and confirm only `commonMain/resources/bip39_english.txt` appears
- [x] T017 Update `CHANGELOG.md` with entry for the KMP resource migration under the current release section

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (T001–T003) — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion
- **User Story 2 (Phase 4)**: Depends on Phase 3 completion (needs the refactored generator to write mock-free tests)
- **Polish (Phase 5)**: Depends on Phases 3 and 4 being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — no dependency on US2
- **User Story 2 (P2)**: Depends on US1 completion — the test migration requires the generator to already use `loadResourceLines()` so tests can run without mocking

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD per constitution)
- Core implementation before validation runs
- Story complete before moving to next priority

### Parallel Opportunities

- T004 and T005 can run in parallel (different platform source sets)
- T001 and T003 are sequential (T003 references the moved resource conceptually)
- T007 can run in parallel with T004/T005 if the expect declaration (T003) is complete
- User Stories 1 and 2 are sequential for this feature (US2 depends on US1's refactored generator)

---

## Parallel Example: Phase 2 (Foundational)

```powershell
# Launch both actual implementations in parallel:
Task: "Create actual fun in core/security/src/androidMain/.../ResourceLoader.android.kt"
Task: "Create actual fun in core/security/src/iosMain/.../ResourceLoader.ios.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (move resource, create expect declaration)
2. Complete Phase 2: Foundational (actual implementations for Android + iOS stub)
3. Complete Phase 3: User Story 1 (refactor generator, validate)
4. **STOP and VALIDATE**: `.\gradlew :core:security:androidHostTest` + `.\gradlew assembleDebug`
5. Generator loads wordlist from shared KMP resource — MVP delivered

### Incremental Delivery

1. Setup + Foundational → Resource relocated, contract compiled ✅
2. User Story 1 → Generator refactored, wordlist loads from commonMain ✅
3. User Story 2 → Tests migrated, no more Context mocking ✅
4. Polish → Full CI validation, changelog updated ✅

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US2 depends on US1 in this feature because the test migration requires the refactored generator
- Constitution mandates TDD — test tasks are included
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
