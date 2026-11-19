# Process transport selection address handoff

**Finding**: SEC065-FIDO-019  
**Date**: 2026-11-16  
**Classification**: CLS-FIDO-004 (paired Bluetooth host identity).

`ProcessTransportSelection.connectDevice` borrows a caller-supplied MAC address. An unresolved or invalid transport selection rejects the call before transport activation. BLE selection rejects outbound device addressing. A resolved Classic HID selection connects and forwards the same address to the active `Fido2Transport`; the selector does not construct an additional address string, persist the address, or log it. The paired-device classification now names this transfer owner.

The controller retains the active transport and connection-state collector until `disconnect`; `close` only cancels the collector. Platform `BluetoothDevice`/address copies and their lifetime are downstream transport owners and remain unverified. The full FIDO host suite currently passes 1,276 tests across 168 reports with zero failures/errors/skips, including the existing invalid/BLE outbound-selection cases.

The source and test fixtures use only synthetic MAC-shaped values. This is a source ownership map, not Android Bluetooth runtime, heap, or platform-string erasure evidence.
