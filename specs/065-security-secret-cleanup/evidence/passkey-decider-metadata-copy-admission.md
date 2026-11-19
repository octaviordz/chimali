# Passkey decider metadata copy admission cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-016

## Source correction

Registration event creation and all event-driven PasskeyState transitions copied five mutable metadata arrays sequentially before constructing the event/state. A later copy or model-construction failure could leave earlier copies without a cleanup owner. These operations now use `withOwnedPasskeyMetadata`, which stages the four character arrays and credential-ID bytes until construction succeeds and erases every staged array on failure.

## Verification and limitations

The new domain regression injects failure at every copy position and during final event/state construction. It verifies all staged arrays are zeroed and source arrays remain unchanged. The full domain host suite passes 109 tests across 24 reports; the full FIDO host suite passes 1,342 tests across 173 reports. Both domain and FIDO KtLint checks pass with no test failures, errors or skips.

This covers application-owned event/state copy staging. Serialization, database/provider copies, downstream aggregate lifetime, allocation exhaustion and heap roots remain separate open boundaries.
