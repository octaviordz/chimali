# SEC065-CORE-042 — MakeCredential response CBOR temporary cleanup

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`Ctap2MakeCredentialHandler.handleMakeCredentialRaw` received the encoded attestation CBOR array, copied it into a status-prefixed `RawCtapResult.responseBytes`, then returned without clearing the original array. The handler erased the attestation source object but left this separate encoded response temporary populated.

The successful registration regression retains the array returned by the synthetic CBOR encoder. It failed before correction because the captured temporary was nonzero after response framing. The handler now clears that encoded array in `finally` while retaining the distinct response payload for transfer to the HID layer. Attestation owner cleanup still runs on response success or failure.

Verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.ctap2.Ctap2MakeCredentialHandlerValidationTest :feature:fido2:ktlintCheck` passes all 24 validation cases and FIDO KtLint.

Limits: the test covers this app-owned intermediate array only. `RawCtapResult` and framed response arrays are deliberate caller-owned protocol output; CBOR encoder/provider internals, transport copies and heap roots remain unverified.
