# HDK result generic formatting correction

Date: 2026-11-18  
Finding: SEC065-CORE-046  
Scope: `HdkKeyPair` and `HdkChild`

## Source review

Before the correction, each type's `hashCode()` incorporated its private scalar or child factor. Neither type overrode `toString()`, so Kotlin/JVM inherited generic object formatting derived its visible suffix from the secret-dependent hash. This exposed a deterministic 32-bit fingerprint, not the full scalar or factor. The source-reference review did not establish a current logging or formatting sink.

Both types now use a fixed class hash and a fixed redacted `toString()` value. Content-based equality remains unchanged, is not constant-time, and may still be invoked explicitly by callers. This correction was based on source inspection; no regression test was added or run, so the behavior change is not test-verified.

## Remaining scope

The provider and `Fido2CryptoService` return/call paths require continued terminal-owner reconciliation. Test fixtures, equality/hash collection callers, provider internals, heap roots, Android/iOS runtime behavior and frozen-candidate behavior are not established by this source correction. The existing core-security test evidence predates this edit and is not attributed to it.
