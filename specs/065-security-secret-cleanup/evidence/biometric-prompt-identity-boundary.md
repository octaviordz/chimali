# FIDO biometric prompt identity boundary

**Date:** 2026-11-17  
**Scope:** `BiometricPromptComponent` and the in-repository caller in `CredentialListScreen`.  
**Classification/boundary:** `CLS-FIDO-001/002`; `BND-UI-009`.  
**Finding:** SEC065-FIDO-BIOMETRIC-001.

`CredentialListScreen` displays a delete-confirmation dialog containing the account name and constructs a biometric subtitle containing the selected credential's RP ID. `BiometricPromptComponent` transfers the subtitle to AndroidX `BiometricPrompt.PromptInfo` and renders the caller-provided title/subtitle/optional description in Compose. On prompt disposal it calls `cancelAuthentication()`.

The only in-repository caller clears its prompt credential on success, error or fallback. Its `onError` callback ignores the platform error code and message and displays a fixed “Authentication failed” snackbar. The component itself emits no diagnostic. Platform, Compose, accessibility and heap copies, process interruption, and prompt disposal timing remain unverified; caller-created immutable String snapshots are GC-managed. This is a mapped system UI boundary, not a claim of erasure.
