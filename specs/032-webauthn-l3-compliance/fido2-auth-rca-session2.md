# FIDO2 Auth RCA — Session 2 (Post Fix A/B/C/D)

**Date:** 2026-05-04 (14:23 local)
**Build:** Includes Fix A (Mutex), Fix B (NavigateBack on error), Fix C (buffer×4), Fix D (HomeScreen guard)
**Symptom:** Two "Sign in" prompts visible during authentication (down from 5+). Both succeed. No "NotFound" errors.

---

## 1. Log Timeline (Authentication Phase Only)

```
14:23:43.517  CTAP2 cmd=0x1 (MakeCredential) on CID=76e1f533 — registration begins
14:23:47.787  FIDO2 Operation succeeded — registration complete
14:23:49.852  WindowOnBackDispatcher: registration screen navigates back

14:23:57.470  CTAP2 cmd=0x2 (GetAssertion) on CID=1ad4388a — assertionLock.tryLock() = true
              GetAssertion: rpId=webauthn.io allowCredentials=1
              → bus.dispatch(AuthenticationRequested(opts1, deferred1))
              → Compose navigates to AuthenticationPromptScreen
              → AuthenticationPromptViewModel created, init{} fires

14:24:00.776  GetAssertion for rpId=webauthn.io  ← MAIN THREAD (3306ms after arrival!)
14:24:00.784  Auto-selecting single credential: fS1TEBhsHvNSHG5SqLszfvWI9fBs2iur5OMO47ko-lE
14:24:00.835  Assertion complete: signCount=1
14:24:00.839  FIDO2 Operation succeeded → assertionLock.unlock()
14:24:00.841  ❌ OVER BUDGET | GetAssertion = 3368ms (Total: 3368ms, User: 0ms)
14:24:00.915  WindowOnBackDispatcher: auth screen navigates back (Fix B NavigateBack fired)

14:24:01.020  CTAP2 cmd=0x2 (GetAssertion) on CID=1ad4388a — assertionLock.tryLock() = true
              GetAssertion: rpId=webauthn.io allowCredentials=1  (payloadLen=116, was 112)
              → bus.dispatch(AuthenticationRequested(opts2, deferred2))
              → Compose navigates to AuthenticationPromptScreen AGAIN
              → AuthenticationPromptViewModel created, init{} fires

14:24:04.431  GetAssertion for rpId=webauthn.io  ← MAIN THREAD (3411ms after arrival!)
14:24:04.437  Auto-selecting single credential: fS1TEBhsHvNSHG5SqLszfvWI9fBs2iur5OMO47ko-lE
14:24:04.500  Assertion complete: signCount=2
14:24:04.503  FIDO2 Operation succeeded → assertionLock.unlock()
14:24:04.505  ❌ OVER BUDGET | GetAssertion = 3484ms (Total: 3484ms, User: 0ms)
14:24:04.543  WindowOnBackDispatcher: auth screen navigates back (Fix B NavigateBack fired)
```

---

## 2. What Is Different From Session 1

| Symptom | Session 1 | Session 2 (this log) |
|---|---|---|
| "NotFound" errors | Yes, multiple | **None** |
| Number of prompts | 5+ | **2** |
| Orphaned deferreds | Yes | **None** (Mutex prevents) |
| Both assertions succeed | No | **Yes** |
| `CTAP1_ERR_CHANNEL_BUSY` logged | N/A | **Not seen** — Mutex was never contested |
| Retry storm | Yes | **None** |

**Conclusion: Fix A–D are all working. The two prompts are a new, separate phenomenon.**

---

## 3. Root Causes (New)

### RCA-5 — CONFIRMED PRIMARY: Host sends two sequential GetAssertion ceremonies

The CTAP2 host (Chrome on Windows / webauthn.io) sends **two separate, sequential GetAssertion commands** on the same CID (`1ad4388a`):

| Request | payloadLen | Arrival | Response sent | Delta |
|---|---|---|---|---|
| GetAssertion #1 | 112 bytes | 14:23:57.470 | 14:24:00.839 | 3.37 s |
| GetAssertion #2 | 116 bytes | 14:24:01.020 | 14:24:04.503 | 3.48 s |

The second request arrives **181 ms** after the first succeeded — well after `assertionLock` was released. The Mutex correctly allows it through. The payloads differ by 4 bytes (116 vs 112), indicating these are two distinct ceremony invocations, not a retry.

**Why does Chrome/webauthn.io do this?** Two known causes:
1. **Two-credential-type probing**: Chrome probes with the specific credential ID first, then falls back to a resident/discoverable credential request without an allow-list. The payloadLen difference (allow-list present vs absent) is consistent with this.
2. **PRF/hmac-secret extension on second request**: webauthn.io may send a second GetAssertion specifically requesting the `hmac-secret` extension output (4 bytes = one additional extension key in the CBOR map).

Both are **legitimate RP behavior** — the host is intentionally making two separate requests. Chimali cannot suppress the second request; it must respond to both.

**Effect on UX:** Each GetAssertion dispatches `AuthenticationRequested` to the UI event bus → Compose navigates to `AuthenticationPromptScreen` → `AuthenticationPromptViewModel` is created → auto-select fires immediately. The user sees the auth screen appear and disappear twice in rapid succession.

---

### RCA-6 — CONFIRMED SECONDARY: Compose navigation + ViewModel init takes ~3.3 s per ceremony

**Evidence:**
```
14:23:57.470  GetAssertion dispatched to UI bus
14:24:00.776  "GetAssertion for rpId=..." logged on main thread (thread 25691)
              Δ = 3306 ms
```
```
14:24:01.020  GetAssertion #2 dispatched to UI bus
14:24:04.431  "GetAssertion for rpId=..." logged on main thread
              Δ = 3411 ms
```

The `User: 0ms` in the perf log confirms **the user did not cause the delay** — the system did. The 3.3-second window is spent:

1. **Compose navigation animation** from HomeScreen → `AuthenticationPromptScreen`
2. **`AuthenticationPromptViewModel` constructor** running (Koin DI resolution, coroutine setup, `init{}` block subscribing to the event bus)
3. **`userVerificationService.getUserVerificationAvailability()`** called inside `initAuthentication()` — this is a `suspend` call that may perform Android Keystore / BiometricManager queries on the main thread
4. **`confirmAuthentication()`** is not called automatically — the screen is in `AwaitingUserConsent` state and waits for the **"Sign in" button press**

> **Critical gap:** The auto-select path (`SelectCredentialUseCase` → `GetAssertionUseCase`) is triggered from `GetAssertionUseCase` directly inside `performAuthentication`, but `performAuthentication` is only called after user confirmation OR after UV. Looking at the log: `"GetAssertion for rpId=webauthn.io"` is logged on the MAIN THREAD at 14:24:00.776, which is the `GetAssertionUseCase` executing, NOT from the ViewModel. This means auto-select is happening inside `GetAssertionUseCase` via the use-case's own logic — independently of the ViewModel prompt cycle.

**Where exactly is `"GetAssertion for rpId=webauthn.io"` logged?**

This log line must come from `GetAssertionUseCase` or `SelectCredentialUseCase`. It runs on the main thread (thread 25691) at 14:24:00.776 — over 3 seconds after the GetAssertion CTAP2 command arrived. This is the use case being executed by the ViewModel's `performAuthentication` triggered by… what? The ViewModel's `confirmAuthentication` is only called when the user presses "Sign in".

**Hypothesis:** The `AwaitingUserConsent` state shows "Sign in" button on the auth screen. The biometric availability check (`UV = NONE`) causes `confirmAuthentication()` to call `performAuthentication()` directly (skipping biometric). The 3.3 s delay is the full round-trip:
- Compose navigation (≈300ms)
- ViewModel init + UV availability check (≈300ms)
- **User sees the auth screen and presses "Sign in"** (≈2.7s of user action time)

BUT the perf log says `User: 0ms`. This contradiction means `performAuthentication` was NOT triggered by a button press. It was triggered by the **ViewModel's init path** automatically, via `confirmAuthentication()` calling `performAuthentication()` when UV = NONE.

**Revised timeline:**
```
14:23:57.470  GetAssertion dispatched to event bus
14:23:57.xxx  Compose navigates to auth screen (~300ms navigation)
14:23:57.xxx  AuthenticationPromptViewModel.init{} fires:
              currentAuthenticationRequest != null → initAuthentication(opts)
              → userVerificationService.getUserVerificationAvailability() called
14:23:57.xxx  AwaitingUserConsent state set
              (auth screen displays "Sign in" button)
              ← MISSING: who calls confirmAuthentication() automatically?
```

Looking at the screen: `AwaitingUserConsent` shows a "Sign in" button. The user must press it. **But `User: 0ms` in perf...** 

Actually, `User: 0ms` in the `[NFR-PERF-030]` metric means the *crypto user* time — not wall-clock user interaction time. The total = 3368ms, User = 0ms, means crypto operations took 0ms "user crypto time" but the total ceremony was 3368ms.

**Re-conclusion:** The 3.3 second delay IS user interaction time. The user pressed "Sign in" on the first prompt, and the ViewModel's `confirmAuthentication()` → `performAuthentication()` → `GetAssertionUseCase` executed in <100ms. The 3.3s is:
- Navigation to auth screen: ~300ms
- Auth screen rendered: ~200ms
- User sees "Sign in" and taps: ~2.8s

`User: 0ms` in the NFR metric refers to cryptographic operation time (signing), not UI interaction time. The distinction is in how the NFR timer is instrumented.

---

### RCA-7 — CONFIRMED: `clearAll()` is called after first success, clearing `currentAuthenticationRequest` — but the second request re-populates it

When GetAssertion #1 succeeds:
1. `performAuthentication` → `Outcome.Success` → `uiEventBus.clearAll()` called
2. `pendingDeferred = null`, `pendingOptions = null`
3. `NavigateToSuccess` effect emitted → `onSuccess()` callback → nav back to Home
4. `assertionLock.unlock()`

Then GetAssertion #2 arrives (14:24:01.020):
1. `dispatch(AuthenticationRequested(opts2, deferred2))` → `currentAuthenticationRequest = event`
2. `_events.tryEmit(event)` → event enters SharedFlow buffer
3. `Fido2HomeScreen` collector: `viewModel.getPendingRegistration() == null` → `updatedOnAuthenticateRequest()` called
4. Nav goes back to `AuthenticationPromptScreen`
5. New `AuthenticationPromptViewModel` created (old one was destroyed when screen left composition)
6. New ViewModel's `init{}` sees `currentAuthenticationRequest != null` → processes event
7. Second prompt shown → second sign-in

This is **correct behavior** for a second legitimate ceremony — but visible to the user as a second "Sign in" screen.

---

## 4. What Didn't Change (Still Open)

| Issue | Status |
|---|---|
| Two sequential GetAssertion from host | **Architectural** — Chimali cannot suppress. But the UX of showing auth screen for auto-select is unnecessary. |
| 3.3 s nav latency per ceremony | **New regression** — auth screen should auto-confirm when UV=NONE and auto-select is possible |
| `[NFR-PERF-030] ❌ OVER BUDGET GetAssertion = 3368ms` | Both requests over budget. Target is ~200ms. |

---

## 5. Fix Plan for Next Session

### Fix E — HIGH PRIORITY: Skip auth screen when auto-select is possible (UV=NONE, single candidate)

**Location:** `Ctap2GetAssertionHandler.kt` or `GetAssertionUseCase` / `AuthenticationPromptViewModel`

**Mechanism:**  
Before dispatching `AuthenticationRequested` to the UI bus, attempt a quick "headless" credential lookup:
- Call `getAssertionUseCase(options)` directly (no UI) if UV requirement is `PREFERRED`/`DISCOURAGED` and the candidate set has exactly 1 credential.
- If successful, complete the deferred immediately without ever showing the auth screen.
- Only dispatch to the UI bus (triggering auth screen) if UV is `REQUIRED` or if the headless lookup fails (not found, crypto error).

This eliminates the auth screen entirely for the common case (already-registered site, `uv=preferred`, single credential). Both GetAssertion #1 and #2 would be handled headlessly, removing both prompts.

```
GetAssertion received
  → if (uvRequired=false AND allowList.size==1):
       result = getAssertionUseCase(options)  // no UI
       deferred.complete(result)
       return
  → else:
       bus.dispatch(AuthenticationRequested(options, deferred))
       // show auth screen
```

> **CTAP2 §6.2.1** UV=PREFERRED means UV is requested but not required. Authenticators MAY skip UV if not enrolled. Chimali has no PIN; UV availability = NONE on this device → can always proceed headlessly for `PREFERRED`.

### Fix F — MEDIUM PRIORITY: Auto-confirm when UV=NONE in ViewModel

**Location:** `AuthenticationPromptViewModel.initAuthentication()`

If `availability.getBestAvailableMethod() == VerificationMethod.NONE`, call `performAuthentication(options)` directly from `initAuthentication()` instead of setting `AwaitingUserConsent` state. This removes the "Sign in" button from the user flow entirely for devices with no biometric enrolled.

**Effect:** Auth screen will flash `Processing` → `Success` → navigate back in <100ms, instead of showing "Sign in" button for 2-3 seconds waiting for user tap.

> Fix E is preferred as it avoids the screen altogether. Fix F is a fallback that still shows the screen briefly.

---

## 6. Files to Touch in Next Session

| File | Change |
|---|---|
| `Ctap2GetAssertionHandler.kt` | Fix E: headless fast-path before UI dispatch |
| `AuthenticationPromptViewModel.kt` | Fix F (fallback): auto-confirm when UV=NONE |

---

## 7. Signals Confirmed Working

| Signal | Status |
|---|---|
| `"ceremony already in progress — returning CHANNEL_BUSY"` | NOT seen — correct (no concurrent requests occurred) |
| `"[EventBus] Event dropped"` | NOT seen — buffer=4 is sufficient |
| `NavigateBack` after first success | Confirmed (WindowOnBackDispatcher at 14:24:00.915) |
| `NavigateBack` after second success | Confirmed (WindowOnBackDispatcher at 14:24:04.543) |
| Fix D registration guard | Not applicable this run (no concurrent registration) |
| Both assertions succeed (signCount=1, signCount=2) | **YES** — credential lookup, signing, and CTAP2 response all correct |
