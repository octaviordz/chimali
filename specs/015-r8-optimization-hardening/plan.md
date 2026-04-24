# Implementation Plan: R8 Optimization & Hardening

**Branch**: `015-r8-optimization-hardening` | **Date**: 2026-04-23 | **Spec**: [spec.md](file:///D:/octav/source/repos/Chimali/specs/015-r8-optimization-hardening/spec.md)

## Summary
Implement a production-ready R8 configuration to minimize application size and harden the codebase. This involves enabling R8 Full Mode, resource shrinking, purging redundant ProGuard rules, and establishing a `shrunkDebug` build type for local optimization verification.

## Technical Context

**Language/Version**: Kotlin 2.3.20, AGP 9.2.0  
**Primary Dependencies**: KMP, kotlinx.serialization, Bouncy Castle, SQLCipher  
**Storage**: N/A (Build Infrastructure)  
**Testing**: JUnit 5, Compose UI Test, UI Automator  
**Target Platform**: Android (Min SDK 28)
**Project Type**: Mobile App (Infrastructure Hardening)  
**Performance Goals**: Reduction in APK size (>15%), maintained startup time targets (Cold < 2s).  
**Constraints**: MUST NOT break serialization or cryptographic operations at runtime.  
**Scale/Scope**: Repository-wide ProGuard optimization.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Principle III (Architecture & Quality)**: Static analysis (Ktlint/Detekt) is mandatory. **Status**: PASS (Ensured via `local-ci.ps1`).
2. **Principle IV (Performance)**: Startup time targets are critical. **Status**: PASS (R8 directly supports this goal).
3. **Principle VII (Documentation)**: IEEE 830 principles for requirements. **Status**: PASS (Specs use FR/SC identifiers).

## Project Structure

### Documentation (this feature)

```text
specs/015-r8-optimization-hardening/
├── plan.md              # This file
├── research.md          # R8 Full Mode and resource shrinking analysis
├── data-model.md        # Keep rule hierarchy and build type schema
├── quickstart.md        # How to test optimized builds locally
└── tasks.md             # Implementation tasks (Phase 2)
```

### Source Code (repository root)

```text
D:/octav/source/repos/Chimali/
├── app/
│   └── build.gradle.kts     # Main build type definitions
├── feature/fido2/
│   └── proguard-rules.pro   # Hardened keep rules
├── gradle.properties        # R8 Full Mode flags
└── AGENTS.md                # Updated agent context
```

**Structure Decision**: Standard repository layout. The primary changes are concentrated in the `app` module and `feature/fido2` ProGuard files.

## Complexity Tracking

*No violations detected.*

---

## Phase 0: Outline & Research
- [x] Research R8 Full Mode best practices for KMP.
- [x] Analyze `kotlinx.serialization` compatibility with aggressive shrinking.
- [x] Evaluate `shrunkDebug` implementation strategies.

## Phase 1: Design & Contracts
- [ ] Generate `data-model.md` for keep rule hierarchy.
- [ ] Create `quickstart.md` for testing optimized builds.
- [ ] Update `AGENTS.md` with plan reference.
- [ ] Finalize ProGuard purge list.
