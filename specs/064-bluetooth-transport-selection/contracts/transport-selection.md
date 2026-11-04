# Contract: Transport Selection

## User Interface

Location: Settings > Passkey Authenticator > Bluetooth HID. Keep the requested section name even though FIDO BLE is distinct from HID over GATT.

Expose exactly two radio options: Classic HID and FIDO BLE. Checked radio means successfully saved preference. Separate text identifies the active process mode, or explicitly says the process-selected mode is unavailable. If saved and process choices differ, announce the pending mode and "Restart Chimali to apply this change."

Restart means full process termination and relaunch; Activity recreation, navigation or service restart is insufficient. No automatic restart/force-kill action. Returning the saved choice to the current process mode clears pending.

Loading shows no invented active claim. After the snapshot and non-initializing capability check, SelectedNotStarted shows the saved and process-selected mode as “Not started”; it is neither active nor reported as available. Starting shows activation in progress. A known unsupported device, missing required permission or disabled Bluetooth is presented as the corresponding unavailable reason when determinable during the startup capability check; otherwise failure is reported when activation is attempted. Disable changes during resolution/save and expose progress. Failed write reports that selection was not saved and preserves the previous checked value. Transport failure leaves settings accessible; after configuration has been read, choosing the alternative applies next process.

Each option exposes accessible name, radio role and saved selection. Active/pending/error status uses readable text, not color alone. Announce errors and pending restart, retain focus, and support TalkBack, high contrast and 200% scaling. Use existing typography/theme and localized resources for new text.

## Repository and Runtime Boundaries

Extend Fido2SettingsRepository with observation and durable save operations. Invalid input, read failure and write failure have explicit distinct outcomes. On a persistent read failure, keep selection changes disabled and provide retry, device-storage and relaunch guidance, then recommend technical assistance if the error persists; do not reset the shared file or suggest clearing app data. A read-only domain runtime boundary exposes process choice and availability from the controller. The ViewModel observes both; UI cannot acquire the handle or mutate runtime state.

The app composition root provides one unqualified Fido2Transport handle. Its process gate resolves a preference snapshot before concrete transport construction or Bluetooth initialization. Android may create the service shell first, but service code must defer all transport use and construction until resolution. After a choice is frozen, run a selected-mode capability check using only reliably available signals, without initializing either transport; report known unsupported, permission-required or Bluetooth-off conditions, and retain SelectedNotStarted if activation is needed to determine support. Service and home share the gate. Construct only the selected implementation on a start request after resolution; avoid competing generated Koin interface bindings.

The snapshot operation alone freezes choice. Its 5s deadline starts at snapshot launch and ends on choice resolution or failure. After resolution, the first transport-start request begins lazy selected-transport activation with its own 5s deadline ending on ready or failure. Concurrent callers share that activation attempt. On timeout/cancellation the controller fences late callbacks and releases attempt-owned resources before retry; an explicit retry after correcting a recoverable condition starts a fresh activation deadline for the same frozen choice. A read retry may start a fresh snapshot deadline only before a choice has been frozen. Settings writes cannot replace the delegate. These safety bounds do not extend the cold-start budget. Service restart/reconnect uses the same choice.

The BLE prerequisite must reconcile existing connect/connectDevice/disconnect consumers and HID-specific state, secure pairing and cleanup. Missing compatibility blocks integration. No BLE-through-Classic emulation or placeholder default is acceptable.

## Acceptance Observations

| Feature IDs | Required evidence |
| --- | --- |
| FR-001–003 | Hierarchy, labels and absent-value BLE default including development installations |
| FR-004, FR-010 | Durable preference, one startup snapshot before hardware, reboot/process-death survival |
| FR-005–007 | Active/pending distinction, pending cancellation, accessible state |
| FR-008–009 | Independent real Windows registration and assertion for each transport |
| FR-011 | Selected unavailable, no fallback construction/connection, accessible recovery |
| FR-012 | Mid-ceremony save alters preference only; original connection and response complete normally |

Map feature-local IDs to canonical BRD/TRD IDs before Tier 1 annotations and tests. This selector introduces no public network API or new wire protocol.
