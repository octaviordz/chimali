# Secure memory helper and mnemonic buffer review

Date: 2026-11-15
Finding: SEC065-CORE-026

## Disposition

The unused `SecureMemory` helper was removed from `core/security/src/androidMain/kotlin/com/chimali/core/security/util/SecureMemory.kt`. A repository-wide source search found no production or test caller for `SecureMemory`, `wipe(ByteArray)`, `wipe(CharArray)`, or `useSensitive`; the only matches outside historical audit records were declarations in the helper itself. The deleted utility therefore contributed no active cleanup path. Historical audit references are retained as historical records.

The active owner is `ErasableCharBuffer`, used by `Bip39MasterSeedGenerator` to normalize a borrowed mnemonic. Growth copies to a larger owner and clears the replaced array. `erase()` fills the full allocated array and releases live length. Existing host tests inspect actual replaced/current backing arrays, and the generator tests inspect the normalized owner lifecycle. These source and host assertions do not establish JVM/provider copies, caller/IME immutable strings, Android runtime behavior, or heap-root absence.

The resource loader was separately assessed: its active consumer loads the bundled public 2048-word BIP39 English vocabulary. The resource-loader declarations/implementations and vocabulary test do not carry a user's mnemonic or derived seed; those remain owned by the generator path. The iOS actual currently has no active consumer.

`SecurityModule` component-scans security services including `Bip39MasterSeedGenerator` and binds the P-256 HDK implementation. It is mapped as a dependency-reachability/composition source, not as a secret-value owner. Existing module tests verify selected service resolution only and do not prove cleanup.

## Evidence limits

This is a source-level dead-code and ownership review plus existing host-test coverage. It is not a heap-dump, provider, platform, iOS, or device acceptance result. T005/T006/T012/T015 remain open.

## Security API and fixture inventory extension

The previously Open common security API files are now mapped as contract owners for P-256 inputs/outputs (`HdkManager`, `HdkTypes`), mnemonic/seed generation (`MasterSeedGenerator`), and cached wallet key/session ownership (`MasterSeedProvider`). These are contract-level traces and do not independently prove each consumer obeys the contract. The static `KeyWrappingPolicy` declaration contains only a policy version and prose and has no production/test caller; it is assessed as no sensitive-data use.

`local-profile-v1.json` contains deterministic synthetic private scalars, child factors and other cryptographic vectors. Its provenance is the independent reference generator in `tools/hdk-reference`, and Feature-055 review records cite it. Current source search found no product-test consumer. It is retained and explicitly mapped as a synthetic review fixture, not claimed as an executed current test.
