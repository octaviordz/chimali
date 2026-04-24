# Research: KMP Debug Flag Implementation

## Decision
Use Kotlin's `expect/actual` pattern in the `:core:common` module to provide a centralized `isDebug` flag.

## Rationale
- **Platform Native**: Taps into existing platform-specific build variant indicators (`BuildConfig.DEBUG` for Android, `Platform.isDebugBinary` for Native).
- **Type Safety**: Provides a compile-time constant `Boolean` that can be optimized by R8/ProGuard.
- **Developer Experience**: No extra setup (like `BuildKonfig`) is required once the infrastructure is in place.
- **Architecture Alignment**: Centralizes build-variant logic in the most basic module (`:core:common`), making it available to all features and core components.

## Alternatives Considered

### 1. BuildKonfig Plugin
- **Pros**: Centralizes all constants (API keys, URLs).
- **Cons**: Adds a 3rd party dependency. Requires extra Gradle setup. Overkill for a single boolean flag.

### 2. Manual Property Injection
- **Pros**: No generated code.
- **Cons**: Requires manual initialization in `Application.onCreate` or equivalent. Prone to human error. Not accessible in static contexts or early initialization phases.

## Findings
- **Android**: Enabling `buildFeatures { buildConfig = true }` in a KMP module's `android` block generates a `BuildConfig` class in the module's namespace.
- **iOS**: `Platform.isDebugBinary` is a built-in property in the Kotlin/Native `kotlin.native` package, specifically designed for this purpose.
- **R8**: R8 is highly effective at constant folding for `val` properties in `object` singletons, ensuring dead code elimination for `if (isDebug)` blocks.
