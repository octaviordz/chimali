# Research: ViewModel Forwarding Cleanup

**Created**: April 27, 2026  
**Purpose**: Research findings for implementing proper ViewModel forwarding patterns  
**Status**: Complete

## Research Summary

This document provides the research foundation for removing `@Suppress("ViewModelForwarding")` annotations and implementing proper ViewModel forwarding patterns in the Chimali Android application.

## Key Findings

### 1. Current State Analysis

**Decision**: Found exactly one instance of `@Suppress("ViewModelForwarding")` in the codebase  
**Rationale**: The suppression is located in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt` at line 200, within a LazyColumn items block.  
**Alternatives considered**: Multiple suppressions vs single instance - confirmed single instance.

**Location**: 
```kotlin
// TODO: Add state hoisting in follow-up refactor
@Suppress("ViewModelForwarding")
CredentialSwipeToDismissBox(
    credential = credential,
    viewModel = viewModel,
)
```

### 2. ViewModelForwarding Rule Understanding

**Decision**: The ViewModelForwarding rule enforces that ViewModels should not be passed directly to child composables  
**Rationale**: This promotes better state management and prevents tight coupling between UI components and ViewModels. The rule is part of the Detekt Compose rules set and is actively configured in `config/detekt/detekt.yml`.  
**Alternatives considered**: Disabling the rule vs fixing the pattern - fixing the pattern aligns with clean architecture principles.

**Rule Configuration**:
```yaml
ViewModelForwarding:
  active: true
```

### 3. Proper Forwarding Patterns

**Decision**: Use state hoisting and callback-based communication instead of direct ViewModel forwarding  
**Rationale**: This follows Compose best practices for state management and maintains the unidirectional data flow pattern required by the Chimali Constitution (Principle III).  
**Alternatives considered**: Event-based patterns vs callback patterns - callbacks are simpler and sufficient for this use case.

**Recommended Pattern**:
```kotlin
@Composable
fun ParentScreen(
    viewModel: SomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    LazyColumn {
        items(state.items) { item ->
            ChildComponent(
                item = item,
                onDelete = { viewModel.onIntent(DeleteIntent(item.id)) },
                onSelect = { viewModel.onIntent(SelectIntent(item.id)) }
            )
        }
    }
}
```

### 4. Impact Analysis

**Decision**: Minimal impact expected - only one file requires modification  
**Rationale**: The suppression is localized to a single component with a clear TODO comment indicating state hoisting was already planned.  
**Alternatives considered**: Comprehensive refactoring vs targeted fix - targeted fix is sufficient given the limited scope.

**Affected Components**:
- `CredentialSwipeToDismissBox` (private composable in CredentialListScreen.kt)
- No other ViewModels or components require changes

### 5. Testing Strategy

**Decision**: Leverage existing test suite and add specific tests for the refactored component  
**Rationale**: The existing test suite provides adequate coverage. Additional tests will verify that the refactored component maintains identical behavior.  
**Alternatives considered**: Full regression testing vs targeted testing - targeted testing is sufficient given the limited scope.

**Test Requirements**:
- Verify all existing tests continue to pass
- Test swipe-to-dismiss functionality remains identical
- Verify ViewModel intent handling is preserved

### 6. Static Analysis Integration

**Decision**: Ensure Detekt passes without any suppressions after the fix  
**Rationale**: The goal is to achieve zero static analysis violations while maintaining code quality standards.  
**Alternatives considered**: Updating baseline vs fixing code - fixing code is the proper approach.

**Verification Steps**:
1. Remove @Suppress annotation
2. Implement proper forwarding pattern
3. Run Detekt to verify no violations
4. Run Local CI pipeline to ensure all checks pass

## Implementation Strategy

### Phase 1: Code Refactoring
1. Extract state management from CredentialSwipeToDismissBox
2. Move ViewModel interactions to the parent CredentialListScreen
3. Use callbacks for child component communication

### Phase 2: Testing & Validation
1. Run existing test suite
2. Verify swipe-to-dismiss functionality
3. Run static analysis tools
4. Validate Local CI pipeline

### Phase 3: Documentation
1. Update any relevant documentation
2. Remove TODO comments
3. Ensure code follows established patterns

## Risk Assessment

**Low Risk**: 
- Single file modification
- Clear TODO comment indicates planned work
- Existing test suite provides safety net

**Mitigation Strategies**:
- Maintain identical behavior through careful refactoring
- Comprehensive testing before and after changes
- Step-by-step validation process

## Edge Case Handling Strategies

### Legitimate Suppression Scenarios
**Decision**: Document criteria for when @Suppress might be acceptable  
**Rationale**: Some third-party libraries or complex scenarios may require temporary suppressions  
**Approach**: Create review process and documentation requirements for any legitimate suppressions

### Third-Party Library Integration
**Decision**: Define wrapper pattern for external libraries  
**Rationale**: Cannot modify third-party code but can control integration points  
**Approach**: Create adapter components that follow proper forwarding patterns at the boundary

### Architectural Complexity
**Decision**: Establish phased migration for complex refactoring scenarios  
**Rationale**: Some components may require gradual migration to avoid disruption  
**Approach**: Document complexity and create clear migration timeline

## Conclusion

The research confirms that this is a straightforward code quality enhancement with minimal complexity. The existing TODO comment indicates the development team was already aware of the need for state hoisting, making this a natural progression of planned work rather than a disruptive change.

The implementation will improve code quality by:
1. Eliminating static analysis suppressions
2. Promoting proper Compose state management patterns
3. Maintaining clean architecture principles
4. Supporting the Chimali Constitution's quality standards
