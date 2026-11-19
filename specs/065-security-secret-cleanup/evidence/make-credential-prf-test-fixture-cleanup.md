# MakeCredential PRF test fixture cleanup

**Finding:** SEC065-FIDO-008  
**Scope:** Synthetic request and observer buffers owned by `Ctap2MakeCredentialPrfInputTest`; no user credential is used.

The terminal-path test exercises registration failure and cancellation and observes production-decoded salt/text owners. The test previously left its source salts, challenge, user ID, encoded request, packet payload, channel ID, and observer-held mutable references alive after each parameterized case. It now clears source arrays and encoded/payload buffers in `finally`, erases observed text owners, clears observer lists, and clears the channel fixture in `@AfterTest`. The test also zeros its temporary character copy after checking decoded text cleanup.

The focused `Ctap2MakeCredentialPrfInputTest` run passes both parameterized cases (failure and cancellation). The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck :feature:fido2:detekt` run passes 1,270 tests across 167 XML reports with zero failures/errors/skips; KtLint and Detekt pass. XML SHA-256: `d6b4cc481801dba7b058c815bd79f02e5865771da6ab03f4cff09d4f25502a2f`.

This is test-fixture cleanup only; it does not expand production request, provider or heap-root evidence (T005/T006/T015).
