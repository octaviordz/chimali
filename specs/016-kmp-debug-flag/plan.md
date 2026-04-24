# Implementation Plan - KMP Debug Flag

Centralize build variant detection (Debug vs. Release) in the `:core:common` module using Kotlin Multiplatform `expect/actual` patterns. This replaces hardcoded `if (true)` guards and platform-specific `BuildConfig.DEBUG` references with a unified, compile-time safe `isDebug` flag.

## User Review Required

> [!IMPORTANT]
> This change introduces a new dependency on `BuildConfig` generation within the `:core:common` module. While standard, it adds a small amount of generated code to the core library.

## Proposed Changes

### Core Infrastructure (`:core:common`)

We will add a platform-agnostic `isDebug` flag to `core:common`.

#### [MODIFY] [build.gradle.kts](file:///D:/octav/source/repos/Chimali/core/common/build.gradle.kts)
Enable `buildConfig` generation for the Android target.

#### [NEW] [BuildVariant.kt](file:///D:/octav/source/repos/Chimali/core/common/src/commonMain/kotlin/com/chimali/core/common/BuildVariant.kt)
Define the `expect` property.

#### [NEW] [BuildVariant.kt](file:///D:/octav/source/repos/Chimali/core/common/src/androidMain/kotlin/com/chimali/core/common/BuildVariant.kt)
Implement the `actual` property for Android using `BuildConfig.DEBUG`.

#### [NEW] [BuildVariant.kt](file:///D:/octav/source/repos/Chimali/core/common/src/iosMain/kotlin/com/chimali/core/common/BuildVariant.kt)
Implement the `actual` property for iOS using `Platform.isDebugBinary`.

---

### Feature Adoption (`:feature:fido2`)

Update the FIDO2 module to use the new centralized flag.

#### [MODIFY] [DevelopmentToolsScreen.kt](file:///D:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt)
Replace `if (true)` with `if (com.chimali.core.common.isDebug)`.

#### [MODIFY] [Fido2Initializer.kt](file:///D:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/Fido2Initializer.kt)
Optional: Refactor to use the centralized flag instead of a passed-in parameter (improves API consistency).

---

## Constitution Check

| Principle | Adherence | Rationale |
| :--- | :--- | :--- |
| **I. Security First** | ✅ | Removes dangerous hardcoded `if (true)` guards that could leak debug tools to production. |
| **III. Architecture** | ✅ | Follows KMP best practices (`expect/actual`) and centralizes cross-cutting concerns. |
| **VII. Documentation**| ✅ | Follows IEEE 830 traceability via the feature spec. |
| **VIII. Local CI/CD** | ✅ | Verified via unit tests in `commonTest` and `androidHostTest`. |

## Verification Plan

### Automated Tests
- **Unit Test (`core:common`)**: Create a test in `commonTest` that accesses `isDebug`.
- **Unit Test (`androidHostTest`)**: Verify that `isDebug` matches `BuildConfig.DEBUG` on the JVM.
- **R8 Analysis**: Run `shrunkDebug` build and verify that the `DevelopmentToolsScreen` debug section is removed from the DEX (manual verification via Layout Inspector or APK Analyzer).

### Manual Verification
1. Run the app in **Debug** mode: Verify "Test & Debug Utilities" are visible.
2. Run the app in **Release** mode: Verify "Test & Debug Utilities" are hidden.
