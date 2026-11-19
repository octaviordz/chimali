# BIP39 mnemonic construction failure cleanup

Date: 2026-11-18  
Finding: SEC065-SEED-004  
Source: `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGenerator.kt`

## Source review and correction

`entropyToMnemonic` previously allocated the SHA-256 checksum before allocating the index array, with cleanup beginning only after both allocations. If index allocation failed, the checksum had no cleanup owner. It also built the secret mnemonic in a local array and returned it without retaining a cleanup path for an unexpected failure while copying the words.

The method now stages checksum, indices and phrase as local owners under one `try/finally`. On successful construction, the phrase is transferred to the caller; on every other exit the phrase is zeroed. Checksum and index owners are always cleared.

## Verification and limitations

This is source inspection and correction only. No regression was added or run, and allocation or word-copy failure was not injected. The review does not establish Android ICU/JCA provider copies, caller terminal cleanup, runtime behavior or heap-root absence.
