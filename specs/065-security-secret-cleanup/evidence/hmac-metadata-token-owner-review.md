# HMAC metadata lookup-token owner map

**Date:** 2026-11-15  
**Scope:** `MetadataLookupTokenService`, `HmacMetadataLookupTokenService`, and `HmacMetadataLookupTokenServiceTest`. No production source correction was required in this review.

`MetadataLookupTokenService` borrows the domain string and metadata `CharSequence`; generated token arrays are caller-owned and must be erased by the direct caller. The Android implementation copies a provisioned key into service ownership, zeros the prior copy on replacement, and zeros/drops it on `clearKey()`.

For each token operation the service snapshots the active key while holding `keyLock`. It then creates domain UTF-8 and metadata UTF-8 arrays and invokes the MAC synchronously. The operation `finally` erases the key snapshot and both byte arrays on success and failure. The strict encoder zeroes its backing `ByteBuffer` array in its own `finally`, including malformed surrogate input. Replacement/clear racing after the snapshot does not erase a key buffer still borrowed by an admitted operation; the operation uses its snapshot and erases it at its own terminal event. The returned HMAC token is a separate caller-owned array.

The host suite contains deterministic/domain-separation checks, a gated concurrent-clear test, replacement and clear tests, and retained-reference assertions for operation-key/domain/value/scratch erasure after success and malformed UTF-8 failure. It also verifies that the borrowed malformed `CharArray` is unchanged and that the caller erases returned token outputs in the selected cases. The full core-security host suite passes 72 tests across 13 reports with zero failures/errors/skips; KtLint passes.

The tests and source cannot inspect internal key copies in `SecretKeySpec`/`Mac` or native/provider storage. The token array is returned to higher-level callers; their complete terminal release, SQLDelight copies and database lifetime remain open under T005/T006/T012/T015. Some successful token test outputs are erased after assertions rather than by a per-test cleanup fixture, and synthetic values are used throughout. No Android production-provider runtime or heap claim is made.
