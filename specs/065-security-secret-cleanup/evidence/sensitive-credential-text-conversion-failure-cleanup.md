# Sensitive credential text conversion failure cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-048  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/SensitiveCredentialText.kt`

## Source review and correction

The UTF-8 decoder methods copied decoded characters from a staging `CharBuffer` directly into the owner constructor. If owner construction failed after the copy, the detached character array had no local cleanup owner. `fromBase64UrlBytes` had the same transfer shape. `copyOwner` copied the array before wrapper construction without a staging cleanup path. In `toUtf8Bytes`, the returned byte array was constructed inline with the buffer write, so an unexpected write failure could leave a partial caller result without cleanup.

The conversion methods now stage arrays until wrapper construction or output population succeeds. Failed exits erase the staged arrays; successful exits transfer ownership to the new text owner or caller. Existing decoder/encoder staging buffers continue to be cleared.

## Verification and limitations

Injected wrapper-construction failure verifies staged decoded/copy characters are erased; injected failure during UTF-8 result transfer verifies the partial caller byte array is erased. All 8 `SensitiveCredentialTextTest` cases pass, and FIDO KtLint passes. The full FIDO host run completed 1,365 tests but failed its separate simulated-latency assessment: one measured MakeCredential operation was 261 ms against its declared budget. This does not fail the focused conversion class. Downstream caller erasure, platform `String` boundaries, JCA/ICU copies and heap-root absence remain open.
