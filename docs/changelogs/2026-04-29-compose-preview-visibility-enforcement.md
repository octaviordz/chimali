# Changelog: Jetpack Compose Preview Visibility Enforcement

**Date**: 2026-04-29  
**Status**: Completed  
**Feature Branch**: `027-enforce-preview-public`

## Problem Statement
Jetpack Compose `Preview` functions were exposed as `public` in several modules, cluttering the public API surface. Additionally, many of these previews were guarded by redundant lint suppressions (`PreviewPublic`) and "TODO" comments in code, creating technical debt and bypassing quality gates.

## Solution Details

### Visibility Refactoring
Systematically refactored 8 UI screen previews in the `feature:vault` module from `public` to `private`. This ensures that internal development tools do not leak into the module's public interface while remaining fully functional for IDE use.

Targeted files:
- `CreditCardDetailScreen.kt`
- `CreditCardEntryScreen.kt`
- `LabelManagerScreen.kt`
- `PasswordDetailScreen.kt`
- `PasswordEntryScreen.kt`
- `SecureNoteDetailScreen.kt`
- `SecureNoteEntryScreen.kt`
- `VaultListScreen.kt`

### Quality Gate Hardening
- **Detekt Configuration**: Updated `config/detekt/detekt.yml` to ignore `@Preview` annotated functions for the `UnusedPrivateMember` rule.
- **Rule Enforcement**: Removed all instances of `@Suppress("PreviewPublic")` and `@Suppress("ForbiddenComment")` related to preview visibility.
- **Cleanup**: Eliminated associated TODO markers from the codebase.

### Verification Results
- **Automated Checks**: Verified that `local-ci.ps1` passes all checks, including the `PreviewPublic` and `UnusedPrivateMember` rules.
- **Regression Test**: Manually introduced a public preview and confirmed that Detekt correctly flags it as a violation, ensuring long-term enforcement.

## Related Documentation
- [Feature Specification](file:///d:/octav/source/repos/Chimali/specs/027-enforce-preview-public/spec.md)
- [Implementation Plan](file:///d:/octav/source/repos/Chimali/specs/027-enforce-preview-public/plan.md)
- [Task List](file:///d:/octav/source/repos/Chimali/specs/027-enforce-preview-public/tasks.md)
