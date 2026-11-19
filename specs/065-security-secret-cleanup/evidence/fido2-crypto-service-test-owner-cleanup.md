# FIDO2 Crypto Service Test Owner Cleanup

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`Fido2CryptoServiceTest` retains references to synthetic root-key copies and parent key pairs issued to the service. Its `@AfterTest` hook now erases every retained root copy, erases parent private keys, and clears both tracking lists before wiping the class fixtures. This enforces cleanup even when an assertion exits a test before its local assertions inspect the service's cleanup.

Verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.Fido2CryptoServiceTest :feature:fido2:ktlintCheck` passed. All 12 class cases pass; KtLint passes.

The source inventory now separately maps the production derivation/signing service and its test owner. The production source clears application-owned seeds, private arrays, identifiers, factors and untransferred signatures/public keys on their terminal paths. Provider-owned `Ed25519PrivateKeyParameters`, ML-DSA/JCA key objects, `BigInteger`/EC intermediates and provider copies remain opaque to these array assertions.

**Limits:** Host tests do not establish provider/native key-object erasure, external signing-provider copies, caller release of successful outputs, managed-heap root removal, or physical-device behavior. T005/T006/T012/T015 remain open.
