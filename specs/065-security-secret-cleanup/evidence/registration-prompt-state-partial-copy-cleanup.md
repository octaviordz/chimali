# Prompt consent-state partial owner-copy cleanup

**Date:** 2026-11-17
**Findings:** T006/T012; SEC065-FIDO-OWNER-005

Registration and authentication prompt ViewModels constructed consent states directly from multiple `SensitiveCredentialText` copy calls. If a later copy or state constructor failed, earlier returned owners had no terminal holder. Both paths now use `withSensitiveTextOwnerCopies`: the helper preallocates an owner slot array, records each completed copy, transfers the group only after state construction succeeds, and erases every completed owner on any exceptional exit.

A regression injects failure after two owners are created and verifies both owners are zeroed. The registration owner integration suite passes 26 tests. The full FIDO Android host suite passes 1,329 tests across 172 XML reports with zero failures, errors, or skips; `:feature:fido2:ktlintCheck` passes.

This closes the synchronous partial state-construction path only. Once transferred, mutable text is owned by the ViewModel state and erased at state replacement or ViewModel clear. Compose, AndroidX BiometricPrompt, system renderer copies, framework lifecycle, provider/runtime copies, and heap-root behavior remain open.

- Registration ViewModel source SHA-256: `bbd9fb5c7b7918d7bb51386101826cc0803ea9036a05900753cfd44bc8404d4d`
- Authentication ViewModel source SHA-256: `caca42b81dbf662cc570095d24d9e4622af7e7cff54eac5842d39bbc5f7d3d7a`
- Regression test source SHA-256: `895032054b30285894f33d7b97d00843f1af2e6d905bffc79004740213431252`
- Focused XML SHA-256: `c75569d88bb4f9c2b938b691f4d2524f3ca4dca320bb280068b7af96912b2ee4`
- Full host report index SHA-256: `12e549c722c0053190d4e56d6414ee9936b85865aafd2cd3fb009bcfe77f5b0e`
