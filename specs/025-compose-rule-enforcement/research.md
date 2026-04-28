# Research: Compose Rule Enforcement

**Feature**: Compose Rule Enforcement  
**Date**: 2026-04-27  
**Purpose**: Resolve technical unknowns for implementation planning

## Research Findings

### Kotlin Version Requirements

**Decision**: Kotlin 1.9.20+  
**Rationale**: 
- Current Android projects typically use Kotlin 1.9.20 or later for Compose compatibility
- This version includes improved Compose compiler support and better type inference
- Aligns with modern Android development practices

**Alternatives considered**:
- Kotlin 1.8.x: Older, may have compatibility issues with latest Compose
- Kotlin 2.0.x: Too new, may introduce breaking changes

### Jetpack Compose Version

**Decision**: Compose BOM 2024.02.00+  
**Rationale**:
- Provides stable versions of all Compose libraries
- Includes latest fixes for parameter ordering and restartable effect linting
- Compatible with Kotlin 1.9.20+

**Alternatives considered**:
- Older Compose versions: May lack proper lint rule support
- Snapshot versions: Too unstable for production codebase

### Detekt Configuration

**Decision**: Detekt 1.23.0+ with Compose rules enabled  
**Rationale**:
- Latest stable version with comprehensive Compose rule set
- Includes `LambdaParameterInRestartableEffect` and `ComposableParamOrder` rules
- Better integration with Android build systems

**Alternatives considered**:
- Built-in Android lint: Less comprehensive for Compose-specific rules
- Older Detekt versions: May lack proper Compose rule support

### Testing Framework Versions

**Decision**: JUnit 5.10.0+, Compose UI Testing 1.5.0+  
**Rationale**:
- JUnit 5 provides modern testing features and better parameterized tests
- Compose UI Testing 1.5.0+ includes improved testing for Compose components
- Compatible with current Android testing infrastructure

**Alternatives considered**:
- JUnit 4: Legacy, lacks modern features
- Espresso: Not ideal for pure Compose testing

### Code Analysis Strategy

**Decision**: Multi-phase approach using automated tools + manual review  
**Rationale**:
- Automated tools can identify most suppressions efficiently
- Manual review needed for complex cases and edge conditions
- Ensures thorough coverage while maintaining code quality

**Implementation approach**:
1. Use grep/rg to find all suppressions
2. Run Detekt to identify specific rule violations
3. Systematically address each suppression
4. Manual review of complex cases
5. Verification through build and test execution

### Binary Compatibility Considerations

**Decision**: Careful parameter reordering with compatibility checks  
**Rationale**:
- Parameter reordering can break binary compatibility for public APIs
- Need to distinguish between internal and public Composable functions
- Use @Deprecated with migration path for public functions when necessary

**Mitigation strategy**:
- Identify public vs internal Composable functions
- For public functions, use overloads or deprecation cycles
- Internal functions can be reordered directly

## Resolved Technical Context

Based on research findings, the updated technical context:

**Language/Version**: Kotlin 1.9.20+  
**Primary Dependencies**: Jetpack Compose BOM 2024.02.00+, Detekt 1.23.0+  
**Storage**: N/A (code cleanup feature)  
**Testing**: JUnit 5.10.0+, Compose UI Testing 1.5.0+  
**Target Platform**: Android (Minimum SDK 28)  
**Project Type**: mobile-app  
**Performance Goals**: No performance degradation during cleanup process  
**Constraints**: Must maintain binary compatibility, no logic changes allowed  
**Scale/Scope**: Entire codebase across all modules

## Implementation Strategy

### Phase 1: Discovery
- Scan codebase for all `@Suppress` annotations
- Identify specific instances of target suppressions
- Catalog by module and complexity

### Phase 2: LambdaParameterInRestartableEffect Removal
- Address restartable effects with proper parameter naming
- Fix underlying issues revealed by suppression removal
- Ensure proper memory management in effects

### Phase 3: ComposableParamOrder Removal
- Reorder parameters following Compose conventions (content, modifier, others)
- Handle binary compatibility for public functions
- Verify all functions compile without warnings

### Phase 4: Validation
- Run full Detekt analysis
- Execute build and test suite
- Manual review of complex cases
- Documentation of any remaining suppressions with justification

## Risk Mitigation

### Binary Compatibility Risks
- Strategy: Careful analysis of public API surface
- Mitigation: Use overloads and deprecation where needed

### Compilation Failures
- Strategy: Incremental changes with frequent builds
- Mitigation: Address issues immediately as they arise

### Performance Impact
- Strategy: Benchmark before and after changes
- Mitigation: Revert any changes that affect performance

### Functionality Regression
- Strategy: Comprehensive testing after each change
- Mitigation: Maintain test coverage and manual verification
