# Root Cause Analysis: NFR-PERF-030 Over Budget (GetAssertion)

## Problem Description
During a FIDO2 authentication ceremony (`GetAssertion`), the system incorrectly reports a performance budget violation for the `[NFR-PERF-030]` requirement. The logcat output shows:

```text
[NFR-PERF-030] ❌ OVER BUDGET | GetAssertion = 3574ms (Total: 3574ms, User: 0ms)
```

The budget allows for a maximum of 200ms pure system processing time. The logs indicate that the total time was 3574ms, and the accumulated user interaction time was 0ms.

## Analysis

### 1. Profiler Mechanism
The measurement is recorded using `LatencyProfiler.kt`. 
- `LatencyProfiler.start("GetAssertion")` and `LatencyProfiler.end("GetAssertion")` surround the operation.
- The profiler provides `LatencyProfiler.startUserInteraction(id)` and `LatencyProfiler.endUserInteraction(id)` to pause the timer and subtract user-facing UI delays (e.g., biometric prompts or credential selection).

### 2. Transport Layer Measurement
In `BluetoothHidTransportImpl.kt`, when an incoming `CMD_GET_ASSERTION` (0x02) command is identified, `LatencyProfiler.start("GetAssertion")` is invoked. It then delegates the handling of the CBOR payload to `Ctap2GetAssertionHandler.handle()` and stops the profiler via `LatencyProfiler.end("GetAssertion")` once the response is ready to be sent.

### 3. Missing Interaction Tracking
Inside `Ctap2GetAssertionHandler.kt`, if the assertion requires user interaction (i.e. `shouldGoHeadless()` returns false), it dispatches an event to the UI using `uiEventBus` and suspends using `deferred.await()`:

```kotlin
val deferred = kotlinx.coroutines.CompletableDeferred<Outcome<AssertionObject, DomainError>>()
uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(options, deferred))

try {
    kotlinx.coroutines.withTimeout(options.getSafeTimeout()) {
        deferred.await()
    }
} catch (_: kotlinx.coroutines.TimeoutCancellationException) {
...
```

**Root Cause:** The `Ctap2GetAssertionHandler.kt` fails to call `LatencyProfiler.startUserInteraction("GetAssertion")` and `LatencyProfiler.endUserInteraction("GetAssertion")` around the `deferred.await()` suspension. 

By contrast, `Ctap2MakeCredentialHandler.kt` correctly implements this pattern for registration:

```kotlin
// From Ctap2MakeCredentialHandler.kt
LatencyProfiler.startUserInteraction("MakeCredential")
val makeCredentialResult =
    try {
        kotlinx.coroutines.withTimeout(makeCredentialOptions.getSafeTimeout()) {
            deferred.await()
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) { ... }
LatencyProfiler.endUserInteraction("MakeCredential")
```

Because these `UserInteraction` calls are missing in `Ctap2GetAssertionHandler.kt`, the `LatencyProfiler` interprets the entire user interaction duration (e.g. 3.5 seconds) as raw system processing time (`User: 0ms`). This triggers a false-positive violation of the `< 200ms` `NFR-PERF-030` budget.

## Proposed Remediation (Do Not Implement - Code Changes Deferred)

Modify `Ctap2GetAssertionHandler.kt` to record user interaction time around the UI deferral:

```kotlin
// In Ctap2GetAssertionHandler.kt
val deferred = kotlinx.coroutines.CompletableDeferred<Outcome<AssertionObject, DomainError>>()
uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(options, deferred))

LatencyProfiler.startUserInteraction("GetAssertion")
try {
    kotlinx.coroutines.withTimeout(options.getSafeTimeout()) {
        deferred.await()
    }
} catch (_: kotlinx.coroutines.TimeoutCancellationException) {
    ...
} finally {
    LatencyProfiler.endUserInteraction("GetAssertion")
}
```

Wrapping the `deferred.await()` with `startUserInteraction` and `endUserInteraction` inside a try/finally block will ensure that the UI time is correctly subtracted from the `Total` elapsed time, yielding an accurate metric for pure system latency and resolving the false-positive log.
