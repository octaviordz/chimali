# Quick Start: ViewModel Forwarding Cleanup

**Created**: April 27, 2026  
**Purpose**: Quick start guide for implementing ViewModel forwarding cleanup  
**Status**: Ready for Implementation

## Overview

This quick start guide provides step-by-step instructions for implementing the ViewModel forwarding cleanup to remove `@Suppress("ViewModelForwarding")` annotations and enforce proper Compose patterns.

## Prerequisites

- Access to the Chimali codebase
- Understanding of Jetpack Compose state management
- Familiarity with Detekt static analysis tools
- Local development environment set up

## Implementation Steps

### Step 1: Locate the Suppression

**File**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt`
**Line**: 200
**Current Code**:
```kotlin
// TODO: Add state hoisting in follow-up refactor
@Suppress("ViewModelForwarding")
CredentialSwipeToDismissBox(
    credential = credential,
    viewModel = viewModel,
)
```

### Step 2: Refactor Component Signature

**Update `CredentialSwipeToDismissBox` signature**:
```kotlin
@Composable
private fun CredentialSwipeToDismissBox(
    credential: PasskeyCredential,
    onPendingDelete: (PasskeyCredential) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Implementation changes in Step 3
}
```

### Step 3: Update Component Implementation

**Replace direct ViewModel access with callbacks**:
```kotlin
@Composable
private fun CredentialSwipeToDismissBox(
    credential: PasskeyCredential,
    onPendingDelete: (PasskeyCredential) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onPendingDelete(credential)  // Use callback instead of direct ViewModel access
                false
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
    )
    
    // Rest of component remains unchanged
    SwipeToDismissBox(
        state = dismissState,
        // ... existing implementation
    )
}
```

### Step 4: Update Parent Component

**Modify the LazyColumn items block in `CredentialListScreen`**:
```kotlin
items(state.credentials, key = { it.id }) { credential ->
    CredentialSwipeToDismissBox(
        credential = credential,
        onPendingDelete = { credential ->
            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))
        }
    )
}
```

### Step 5: Remove Suppression

**Delete the `@Suppress("ViewModelForwarding")` annotation** and the TODO comment.

### Step 6: Verify Changes

**Run static analysis**:
```bash
./gradlew detekt
```

**Run local CI**:
```powershell
./tools/local-ci.ps1
```

**Run tests**:
```bash
./gradlew test
```

## Validation Checklist

- [ ] `@Suppress("ViewModelForwarding")` annotation removed
- [ ] `CredentialSwipeToDismissBox` uses callback pattern
- [ ] Parent component passes callbacks instead of ViewModel
- [ ] All existing tests pass
- [ ] Detekt passes without violations
- [ ] Local CI pipeline passes
- [ ] Swipe-to-dismiss functionality works identically

## Testing

### Manual Testing
1. Open the passkey list screen
2. Swipe a credential item
3. Verify the delete confirmation dialog appears
4. Complete the deletion flow
5. Confirm behavior matches before the changes

### Automated Testing
All existing tests should pass without modification:
```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Troubleshooting

### Common Issues

**Issue**: Detekt still shows ViewModelForwarding violations
**Solution**: Ensure all ViewModel parameters are removed from child composables

**Issue**: Tests fail after refactoring
**Solution**: Verify callback functions are properly passed and invoked

**Issue**: Swipe functionality broken
**Solution**: Check that callback is passed correctly in the parent component

## Rollback Plan

If issues arise, rollback by:
1. Reverting `CredentialSwipeToDismissBox` to original signature
2. Restoring the `@Suppress("ViewModelForwarding")` annotation
3. Reverting the parent component changes

## Success Criteria

- Zero `@Suppress("ViewModelForwarding")` annotations in codebase
- All static analysis tools pass
- All tests pass with identical behavior
- No user-facing functionality changes
- Code follows Compose best practices

## Next Steps

After implementation:
1. Run full test suite
2. Perform manual testing
3. Submit for code review
4. Merge to main branch
5. Update documentation if needed
