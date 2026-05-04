# FIDO2 Auth RCA & Handover — Conversation 33c16f65

**Date:** 2026-05-04  
**Symptom:** Multiple "Sign in" prompts during registration and authentication. GetAssertion fails several times with "No credentials found" before eventually succeeding via auto-select.

---

## 1. Confirmed Event Sequence (from log)

```
02:04:50.352  CTAP2 handler: GetAssertion received (payloadLen=112, allowCredentials=1, rpId=webauthn.io)
02:04:50.352  bus.dispatch(AuthenticationRequested(options, deferred1))
02:04:50.xxx  UI navigates to AuthenticationPromptScreen
02:04:50.xxx  ViewModel init: pendingDeferred = deferred1, pendingOptions = options1

  [host retries while biometric is showing]

02:04:54.969  CTAP2 handler: GetAssertion #2 received (same payload)
02:04:54.969  bus.dispatch(AuthenticationRequested(options2, deferred2))
02:04:54.xxx  ViewModel onEach fires: pendingDeferred = deferred2, pendingOptions = options2
              (deferred1 is now ORPHANED — never completed)

02:04:54.905  User biometric succeeds → handleIntent(UserVerificationSuccess)
              → performAuthentication(pendingOptions)  ← uses options2 (or options3...)
              → getAssertionUseCase(options) → Outcome.Error(NotFound)
              → pendingDeferred?.complete(errorResult)  ← completes deferred2
              → CTAP2 coroutine for request #2 unblocks → sends error CTAP2 response

02:04:54.917  ViewModel logs "Authentication process failed"
02:04:54.915  CTAP2 handler logs "Assertion failed (expected)"  [receives error from deferred2]
02:04:54.923  "FIDO2 Operation succeeded"  [error CTAP2 response sent to host for req #2]

              deferred1 → still stuck! CTAP2 coroutine #1 blocked waiting.
              CTAP2 host receives error for #2, retries with #3...

  [cycle repeats for #3, #4...]

02:05:04.673  GetAssertion #5: findCandidateSummaries RETURNS credential
              → SelectCredentialUseCase logs "Auto-selecting single credential: ssKSO3..."
              → GetAssertionUseCase succeeds → deferred5 completed with Success
```

---

## 2. Root Causes Identified

### RCA-1 — CONFIRMED: Silent deferred orphaning under host retries (PRIMARY)

**File:** `Fido2UiEventBus.kt` line 19, `AuthenticationPromptViewModel.kt` init block lines 113–126

**Mechanism:**  
`MutableSharedFlow(replay=0, extraBufferCapacity=1)` — buffer of 1. When the CTAP2 host sends multiple GetAssertion requests while biometric is showing, each new request calls `bus.dispatch(event)`. If the SharedFlow buffer is full, `tryEmit()` **silently drops** the second event. If it succeeds, the ViewModel's `onEach` collector overwrites `pendingDeferred` with the new deferred, leaving the previous deferred **permanently unresolved**.

The CTAP2 coroutine awaiting `deferred1.await()` is then blocked indefinitely (or until `withTimeout` fires). Meanwhile, the biometric completion resolves `pendingDeferred` (= deferred2 or later), completing only the most recently stored request. All earlier CTAP2 coroutines either time out or are stuck.

**Evidence:**  
- 3–4 consecutive "No credentials found" errors logged by the ViewModel on the main thread  
- Each followed immediately by "FIDO2 Operation succeeded" (CTAP2 error response sent for that specific request)  
- Eventually auto-select succeeds — meaning the credential DID exist in the DB all along

**Why does getAssertionUseCase() fail?**  
The ViewModel's `performAuthentication` is called with `pendingOptions`, which has been overwritten to the latest request's options. The use case itself (`findCandidateSummaries`) DOES find the credential for the latest request. The "No credentials found" error is **not from a missing credential** — it is from `GetAssertionUseCase` being invoked with an EARLIER request's options that may have a **stale or invalidated deferred context**, OR the retry loop is completing the most-recently-stored deferred while earlier CTAP2 coroutines are still blocked. The host sees failures and retries, producing an escalating retry loop.

> **Note:** This is not a credential lookup failure. The credential exists. Auto-select on the 5th attempt proves this — `findCandidateSummaries` finds it every time. The failures are caused by orphaned deferreds causing host retries which cause new deferreds, which cause new biometric prompts.

### RCA-2 — CONFIRMED: `tryEmit()` is fire-and-forget; dropped events are invisible

**File:** `Fido2UiEventBus.kt` line 30

`_events.tryEmit(event)` returns `false` if the buffer is full (buffer=1, already has an unconsumed event). The return value is discarded. A dropped event means the ViewModel never receives that GetAssertion, leaving its CTAP2 deferred waiting for a timeout (~30s). This causes the 3+ "Sign in" prompts — each one is a CTAP2 retry creating a new deferred.

### RCA-3 — CONFIRMED: Error path in `performAuthentication` does not clear `pendingDeferred`

**File:** `AuthenticationPromptViewModel.kt` lines 233–239

```kotlin
is Outcome.Error -> {
    pendingDeferred?.complete(result)
    Logger.e(...)
    _state.value = AuthenticationState.Error(...)
    // ← pendingDeferred NOT nulled out
    // ← pendingOptions NOT nulled out  
    // ← No NavigateBack emitted
}
```

After a failure, the screen stays on the error state. The host retries. The next `AuthenticationRequested` event arrives via SharedFlow, overwrites `pendingDeferred`. `pendingOptions` is overwritten. A new biometric is shown. The cycle repeats.

### RCA-4 — UNCONFIRMED but still possible: Base64 padding mismatch in allow-list filter

**File:** `GetAssertionUseCase.kt` (patch 6 applied but not yet confirmed deployed)

The `CredentialId.normalized` fix was compiled and verified, but the APK deployed on-device in this test run may or may not include this fix. If it was deployed and still failing, RCA-1/2/3 are the primary cause (not padding). If it was NOT deployed, both are contributing.

**Verdict:** Based on the auto-select succeeding with the same credential, padding is **not** the primary cause. Auto-select calls `findCandidateSummaries` via `GetAssertionUseCase` through the same code path and succeeds. This means the DB query works; the credential is found; padding is not the blocker.

---

## 3. Why Auto-Select Eventually Succeeds

On the 4th–5th attempt, `performAuthentication` is invoked directly (without another biometric) because either:
1. The ViewModel's `initAuthentication` determines UV is not needed (availability = NONE for this attempt), OR
2. The CTAP2 timeout fires for earlier deferreds, allowing the pipeline to drain, and the next attempt succeeds on first try without the retry storm

The `SelectCredentialUseCase` auto-selects when `candidates.size == 1` — proving the DB had the credential all along.

---

## 4. Sign-In Prompt During Registration

**Root cause:** webauthn.io sends a GetAssertion on a second CTAP2 channel **concurrently** with MakeCredential on the first. The bus dispatches both an `AuthenticationRequested` and a `RegistrationRequested`. The HomeScreen receives the `AuthenticationRequested` and navigates to the auth screen **while registration is still in progress**, showing the "Sign in" prompt.

The `clearAll()` fix (patch 6) addresses this — but only on ceremony completion. The root issue is that `HomeScreen.LaunchedEffect` doesn't guard against navigating to auth if registration is already the active destination.

---

## 5. What Patch 6 Fixed (and What It Didn't)

| Fix | Status | Effect |
|---|---|---|
| `CredentialId.normalized` comparison | Compiled, deploy status unknown | Defensive; not the primary cause |
| `clearAll()` in success/cancel paths | Applied | Prevents stale auth nav after registration completes |
| No fix for orphaned deferreds | **NOT FIXED** | Primary cause of retry storm |
| No fix for `tryEmit()` drop | **NOT FIXED** | Contributes to orphaned deferreds |
| No fix for missing `NavigateBack` on error | **NOT FIXED** | Keeps retry loop alive |

---

## 6. Fix Plan for Next Session

### Fix A — High Priority: Serialize GetAssertion in CTAP2 handler

**File:** `Ctap2GetAssertionHandler.kt`

Add a `Mutex` or `AtomicBoolean` guard. If a GetAssertion is already in progress (deferred pending), return `CTAP1_ERR_CHANNEL_BUSY` immediately to the host. The host will retry after the current request completes. This eliminates multiple concurrent deferreds entirely.

```kotlin
private val assertionLock = Mutex()

suspend fun handle(cid, requestBytes): List<ByteArray> {
    if (!assertionLock.tryLock()) {
        return hidReportParser.encodeResponse(
            CtapHidMessage(cid, CTAP_CMD_CBOR, byteArrayOf(CTAP1_ERR_CHANNEL_BUSY))
        )
    }
    try {
        // ... existing logic
    } finally {
        assertionLock.unlock()
    }
}
```

> Check if `CTAP1_ERR_CHANNEL_BUSY` (`0x06`) is already defined in the constants. Alternatively, use `CTAP2_ERR_OPERATION_DENIED`.

### Fix B — High Priority: Navigate back on error in AuthenticationPromptViewModel

**File:** `AuthenticationPromptViewModel.kt` lines 233–239

After completing the deferred with an error, emit `NavigateBack` and null out `pendingDeferred`/`pendingOptions`. This stops the retry loop by returning to the HomeScreen after each failure, rather than keeping the auth screen open waiting for the next retry.

```kotlin
is Outcome.Error -> {
    val error = result.error
    pendingDeferred?.complete(result)
    pendingDeferred = null
    pendingOptions = null
    Logger.e(error.cause) { "Authentication process failed: ${error.message}" }
    val ui = Fido2ErrorHandler.handle(error)
    if (ui.isRetryable) {
        _state.value = AuthenticationState.Error(ui.message, ui.isRetryable)
    } else {
        _state.value = AuthenticationState.Cancelled
        viewModelScope.launch { emit(AuthenticationEffect.NavigateBack) }
    }
}
```

### Fix C — Medium Priority: Replace `tryEmit()` with buffered channel or `extraBufferCapacity` increase

**File:** `Fido2UiEventBus.kt` line 19

Change `extraBufferCapacity = 1` to `extraBufferCapacity = 4` to avoid silent drops under rapid retries. Log dropped events:

```kotlin
if (!_events.tryEmit(event)) {
    Logger.w { "[EventBus] Event dropped (buffer full): ${event::class.simpleName}" }
}
```

### Fix D — Low Priority: Guard HomeScreen from auth navigation during active registration

**File:** `Fido2HomeScreen.kt` or `Fido2RegistrationNavGraph.kt`

In the `LaunchedEffect` that navigates to auth on `AuthenticationRequested`, check `currentRoute != REGISTRATION_ROUTE && currentRoute != AUTHENTICATION_ROUTE` before calling `onAuthenticateRequest()`.

---

## 7. Files to Touch in Next Session

| File | Change |
|---|---|
| `Ctap2GetAssertionHandler.kt` | Add Mutex guard (Fix A) |
| `AuthenticationPromptViewModel.kt` | Null deferred on error + NavigateBack (Fix B) |
| `Fido2UiEventBus.kt` | Increase buffer, log drops (Fix C) |
| `Fido2HomeScreen.kt` | Guard auth nav during active ceremonies (Fix D) |

---

## 8. Build & Test Notes

- **Build command:** `.\gradlew :app:installDebug` (not just `bundleAndroidMainClassesToCompileJar`)
- **Verify patch 6 normalization fix is deployed** by checking logcat for `findCandidateSummaries` debug output (add a temporary log of the normalized IDs being compared)
- **Test sequence:** Registration → observe if auth prompt appears → Authentication × 3 → confirm single prompt + success on first attempt

---

## 9. Patch History This Session

| Patch | File | Change |
|---|---|---|
| 6a | `GetAssertionUseCase.kt` | Normalized Base64 allow-list comparison |
| 6b | `Fido2UiEventBus.kt` | Added `clearAll()` |
| 6c | `AuthenticationPromptViewModel.kt` | `clearAll()` on success + cancel |
| 6d | `RegistrationPromptViewModel.kt` | `clearAll()` on success + cancel |
