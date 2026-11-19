## SEC065-CRYPTO-005 — P-256 HDK/HKDF owner and conversion review

Mapped the P-256 child derivation, HKDF-SHA256 helper, P-256 group operations, and their three host test classes as distinct source/test usages under `CLS-KEY-003` (HKDF also references `CLS-KEY-001/002`). The source-level owner path is now explicit:

- `HdkP256` borrows caller secret/scalar arrays, clears its temporary digest, preimage, context and untransferred outputs, then transfers factor/private-scalar arrays through `Result`; caller must erase those arrays after signing.
- `HkdfSha256` borrows seed/PRK inputs, erases temporary salt/framing/block/previous buffers, and transfers output arrays to callers. `Mac`/`SecretKeySpec` provider copies are outside direct erasure control.
- `P256Group` clears the temporary signed byte array in scalar serialization. Deserialization and curve arithmetic create immutable `BigInteger`/`ECPoint` values that cannot be explicitly overwritten; operation-local scope is not heap-erasure evidence.

The complete `:core:security:testAndroidHostTest` suite passes 72 tests across 13 reports, including six `HdkP256Test`, nine `HkdfSha256Test`, and nine `P256GroupTest` cases. These tests establish current host behavior and selected caller-array cleanup contracts, not provider copies or post-operation heap roots. T005/T006 owner coverage is improved but remains open; T015 still needs controlled heap evidence, and Android runtime/provider behavior remains unverified.

Report SHA-256: HdkP256 `1FCEB3CE4CB0939243318B5ABA59BEC631FBBE97BC40FA8DC72358DD2A3B59F4`; HkdfSha256 `EA252D6E0BBE11E166119CF7B99F1D367F43DC97B658F0A9806928EB23BE590F`; P256Group `17D26E8EE3B54D15C972DD279CBB21DEFB145A5F9FC3DD59D243F97B7C08AEC1`; suite report `E40ED0A5538BFBE0498B97531DBA552B0B6152D3D71320F8450B18EE805BB2BF`.
