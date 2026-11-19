# Android app security integration-test owner map

**Date:** 2026-11-15  
**Scope:** Source review of three app instrumentation tests and the dedicated process-restart runner. No connected-device test was run in this pass.

## Provider-backed synthetic credential signing

`CredentialCryptoProviderTest` confirms that the Bouncy Castle provider is loaded from the target APK, then creates synthetic P-256, Ed25519 and ML-DSA key pairs, round-trips public keys, signs synthetic messages and verifies signatures. This is relevant to private-key handling in `CLS-KEY-004/005`. The test does not explicitly erase key objects or byte arrays, and cannot inspect provider/JCA/native copies. It does not use stored application credentials or a real user secret.

## Mnemonic owner disposal

`LeakCanaryMnemonicOwnerTest` imports a known synthetic mnemonic into a UUID-scoped test DataStore, verifies the mutable import buffer is zeroed, reveals the ViewModel-owned mnemonic buffer, closes the activity and verifies that same buffer is zeroed. Its `finally` clears the provider session and deletes the UUID-scoped fixture directory. `LeakAssertions.assertNoLeaks()` checks lifecycle leaks, and a filesystem assertion rejects leftover HPROF files in the app files/cache directories. These checks do not inspect heap fields/roots for secret content or establish provider/native cleanup. Instrumentation was not executed in this review.

## Production app process restart

`ProductionAppProcessRestartTest` and `tools/run-hdk-app-process-restart-test.ps1` coordinate registration and assertion in two instrumentation invocations, with a force-stop between them. The fixture stores only the prior PID and synthetic credential ID under a per-run app-private directory. The assertion phase clears its mutable challenge, assembled message and signature buffers, clears the seed-provider session and deletes the fixture directory in `finally`. The runner finally force-stops and uninstalls both disposable packages. If the runner itself is terminated before its `finally` executes, cleanup is not established by source inspection. This workflow exercises persisted synthetic credential recovery, not a user's profile. It was not executed in this review.

The three tests are now Relevant in the source inventory and linked to the applicable key/credential classifications. Their source ownership mapping improves T005/T006 coverage but does not close those tasks, the connected-device requirements, T015 heap review, or frozen-candidate evidence.
