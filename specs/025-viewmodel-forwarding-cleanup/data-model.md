# Data Model: ViewModel Forwarding Cleanup

**Created**: April 27, 2026  
**Purpose**: Data structures and entities affected by ViewModel forwarding cleanup  
**Status**: Complete

## Overview

This feature is a code quality enhancement that does not introduce new data structures or modify existing ones. The cleanup focuses on refactoring Compose component patterns while maintaining all existing data flows and entity relationships.

## Affected Entities

### 1. Compose Component Signatures

**Before (Current)**:
```kotlin
@Composable
private fun CredentialSwipeToDismissBox(
    credential: PasskeyCredential,
    viewModel: CredentialManagementViewModel,
) {
    // Component implementation with direct ViewModel access
}
```

**After (Target)**:
```kotlin
@Composable
private fun CredentialSwipeToDismissBox(
    credential: PasskeyCredential,
    onPendingDelete: (PasskeyCredential) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Component implementation with callback-based communication
}
```

### 2. State Flow Patterns

**Existing State Flow** (unchanged):
```kotlin
// In CredentialListScreen
val state by viewModel.state.collectAsState()
val removalEvents by viewModel.removalEvents.collectAsState()
```

**New Callback Pattern**:
```kotlin
// In CredentialListScreen items block
items(state.credentials, key = { it.id }) { credential ->
    CredentialSwipeToDismissBox(
        credential = credential,
        onPendingDelete = { credential ->
            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))
        }
    )
}
```

## Unchanged Entities

### Core Data Models
- `PasskeyCredential` - No changes required
- `CredentialManagementViewModel` - No changes required  
- `CredentialManagementState` - No changes required
- `CredentialManagementIntent` - No changes required
- `CredentialManagementEffect` - No changes required

### UI State Models
- All existing UI state models remain unchanged
- No new state containers introduced
- No data transformation required

## Validation Rules

### Static Analysis Rules
- **ViewModelForwarding**: Must pass without suppressions
- **FunctionNaming**: Existing suppressions for preview functions remain
- **ForbiddenComment**: Existing suppressions remain where appropriate

### Behavioral Constraints
- All swipe-to-dismiss functionality must be preserved
- Visual feedback and animations must remain identical
- User interaction patterns must not change
- Performance characteristics must be maintained

## State Transitions

### User Interaction Flow (Unchanged)
1. User swipes credential item → Swipe gesture detected
2. Pending delete state → ViewModel receives intent
3. Biometric prompt → Authentication flow
4. Delete confirmation → ViewModel processes deletion
5. UI updates → State reflects changes

### Component Communication Flow (Refactored)
**Before**: Child component → Direct ViewModel access  
**After**: Child component → Callback → Parent component → ViewModel access

## Data Integrity

### No Data Modifications
- No database schema changes
- No API contract changes  
- No data model migrations
- No serialization format changes

### Test Data Requirements
- Existing test data remains valid
- No new test fixtures required
- Mock objects remain unchanged

## Conclusion

The ViewModel forwarding cleanup is a pure architectural refactoring that maintains complete data compatibility while improving code structure. All existing entities, relationships, and data flows remain unchanged, ensuring zero impact on application functionality and data integrity.
