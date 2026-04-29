# Feature Specification: Standardize Preview Visibility

**Feature Branch**: `027-enforce-preview-public`  
**Created**: 2026-04-29  
**Status**: Completed
**Input**: User description: "Improve code quality by enforcing preview visibility standards. Remove existing rule bypasses. Non goal make logic changes. Where possible remove forbidden comment bypasses as well."

## Clarifications

### Session 2026-04-29

- Q: Preferred visibility for restructured Previews → A: `private` (local-only preference)
- Q: Scope of ForbiddenComment cleanup → A: Related only (only if guarding a resolved PreviewPublic TODO)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Clean Public API Surface (Priority: P1)

As a library consumer or developer, I want internal development tools (like UI previews) to be hidden from the public interface so that the API remains clean, focused, and free of clutter.

**Why this priority**: High. Exposing internal tooling in public APIs increases technical debt and confuses developers using the module.

**Independent Test**: Verify that no UI preview components are accessible or visible from outside their respective modules or parent components.

**Acceptance Scenarios**:

1. **Given** a UI preview that is currently marked as public, **When** the visibility is restricted and rule bypasses are removed, **Then** the component must remain functional for development but hidden from public API.
2. **Given** a component with technical debt markers (like "TODOs") hidden by rule bypasses, **When** the underlying issue is resolved and the bypass is removed, **Then** the component must pass all automated quality checks.

---

### User Story 2 - Automated Quality Enforcement (Priority: P2)

As a maintainer, I want the system to automatically prevent the introduction of public UI previews so that we maintain high standards without manual oversight.

**Why this priority**: Medium. Ensures consistency and prevents regression in code quality.

**Independent Test**: Attempt to introduce a public UI preview and verify that automated quality checks flag it as a violation.

**Acceptance Scenarios**:

1. **Given** a new UI preview component, **When** it is declared as public, **Then** the automated build system MUST report a failure.

---

### Edge Cases

- **Previews required for shared tooling**: If a preview is explicitly needed by a shared internal tool across modules, it should be marked as `internal` rather than `private`.
- **Legacy bypasses**: Some components may have bypasses for multiple rules; only those related to preview visibility and associated "TODO" comments should be removed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: All manual bypasses for "Public Preview" rules MUST be removed from the codebase.
- **FR-002**: All components currently bypassing visibility rules MUST be refactored to use restricted visibility, preferring `private` for local-only previews and `internal` only when cross-file access is required.
- **FR-003**: All "Forbidden Comment" bypasses specifically used to hide visibility-related "TODOs" MUST be removed once the visibility is corrected.
- **FR-004**: The system MUST NOT undergo any functional or behavioral changes; only visibility and quality markers should be modified.
- **FR-005**: All modified components MUST pass all automated static analysis checks after the changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Number of active bypasses for "Public Preview" rules is 0.
- **SC-002**: Number of public-facing UI preview functions in the codebase is 0.
- **SC-003**: 100% of the codebase passes the "Public Preview" check in the CI pipeline.
- **SC-004**: No functional regressions are introduced in the components being modified.

## Assumptions

- Compose Previews are not intended to be part of the public API of any module.
- Making a Preview function `private` or `internal` will not break any existing valid use cases (since Previews are for tooling).
- The `ForbiddenComment` suppression in `VaultListScreen.kt` is specifically there for the `PreviewPublic` TODO.

## Final Results & Technical Notes

### Achievement Summary
- **Visibility Enforced**: All 8 identified previews in feature:vault are now private.
- **Debt Cleared**: Removed all @Suppress("PreviewPublic"), @Suppress("ForbiddenComment"), and associated TODO markers.
- **Verification**: Verified that Detekt correctly flags public previews and that private previews pass when suppressed for UnusedPrivateMember.

### Implementation Detail: Detekt Compatibility
To satisfy the "restricted visibility" requirement while maintaining a green build, @Suppress("UnusedPrivateMember") was added to the private preview functions. This is necessary because Detekt's default configuration does not recognize IDE-only usage of private functions. This approach was chosen over internal visibility to strictly adhere to the project's "local-only" visibility preference for UI previews.
