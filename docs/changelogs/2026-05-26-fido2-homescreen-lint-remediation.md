# FIDO2 HomeScreen Refactoring & Lint Remediation (2026-05-26)

## Summary
Refactored the `Fido2HomeScreen` component to address high cyclomatic complexity, excessive method length, and Jetpack Compose best practice violations (state hoisting and effect handling).

## Changes
- **Architectural Refactoring**:
    - Extracted `Fido2EventObserver` and `BluetoothErrorDialog` into standalone private Composables to reduce the complexity of the main `Fido2HomeScreen` function.
    - Improved code maintainability by encapsulating complex FIDO2 event logic and permission dialog management.
- **Detekt & Compose Quality**:
    - **State Hoisting**: Refactored `Fido2EventObserver` to accept specific `Flow` and lambda callbacks instead of the entire `ViewModel`, resolving the "ViewModel forwarding" violation.
    - **Effect Handling**: Wrapped lambda parameters in `rememberUpdatedState` within `Fido2EventObserver` to prevent stale references in `LaunchedEffect` without triggering unnecessary restarts, resolving `LambdaParameterInRestartableEffect` violations.
    - **Complexity Reduction**: Significantly reduced cyclomatic complexity and method length, bringing the component into compliance with project thresholds.
    - **Naming Compliance**: Added `@Suppress("FunctionName")` to Composable functions where PascalCase is required by convention but flagged by standard Detekt rules.
- **Bug Fixes & Polish**:
    - **State Management**: Fixed a lint-flagged "assigned value never read" issue by transitioning from property delegates to explicit `MutableState` value access for the `showBluetoothError` state.
    - **Permission Logic**: Simplified multi-version Android Bluetooth permission checks and consolidated discoverability launching logic.
    - **Styling**: Resolved Ktlint formatting violations and "max line length" warnings in the indicator and button components.
- **Documentation**:
    - Restored and enhanced all original technical rationale comments, including critical "Fix D" documentation regarding concurrent event guarding.

## Impact
This refactor results in a more robust and testable `Fido2HomeScreen` that strictly adheres to the project's high-quality Compose and Detekt standards, while preserving the critical security and UX logic required for reliable FIDO2 transport management.
