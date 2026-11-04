# FIDO BLE Wire and Integration Contract

Authority: [CTAP 2.2 BLE binding](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-fido-service).
Related: [spec](../spec.md), [state model](../data-model.md), [parent contract](../../064-bluetooth-transport-selection/contracts/transport-selection.md).

## GATT Surface (FR-001–004, FR-007)

Primary service 0xFFFD plus required GAP and Device Information services. Avoid duplicate Android-owned GAP service registration; verify required characteristics actually exist.

| Attribute | UUID suffix/prefix | Contract |
|---|---|---|
| Control Point | F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB | Write / ATT Write Request; validate admission before acknowledgement |
| Status | F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB | Notify; CCCD 0x2902; respect secured subscription |
| Control Point Length | F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB | Read; two-byte big-endian truthful supported write capacity 20–512 |
| Revision Bitfield | F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB | Read 0x20; Write exactly one byte 0x20 before commands |

No U2F revision bits or U2F1.0 revision characteristic. Apply encrypted-MITM access permissions to protected attributes and CCCD using public Android APIs. Pairing is system numeric comparison; never override a failed authenticated pairing with encryption-only access. Do not log pairing numbers or payloads.

Reject malformed revision selection without changing the previous valid selection. Missing initial selection or notification subscription denies command processing. Support ordinary reads and valid read offsets; reject invalid offsets, prepared/long Control Point writes and unsupported operations explicitly through appropriate ATT status. Only bounded accepted writes receive success. Write Without Response is not advertised as an alternative Control Point transport.

## Framing and Commands (FR-005–006, FR-008–009, FR-015–016)

Initialization: one command byte with high bit set, two-byte big-endian total length, then data. Continuation: sequence 0..127 with wrap, then data. Size limits and receive timer follow data-model.md. Reject short headers, oversize declarations, extra bytes, unexpected sequence and stale-session data. Do not allocate from untrusted length before validating it.

- PING 0x81 echoes its valid bounded payload.
- MSG 0x83 carries CTAP command plus CBOR request, or CTAP status plus CBOR response under CTAP section8. No HID CIDs, HID INIT or HID framing appears on BLE.
- KEEPALIVE 0x82 carries PROCESSING 0x01 or UP_NEEDED 0x02 at intervals <=500ms while the accepted command is processing or waiting for user presence. Processing ends when the operation produces its terminal response, fails, or is cancelled; stop and remove pending keepalives before starting terminal-response delivery.
- CANCEL 0xbe has zero payload and no response itself; a cancelled original MSG completes with CTAP2_ERR_KEEPALIVE_CANCEL. Valid secured CANCEL bypasses ordinary competing-request BUSY admission during reception, processing and user verification. During incomplete reception it discards the partial request; during cancellable processing it follows the original-request cancellation contract. Malformed CANCEL is rejected independently and must not cancel a different transaction.
- ERROR 0xbf carries the applicable BLE framing error: invalid command/parameter/length/sequence, timeout 0x05, or busy 0x06.

ATT errors describe access/admission/offset failures; BLE ERROR describes transport framing; CTAP statuses describe accepted command outcomes. Do not conflate them. A malformed or oversized packet must not reach credential handling.

One notification in flight with a five-second completion deadline; failure or expiry aborts the session, fences late completion and performs owned cleanup. After terminal processing ends, no keepalive deadline applies to fragmented response delivery: preserve FIFO across response fragments and do not interleave keepalives. Erase payloads only after delivery ownership ends. Cancellation removes cancelled-operation packets and fences late callbacks before the cancellation response. Disconnect/Stop suppress all old-generation responses. Receive timeout does not limit a complete request awaiting UV.

## Raw CTAP Operation Boundary

Inputs: owned mutable CTAP request bytes, transport/session/transaction context, cancellation and processing/presence reporting.
Output: owned mutable unframed CTAP status/CBOR bytes and exactly one terminal outcome.
Preserve existing UV/authorization, credential lookup, signing, event commit and error semantics. Classic and BLE each own their wire encoders. HID dispatcher CID allocation remains Classic-only; BLE never borrows the eight-CID HID request owner.

Canonical BRD/TRD IDs must be allocated/mapped for Tier1 @Req annotations before implementing this boundary; local feature FR numbers are traceability rows, not substitutes.

## Lifecycle and Parent Integration (FR-003, FR-010–014)

The parent owns immutable process choice, one gated handle, five-second snapshot and lazy activation deadlines, settings and final app composition bindings. This component exposes a lazy BLE factory; constructing the unselected BLE transport or touching radio hardware is forbidden. Service and home share the parent handle. Failures are typed Unsupported, PermissionRequired, BluetoothOff or startup/protocol failure as appropriate; they do not select Classic.

Evolve the shared transport state away from HidConnectionState without backward-compatibility shims. connect starts the selected transport; disconnect performs idempotent Stop. connectDevice remains the Classic outbound operation and returns an explicit UnsupportedOperation result in BLE mode without a radio operation. Update both implementations and all consumers in the same integration increment.

Ready requires registered services and successful advertising, not merely object construction. Once a host connects, pause advertising and reject a second host. Disconnect resumes only while enabled. Activity backgrounding retains the connectedDevice foreground service and persistent notification; explicit app/notification Stop and service termination tear down all owners. Handle permissions and notification/service prerequisites according to supported Android versions before starting.

Invalid saved bond recovery clears only transient session state and uses supported APIs. If repair is unavailable/fails, present Forget on PC and phone where present, Pair again through system dialogs, then Retry. No app-data clearing or credential deletion.

Recovery/status UI must have localized text, accessible actions/focus, TalkBack announcements, high contrast and 200% scaling. System pairing dialogs own numeric comparison.

## Acceptance Boundaries

FR-001–002: independent discovery/characteristic/revision vectors.
FR-003/010–013: lifecycle, startup budget, single host, parent gate and failure cleanup.
FR-004–006/009: MTU/bounds, modulo128 sequences, receive timeout, backpressure and callback ordering.
FR-007/014: MITM pairing rejection and guided stale-bond recovery on actual hardware.
FR-008/015–016: real handler outcomes, keepalive cadence and cancellation/commit races.
SC-001–006: quickstart.md includes physical interoperability, 50 reconnects, advertising budgets and host-simulated latency.
