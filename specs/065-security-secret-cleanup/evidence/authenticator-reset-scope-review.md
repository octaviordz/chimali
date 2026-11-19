# Authenticator reset scope review

Date: 2026-11-15
Finding: SEC065-DB-010

## Reachability

`Ctap2ResetAuthenticatorHandler` delegates to `ResetAuthenticatorUseCase`, which delegates to `CredentialRepository.resetAuthenticator`. Repository-wide direct-reference search found no production dispatcher/router caller for the handler; the only direct construction is in `SimulatedAssessmentFixture`, which runs against a synthetic/mock repository. The handler remains Koin-annotated, so its DI availability alone does not prove protocol routing. Its `requestBytes` parameter is unused; upstream CTAPHID message cleanup owns parsed request payload storage.

`ResetAuthenticatorUseCaseTest` verifies only success/error delegation. The performance fixture executes the handler with an empty synthetic payload and a mock repository; it is not a production persistence acceptance test.

## Persisted-data scope gap

The `CredentialRepositoryImpl.resetAuthenticator` implementation reads credential copies, deletes them one by one through the event aggregate/projection path, and erases batch copies in `finally`. The missing logical-row cleanup has now been added: consent rows are deleted first, then all relying-party rows, in one SQLDelight transaction. SQLDelight defines consent `ON DELETE CASCADE` as an additional relationship, but reset does not depend on cascade being enabled for cleanup. A new in-memory SQLDelight repository regression seeds both row types, injects a trigger failure on RP deletion, verifies transaction rollback preserves both tables, then retries reset and verifies both tables are empty. The focused destructive-batch suite and FIDO KtLint pass.

SEC065-DB-010 is only partially remediated. Repository-wide tracing still finds no production dispatcher/router caller for the reset handler, so production protocol reachability remains unproven. Reset deletes logical rows and does not erase historical event-store payloads or claim SQLite page/WAL/journal physical scrubbing. Existing use-case/handler wording about all settings/factory defaults still needs scope reconciliation. The focused run is not a full FIDO suite or platform/device acceptance.

## CTAP command routing and safety prerequisites

The reset handler is not injected into `BluetoothHidTransportImpl` or its CTAP command switch; unknown CBOR commands currently receive the unsupported-command response. It must remain unrouted until a user-presence confirmation is enforced for the selected transport. The current handler immediately invokes repository deletion and has no user-presence step. The official [FIDO CTAP 2.3 authenticatorReset section](https://fidoalliance.org/specs/fido-v2.3-ps-20260226/fido-client-to-authenticator-protocol-v2.3-ps-20260226.html) requires evidence of user interaction and permits transport-specific disabling; the 10-second power-up window is specifically required for authenticators with no display. Chimali currently runs on a display-equipped Android device, so that no-display timing rule does not replace its missing user-presence gate. The handler/use-case comments were corrected to describe their actual storage scope and no longer promise factory reset, PIN reset or biometric reset.
