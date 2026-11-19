# Wallet parent-key copy admission cleanup

Date: 2026-11-18  
Finding: SEC065-SEED-003  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt`

## Source review and correction

`initializeLocked` copied `parentResult.privateScalar` and `parentResult.publicKey` as constructor arguments. `getDeviceKeyPair` did the same from the cached parent. If the later public-key copy or `HdkKeyPair` construction failed after the private-scalar copy completed, the scalar copy had not yet transferred to an `HdkKeyPair` and was not covered by either caller's terminal cleanup. The provider's outer cleanup only erased the original derivation/cache owner.

Both paths use `copyOwnedParentKeyPair`, backed by `copyOwnedHdkKeyPair`. It retains both mutable copies until `HdkKeyPair` construction succeeds, transfers ownership only on successful construction, and erases both staged arrays in `finally` on every failed exit. This also covers failure while creating the public-point copy after the private scalar copy has been created.

## Verification and limitations

`HdkKeyPairCopyOwnershipTest` passes two injected failure cases: construction failure after both copies and public-copy failure after private-copy admission. The tests assert staged arrays are zeroed and source arrays remain unchanged. The full FIDO Android host suite passes 1,347 tests across 175 reports with zero failures, errors or skips; FIDO KtLint passes.

The injected seams do not establish allocator/runtime behavior, provider/JCA copies, process interruption or heap-root absence. On success, the returned private scalar remains caller-owned and must still be erased by the receiving crypto operation.
