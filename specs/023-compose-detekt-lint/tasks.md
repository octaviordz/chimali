# Tasks: Compose Detekt & Android Lint Integration

**Input**: Design documents from `specs/023-compose-detekt-lint/`
**Prerequisites**: [plan.md](./plan.md) · [spec.md](./spec.md) · [research.md](./research.md) · [data-model.md](./data-model.md)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to ([US1], [US2], [US3])
- Exact file paths are included in every description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Pin the new dependency in the version catalog — required before any build script changes.

- [ ] T001 Add `compose-rules = "0.5.7"` under `[versions]` and `detekt-compose-rules = { group = "io.nlopez.compose.rules", name = "detekt", version.ref = "compose-rules" }` under `[libraries]` in `gradle/libs.versions.toml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire the Compose rule plugin into Detekt and extend the YAML config. Both changes must be complete before any violation triage or lint work can begin.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 In `build.gradle.kts` (root), add a second `subprojects { afterEvaluate { ... } }` block that checks `plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")` and adds `dependencies { detektPlugins(libs.detekt.compose.rules) }` — targets `:app`, `:core:ui`, `:feature:authenticator`, `:feature:editor`, `:feature:fido2`, `:feature:vault` only

- [ ] T003 Append the `compose:` rule section to `config/detekt/detekt.yml` — activate all rules listed in `data-model.md` Entity 3 with `active: true` and `excludes: '**/build/**,**/generated/**'`; leave all existing sections untouched

**Checkpoint**: `./gradlew detekt --continue` runs without configuration errors; compose rules appear in output.

---

## Phase 3: User Story 1 — Compose Rule Violations Surfaced in Detekt Reports (Priority: P1) 🎯 MVP

**Goal**: All Compose rule violations in Compose-compiler modules are reported at error severity by `./gradlew detekt`.

**Independent Test**: Introduce a `@Composable fun mycomposable()` (lowercase — violates `ComposableNaming`) in `feature/vault/src/`, run `./gradlew detekt`, confirm the violation appears referencing the file and line number. Remove the violation and re-run; confirm clean output.

- [ ] T004 [US1] Run `./gradlew detekt --continue` to discover pre-existing Compose rule violations across all 6 Compose modules; record each violation (file, line, rule ID) as a checklist in a scratch note

- [ ] T005 [P] [US1] For each pre-existing Compose violation found in `app/src/`: suppress it with `@Suppress("RuleId") // TODO: resolve Compose rule violation — <description>` at the narrowest applicable declaration scope (FR-007)

- [ ] T006 [P] [US1] For each pre-existing Compose violation found in `core/ui/src/`: suppress it with `@Suppress("RuleId") // TODO: resolve Compose rule violation — <description>`

- [ ] T007 [P] [US1] For each pre-existing Compose violation found in `feature/authenticator/src/`: suppress it with `@Suppress("RuleId") // TODO: resolve Compose rule violation — <description>`

- [ ] T008 [P] [US1] For each pre-existing Compose violation found in `feature/editor/src/`: suppress it with `@Suppress("RuleId") // TODO: resolve Compose rule violation — <description>`

- [ ] T009 [P] [US1] For each pre-existing Compose violation found in `feature/fido2/src/`: suppress it with `@Suppress("RuleId") // TODO: resolve Compose rule violation — <description>`

- [ ] T010 [P] [US1] For each pre-existing Compose violation found in `feature/vault/src/`: suppress it with `@Suppress("RuleId") // TODO: resolve Compose rule violation — <description>`

- [ ] T011 [US1] Run `./gradlew detekt` (no `--continue`); confirm clean exit (exit code 0) with zero Compose rule findings; confirm non-Compose modules (`:core:common`, `:core:bluetooth`, `:core:data`, `:core:database`, `:core:domain`, `:core:security`, `:core:crdt`) produce no Compose-rule noise

**Checkpoint**: `./gradlew detekt` exits 0. Introducing a deliberate `ComposableNaming` violation causes a non-zero exit with the violation reported. Removing the violation restores clean output. SC-001 verified.

---

## Phase 4: User Story 2 — Android Lint Catches Platform-Specific Issues in CI (Priority: P2)

**Goal**: `./gradlew lintRelease` runs on all Android modules, fails on any error-level lint issue, and generates HTML + XML reports. `tools/local-ci.ps1` invokes `lintRelease` after Detekt.

**Independent Test**: Introduce `android:layout_gravity="left"` in any XML layout or a `@SuppressLint` removal on a known `RtlHardcoded` hit, run `./gradlew lintRelease`, confirm the build fails with a lint error report identifying the file and issue ID. Restore clean state and rerun — confirm pass.

- [ ] T012 [US2] In `build.gradle.kts` (root), inside the existing `subprojects { afterEvaluate { ... } }` block, add a conditional checking `plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")` that applies the `lint { ... }` DSL with all properties from `data-model.md` Entity 2 (Lint DSL Properties table)

- [ ] T013 [US2] In `tools/local-ci.ps1`, inside the `if (-not $SkipLint)` block, add `Run-Task "Lint (Release)" "$Gradle lintRelease"` immediately after the `Run-Task "Detekt"` line

- [ ] T014 [US2] Run `./gradlew lintRelease` locally; confirm clean exit (or triage any pre-existing error-level lint issues — suppress with `@SuppressLint("IssueId") // TODO: resolve Lint violation` at the narrowest declaration scope)

- [ ] T015 [US2] Verify CI script: run `tools/local-ci.ps1` (or `tools/local-ci.ps1 -SkipTests` for speed); confirm "Lint (Release)" step appears, passes on clean code, and exits non-zero when a known lint error is introduced

**Checkpoint**: `tools/local-ci.ps1` completes successfully with the Lint (Release) step present. SC-002 verified.

---

## Phase 5: User Story 3 — Developers Can Run Checks Locally and Suppress Warnings (Priority: P3)

**Goal**: `docs/quality.md` exists and accurately documents all quality tools, local commands, report locations, and suppression patterns.

**Independent Test**: Follow the documentation instructions to (a) run all checks locally using the documented command and (b) suppress one lint warning using the documented `@SuppressLint` method; confirm the suppressed warning disappears without affecting other checks.

- [ ] T016 [US3] Create `docs/quality.md` with the following sections per `research.md` R-006: **Overview** (Detekt + Compose Rules, Ktlint, Android Lint), **Running locally** (commands: `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew lintRelease`, `tools/local-ci.ps1`), **Compose rule suppressions** (`@Suppress("RuleId")` + `// TODO: resolve` pattern), **Android Lint suppressions** (`@SuppressLint` for Kotlin, `tools:ignore` for XML), **Report locations** (`build/reports/detekt/<module>.html`, `build/reports/lint/<module>.html`), **Adding new rules**

**Checkpoint**: A developer unfamiliar with the project can follow `docs/quality.md` to reproduce check results locally and apply a suppression correctly. SC-003 and SC-004 verified during PR review.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, regression check, and CI confirmation.

- [ ] T017 [P] Verify `./gradlew detekt` still produces zero findings on non-Compose modules (`:core:common` KMP module must not receive Compose rules) — confirms FR-001 module scoping is correct (SC-005)

- [ ] T018 [P] Verify `./gradlew lintRelease` HTML reports are generated at `app/build/reports/lint/app.html` and at least one feature module — confirms FR-003 report output works

- [ ] T019 Run Local CI pipeline via `tools/local-ci.ps1` — full pipeline must pass including the new Lint (Release) step; verify execution time is under 5 minutes per SC-003

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on T001 (version catalog must be updated first)
- **Phase 3 (US1)**: Depends on T002 + T003 (plugin must be wired + YAML configured)
- **Phase 4 (US2)**: Depends on Phase 2 completion; independent of Phase 3
- **Phase 5 (US3)**: Can start after Phase 2; documents what Phases 3 and 4 deliver
- **Phase 6 (Polish)**: Depends on Phases 3, 4, and 5 all complete

### User Story Dependencies

- **US1 (P1)**: T001 → T002 + T003 → T004 → T005–T010 [parallel] → T011
- **US2 (P2)**: T001 → T002 + T003 → T012 → T013 → T014 → T015
- **US3 (P3)**: T001 → T002 + T003 → T016 (can draft in parallel with US1/US2)
- **Polish**: T017 + T018 [parallel] → T019

### Parallel Opportunities

- T005, T006, T007, T008, T009, T010 — all parallel (different modules, same pattern)
- T012 and T016 — can run in parallel (different files, independent)
- T017 and T018 — parallel final checks

---

## Parallel Example: User Story 1 Violation Triage

```bash
# After T004 (violation discovery), suppress violations in all 6 modules in parallel:
Task T005: suppress in app/src/
Task T006: suppress in core/ui/src/
Task T007: suppress in feature/authenticator/src/
Task T008: suppress in feature/editor/src/
Task T009: suppress in feature/fido2/src/
Task T010: suppress in feature/vault/src/
# Then: T011 (clean run verification) — must wait for all 6 suppressions
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002, T003)
3. Complete Phase 3: US1 (T004–T011)
4. **STOP and VALIDATE**: `./gradlew detekt` exits 0; deliberate violation is caught
5. US1 is independently deliverable — Compose quality enforcement is live

### Incremental Delivery

1. Setup + Foundational (T001–T003) → Compose rules wired
2. US1 (T004–T011) → Compose violations enforced ✅
3. US2 (T012–T015) → Android Lint enforced in CI ✅
4. US3 (T016) → Developer documentation complete ✅
5. Polish (T017–T019) → Full CI validation ✅

---

## Notes

- No test tasks generated: this feature is build tooling; the spec's Independent Tests serve as acceptance criteria and are embedded in the phase checkpoints above
- T005–T010 may produce zero work if no pre-existing violations exist — that is a valid outcome
- Broad `@Suppress` (file-level, class-level, or `"all"`) is **prohibited** per FR-007; always target the narrowest declaration
- The `ForbiddenComment` Detekt rule is active and flags `TODO:` — use `// TODO:` only as the tracking comment format for violations per FR-007 (the suppression comment, not a standalone TODO block)
- Commit after each completed phase or logical group of tasks
