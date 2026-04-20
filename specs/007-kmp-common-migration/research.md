# Research: KMP Common Module Migration

## Decision 1: Clipboard Management Abstraction
**Decision**: Use an `expect`/`actual` pattern for `ClipboardManagerService` or stick with the current interface + platform implementation pattern but move the interface to `commonMain`.
**Rationale**: The `ClipboardManagerService` interface is already platform-agnostic. By moving it to `commonMain`, feature modules can depend on the interface while the `androidMain` source set provides the implementation.
**Alternatives considered**: Using a third-party KMP clipboard library. Rejected because the current implementation is simple and custom requirements (like clearing the clipboard after 60s) are already implemented.

## Decision 2: Coroutine Dispatcher Injection
**Decision**: Move `DispatcherQualifiers` to `commonMain` and define a common `DispatchersModule` that uses `expect`/`actual` to provide the platform-appropriate dispatchers.
**Rationale**: This allows common code to inject `@Named(DispatcherQualifiers.IO)` without knowing about `Dispatchers.IO` (which is JVM-specific) or `newSingleThreadContext` (on iOS).
**Alternatives considered**: Passing dispatchers manually in constructors. Rejected because Koin is already being used for DI.

## Decision 3: Koin Module Configuration in KMP
**Decision**: Use Koin's `module` DSL in `commonMain` for shared definitions and `includes()` to pull in platform-specific modules defined in `androidMain` and `iosMain`.
**Rationale**: This aligns with Koin's recommended approach for KMP and leverages the existing `ClipboardModule` logic.
**Alternatives considered**: Using Koin Annotations for EVERYTHING. While preferred, some low-level infra like Dispatchers might be easier with DSL for now if `expect`/`actual` is involved.

## Decision 4: Dependency Management
**Decision**: Replace `libs.kotlinx.coroutines.android` with `libs.kotlinx.coroutines.core` in `commonMain`. Move `libs.androidx.core.ktx` to `androidMain`.
**Rationale**: KMP modules must only have multiplatform-compatible dependencies in `commonMain`.
