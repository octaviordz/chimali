# Research: KMP Domain Module Configuration

## Decision 1: Target Platforms
- **Decision**: Support `androidTarget()`, `iosArm64()`, and `iosSimulatorArm64()`.
- **Rationale**: Aligns with existing KMP modules (`core:common`, `core:security`) and the project's iOS support strategy.
- **Alternatives considered**: Including `jvmTarget()` (Desktop), but current scope is mobile-focused.

## Decision 2: Dependency Injection Pattern
- **Decision**: Standardize on Koin Annotations with target-specific KSP wiring.
- **Rationale**: Complies with Constitution §III and ensures compile-time safety across all KMP platforms.
- **Alternatives considered**: Koin DSL, but it is deprecated for new development in this project.

## Decision 3: Standard Libraries
- **Decision**: Include `kotlinx-coroutines-core` and `koin-annotations` in `commonMain`.
- **Rationale**: Provides the necessary tools for async business logic and DI without introducing unneeded dependencies.
