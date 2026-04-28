# Research: Compose MultipleEmitters Rule Enforcement

**Date**: 2026-04-27  
**Feature**: Compose MultipleEmitters Rule Enforcement

## Current State Analysis

### MultipleEmitters Rule Configuration
**Decision**: The MultipleEmitters rule is already active in the project's Detekt configuration.  
**Rationale**: Found in `config/detekt/detekt.yml` at line 845-846:
```yaml
MultipleEmitters:
  active: true
```
**Alternatives considered**: None needed - rule is already configured.

### Existing Suppressions
**Decision**: Identified 5 MultipleEmitters suppressions in `DevelopmentToolsScreen.kt`.  
**Rationale**: Found suppressions in these functions:
- `DebugMnemonicSection` (line 239)
- `RecoverSeedForm` (line 418) 
- `ManualMnemonicEntryForm` (line 592)
- `AlgorithmSelector` (line 669)
- `TestRegistrationTrigger` (line 694)

**Alternatives considered**: 
- Keep suppressions temporarily - REJECTED because goal is to remove all suppressions
- Gradual migration - ACCEPTED as implementation strategy but not as final state

### Static Analysis Tool Integration
**Decision**: Use existing Detekt configuration and local CI pipeline.  
**Rationale**: Project already has:
- Detekt with Compose rules configured
- Local CI script (`tools/local-ci.ps1`)
- Pre-commit git hooks for quality enforcement

**Alternatives considered**:
- Add new static analysis tool - REJECTED due to complexity
- Use only Ktlint - REJECTED because Ktlint doesn't have MultipleEmitters rule

### Compose Rules Source
**Decision**: Continue using `io.nlopez.compose.rules` library.  
**Rationale**: Already integrated in project and provides comprehensive Compose static analysis.

**Alternatives considered**: 
- Custom Detekt rules - REJECTED due to maintenance overhead
- Manual code review only - REJECTED as insufficient for enforcement

## Implementation Strategy

### Phase 1: Refactoring Strategy
**Decision**: Refactor suppressed functions to use single content emitter patterns.  
**Rationale**: Each suppressed function needs architectural changes to emit content through a single return path or proper composition patterns.

**Common patterns identified**:
- Conditional content rendering with multiple emit points
- Dialog/overlay content mixed with main content
- Form validation with error states

### Phase 2: CI/CD Integration
**Decision**: Enhance existing local CI to fail on MultipleEmitters violations.  
**Rationale**: Local CI already runs Detekt, just need to ensure no suppressions are allowed.

### Phase 3: Documentation and Education
**Decision**: Create developer documentation with proper Compose patterns.  
**Rationale**: Developers need clear examples of how to avoid MultipleEmitters violations.

## Edge Cases Handling

### Third-Party Dependencies
**Decision**: Document exception process for unavoidable third-party violations.  
**Rationale**: Some dependencies may have MultipleEmitters that cannot be fixed.

### Legacy Code
**Decision**: Prioritize refactoring based on usage frequency and complexity.  
**Rationale**: Not all legacy code may need immediate refactoring if low impact.

## Technical Dependencies

- **Detekt**: Already configured with Compose rules
- **Local CI**: `tools/local-ci.ps1` script exists
- **Git Hooks**: Pre-commit hooks already configured
- **Compose Rules**: `io.nlopez.compose.rules` library integrated

## Risk Assessment

### Low Risk
- Rule configuration (already active)
- CI integration (existing infrastructure)

### Medium Risk  
- Refactoring complexity (may require architectural changes)
- Developer adoption (requires education)

### High Risk
- Breaking existing functionality during refactoring
- Performance regressions from improper refactoring

## Success Metrics

- Zero MultipleEmitters suppressions in codebase
- All CI builds pass without MultipleEmitters violations
- Developer documentation completed
- Code review time reduction due to clearer standards
