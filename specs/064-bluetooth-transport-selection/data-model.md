# Data Model: Transport Selection

## TransportMode

Legal values: FIDO_BLE and CLASSIC_HID, persisted as stable explicit strings `fido_ble` and `classic_hid`, never enum ordinals or localized labels. UI names are FIDO BLE and Classic HID. Missing field defaults to FIDO BLE; an unknown explicit value is a configuration failure, never a reason to activate another transport.

## Transport Preference (durable)

| Field | Storage | Meaning |
| --- | --- | --- |
| bluetoothTransport | ProtoNumber 9, string, default fido_ble | Choice for next process |
| bluetoothTransportSelectedAtEpochMillis | ProtoNumber 10, Long, default 0 | Last successful explicit selection; 0 means no explicit choice |

Timestamp is nonnegative and informational, not an activation/ordering authority. Update both fields atomically, copying the current preferences to preserve fields 1–8. Re-selecting the saved mode is a no-op. Failed writes preserve prior choice/timestamp. Serialize saves or disable input while saving. No migration for an old default, persisted active/pending flags, credential events or separate encrypted store.

## Process Transport (volatile)

| Field | Meaning |
| --- | --- |
| processChoice | One immutable startup mode, absent while unresolved |
| availability | Loading, SelectedNotStarted, Starting, Ready, Unavailable(reason) |
| reason | PreferenceReadFailed, InvalidPreference, InitializationTimeout, Unsupported, PermissionRequired, BluetoothOff, StartupFailed |
| selectedHandle | Controller-owned process instance, never persisted or exposed to settings |

Loading means the preference or initial capability check is unresolved. SelectedNotStarted means a choice is frozen, support is not known to be absent, and activation has not been requested; it is not active and does not assert that activation will succeed. Starting means the selected activation attempt is in progress. Ready means initialized and available, not connected to a host. Connection/ceremony state remains transport-owned. Unavailable is never presented as active. Immediately after freezing the choice, perform a bounded, non-blocking and non-initializing selected-mode capability check using only reliably available platform signals. A definitive unsupported result is Unavailable(Unsupported); permission-required or Bluetooth-off results are explicit recoverable unavailable reasons. If capability cannot be determined without activation, use SelectedNotStarted and report any failure when activation is attempted. Do not construct or initialize either transport for this check. The alternative implementation opens no hardware resources.

## Derived Settings State

Saved choice, process choice, availability, saving/error state and `restartRequired = savedChoice != processChoice` when both are resolved. Disable writes until startup read resolves, preventing an early save racing the snapshot. On unreadable configuration show an error/retry without inferred active mode or file reset. Keep selection changes disabled while the shared record remains unreadable. If retry fails, guide the user to check device storage, relaunch Chimali and seek technical assistance if the error persists; preserve the shared preferences and all unrelated app data. Do not suggest clearing app data or claim recovery has succeeded.

## Transitions

| Trigger | Durable state | Process state | UI |
| --- | --- | --- | --- |
| New process, no stored choice | Default BLE | Freeze BLE; check known capability without initializing transport | BLE selected; not started until activation, active only when ready |
| New process, Classic saved | Preserve Classic | Freeze Classic; check known capability without initializing transport | Classic selected; not started until activation, active only when ready |
| First start request | Unchanged | SelectedNotStarted becomes Starting; initialize selected mode only | Starting, then active on ready or unavailable on failure |
| Save alternative succeeds | Choice and timestamp updated | Unchanged | Pending and restart instructions |
| Save fails | Unchanged | Unchanged | Error; previous saved selection |
| Save process choice again | Update if different from saved | Unchanged | Pending clears |
| Activity/service recreation or reconnect | Unchanged | Same choice/handle | Pending retained |
| Selected transport cannot start | Unchanged | Selected unavailable | Error; explicit alternative choice |
| Read error/timeout | Unchanged | No transport starts | Retry/error |
| Process termination/relaunch | Saved value retained | New snapshot/handle | Saved choice applies |

Saving has no connect/disconnect, service stop/start, DI rebinding, authorization or credential/event side effects. Corrected permissions/Bluetooth state may retry the same process choice. Read retry may resolve choice only before it is frozen. The snapshot attempt has a 5s deadline from launch to a frozen choice or failure. Each lazy activation attempt has a separate 5s deadline from transport-start request to ready or failure. Concurrent callers share an attempt. On timeout or cancellation the controller fences late callbacks and releases attempt-owned resources before retry. An explicit retry gets a fresh deadline; once frozen, the process choice cannot change.
