# Credential summary owner construction cleanup

## Scope

`CredentialSummary.fromText` and `CredentialSummary.copy` create a mutable `SensitiveText` RP owner before constructing the summary wrapper. If wrapper construction fails, the copied owner must be erased rather than abandoned.

## Change and observation

`fromText`, `fromRpId` and `copy` now use the staged owner path. The `RpId` secondary constructor was removed so no caller can create the mutable owner during constructor delegation; internal tests and fixtures now use the explicit factories. The private factory retains a newly created owner until summary-wrapper construction completes and erases it on failure. A regression supplies an unsupported policy through the owned-text construction seam, expects the constructor error, and verifies every staged RP character is zeroed. Domain, FIDO main/host and FIDO device-test sources compile; KtLint passes for the changed domain common sources and FIDO host/device-test sources. The focused domain model/policy and FIDO candidate/repository ownership classes passed 67 tests, and the complete `:core:domain:testAndroidHostTest :feature:fido2:testAndroidHostTest` run passed 1,438 tests across 196 reports with zero failures, errors or skips.

## Limits

Allocation failure was not injected; the regression covers the deterministic constructor-validation failure path, not JVM exhaustion or provider/native copies. The public constructor API was replaced by explicit `fromText`/`fromRpId` factories; all in-repository constructor callers were migrated. Caller terminal cleanup, provider/runtime copies, immutable strings and heap roots remain open; runtime status remains Unverified.
