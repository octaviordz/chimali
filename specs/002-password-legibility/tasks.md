# Tasks: Password Legibility and Confusion Prevention

**Input**: Design documents from `/specs/002-password-legibility/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- [ ] T001 [P] Configure Atkinson Hyperlegible as a primary font in `core:ui` module
- [ ] T002 [P] Configure JetBrains Mono as a monospaced font option in `core:ui` module
- [ ] T003 [P] Define `LegibilitySettings` data model in `feature:vault/src/main/java/com/chimali/feature/vault/ui/model/LegibilitySettings.kt`
- [ ] T004 Define `LegibilityColors` with orange semantic tokens in `feature:vault/src/main/java/com/chimali/feature/vault/ui/theme/LegibilityColors.kt`

## Phase 3: User Story 1 - Distinguishing characters (Priority: P1) 🎯 MVP

**Goal**: Ensure ambiguous characters like 'O/0' and 'I/l/1' are unmistakable.

**Independent Test**: Display "Il1O0" and verify each character is visually distinct using the legibility font.

- [ ] T005 [P] [US1] Implement `AtkinsonFontFamily` in `core:ui` common assets
- [ ] T006 [P] [US1] Implement `LegibleCredentialText` Composable foundation in `feature:vault/src/main/java/com/chimali/feature/vault/ui/components/LegibleCredentialText.kt`
- [ ] T007 [US1] Update `CredentialDetailScreen.kt` to replace standard Text with `LegibleCredentialText` (depends on T006)

---

## Phase 4: User Story 2 - Character Type Identification (Priority: P2)

**Goal**: Highlight digits (numbers) in orange within the secret text.

**Independent Test**: Input "Pass123" and verify "123" is rendered in orange.

- [ ] T008 [P] [US2] Implement regex-based `AnnotatedString` builder for digit highlighting in `feature:vault/src/main/java/com/chimali/feature/vault/ui/components/LegibleCredentialText.kt`
- [ ] T009 [US2] Integrate Orange semantic color for digits in `LegibleCredentialText` (depends on T004, T008)

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T010 [P] Verify WCAG 2.1 AA contrast for the orange digit highlighting (>= 4.5:1)
- [ ] T011 [P] Verify Screen Reader (TalkBack) reads the secrets correctly regardless of styling
- [ ] T012 Update project documentation (README.md) with accessibility guidelines

## Dependencies & Execution Order

1. **Foundational (T001-T004)**: MUST complete first.
2. **User Story 1 (T005-T007)**: P1 priority, delivers the most critical legibility value.
3. **User Story 2 (T008-T009)**: P2 priority, enhances secondary visual parsing.
4. **Polish (T010-T012)**: Final validation and docs.
