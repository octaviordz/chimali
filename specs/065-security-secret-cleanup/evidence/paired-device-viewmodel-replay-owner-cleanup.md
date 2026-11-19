# Paired-device ViewModel replay and event-owner cleanup

- **Finding:** SEC065-FIDO-005
- **Owners:** `PairedDevicesViewModel.pairedDevices`, `pendingDeleteState`, and `_removalEvents`
- **Classification:** CLS-FIDO-004 (paired Bluetooth host identity and association metadata)
- **Requirements:** FR-003/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T015/T027/T041
- **Synthetic fixtures only:** host-only paired-device rows use synthetic addresses and aliases.

## Change

The `stateIn(WhileSubscribed)` replay cache now expires at zero after the configured 5-second no-subscriber stop timeout. `onCleared()` replaces the pending-delete set with an empty set and cancels the buffered removal-event channel. The change releases app-owned references to paired-device records after UI collection stops or the ViewModel is cleared. It does not erase immutable `String` fields or repository/database/native copies.

## Regression coverage

`PairedDevicesViewModelOwnershipTest#pairedDeviceReplayCacheExpiresAfterUiUnsubscribes` collects a synthetic paired-device row, cancels the collector, advances the test scheduler through the 5-second stop timeout, and verifies `pairedDevices.value` is empty. `#viewModelClearDiscardsBufferedRemovalDeviceOwner` enqueues a synthetic removal event, clears the `ViewModelStore`, and verifies the event flow is cancelled rather than retaining/delivering the buffered record.

## Verification

- Focused ownership tests: 2 passed, 0 failed/errors/skipped.
- Full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`: 1,250 tests across 167 XML reports; 0 failures, errors, or skips; KtLint passed.
- **Environment:** Windows JVM host, Gradle 9.6.0, JDK 25; synthetic values only.

## Limits

No Android device or UI instrumentation was used. This evidence does not prove Compose collector disposal, database/native storage cleanup, backup/deleted-page behavior, or heap-root absence. Immutable address/alias Strings remain GC-managed. T005/T006/T015/T027 and complete candidate/reviewer reconciliation remain open.

## Follow-up: minimize buffered removal data (2026-11-17)

The removal channel now carries only the MAC address needed by Undo or commit, rather than buffering the full `PairedDevice` record. `PairedDevicesSection` displays the fixed text “Device removed” instead of interpolating the alias or Bluetooth name into the snackbar. The lifecycle regression now supplies only an address and verifies that the synthetic alias is absent from snackbar semantics. A host regression verifies that the event value is the address; the existing disposal test verifies that ViewModel clearing cancels a buffered address event.

The focused `PairedDevicesViewModelOwnershipTest` class passes 5 cases. The complete FIDO host suite passes 1,327 tests across 172 XML reports with zero failures, errors, or skips; KtLint passes. `:feature:fido2:compileAndroidDeviceTest` compiles the UI lifecycle test source. The instrumentation test was not run on a device. These results establish app-owned event minimization and source behavior only; immutable MAC/name/alias Strings, Compose/platform snapshots, repository/database copies, concurrent collectors and heap roots remain open.
