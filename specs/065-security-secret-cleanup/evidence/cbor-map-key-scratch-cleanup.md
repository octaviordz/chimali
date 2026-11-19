# CBOR map-key encoding scratch cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-052  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CborEncodingEngine.kt`

## Source review and correction

The recursive encoder encoded each map key into a regular `ByteArrayOutputStream`; `toByteArray()` returned a copy, but the stream's internal buffer containing the encoded key was not erasable. A second partial-transfer edge existed if the copied key bytes were created but `EncodedMapEntry` construction or list admission failed before the map-entry cleanup list owned them.

Map-key encoding now uses `CborCodec.ErasableByteArrayOutputStream` and erases its backing buffer in `finally`. Encoded key arrays are also staged until the `EncodedMapEntry` is successfully admitted; a failed admission clears that array immediately. The existing outer cleanup clears admitted key arrays on all exits.

## Verification and limitations

The new map-key admission failure regression injects an exception after key encoding and verifies that both the copied key array and temporary UTF-8 bytes are zeroed. `CborCodecTest` passes 20 tests, and `:feature:fido2:ktlintCheck` passes. This review does not establish caller output cleanup, immutable source-String retention or heap-root absence.
