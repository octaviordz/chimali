# Data Model: FIDO BLE Transport

The BLE transport adds no persistent transport or bond schema. Android owns bond records; existing events and the parent selector preference remain authoritative. The shared credential projection now supports multiple credential IDs for one RP/user: SQLDelight migration 2 copies existing projection rows while removing the previous uniqueness constraint. This preserves earlier WebAuthn credential IDs when a new same-account credential is registered over another transport. It does not rewrite or prune append-only events. See the parent [integration handoff](../064-bluetooth-transport-selection/integration.md) for the source correction and migration evidence.

## Entities and Ownership

| Entity | Fields / ownership | Validation |
|---|---|---|
| Transport run | generation, enabled, adapter/permission availability, GATT server, Nordic advertising handle, startup owner | One run; no hardware before parent process gate; advertising only after services registered |
| BLE session | generation, host reference, MTU, write/notify capacities, bond state, authenticated access evidence, selected revision, CCCD state | One host; BOND_BONDED alone is insufficient; only 0x20 selectable |
| Request assembly | transaction ID, command, declared length, received count, expected sequence, mutable buffer, monotonic deadline | Length <= wire 65535 and supported core maxMsgSize; reject before allocation; accepted fragments only refresh deadline |
| Ceremony owner | session generation, transaction ID, cancellable job, prompt owner, processing/presence status, terminal outcome | One ceremony; terminal transition exactly once; commit point follows existing core |
| Notification writer | bounded FIFO, byte count, one in-flight operation, completion token | Capacity <=2*maxMsgSize+1024 bytes; no silent dropping; keepalive coalesced; packet <= supported notification size |
| Recovery state | reason, system-settings guidance, retry availability | No secrets/host identity in diagnostics; repair only supported APIs; no credential deletion |
| Neutral transport state | Idle, Advertising, Connecting, Connected, Error plus parent availability | Shared with Classic; domain/UI do not mutate data-layer state |

maxMsgSize is the existing core's truthful advertised limit, validated as positive and <=65535. Establish it before allocating reusable run-owned buffers. Allocation of scratch pools happens at activation, not per hot-path fragment. Request and response payloads may contain sensitive data and retain explicit mutable owners.

Write capacity is min(512, ATT_MTU-3, supported Android write capacity), with a valid minimum of 20. Reject unsupported configurations rather than reporting impossible values. Notify capacity is min(ATT_MTU-3, supported Android notification capacity); track it separately and reject a configuration too small to carry the four-byte BLE error response. Re-readable characteristic values reflect actual current support.

## State Transitions

| Trigger | Transition / required effects |
|---|---|
| Parent lazy Start | Idle -> Starting; acquire GATT then Nordic advertiser; readiness only after service/advertising success |
| Start duplicated | Share current attempt/collector; do not construct another transport |
| Secure host connection | Stop advertising; create new session generation; reject additional hosts |
| Revision selection + notifications + authenticated access | Session becomes command-ready; restored valid CCCD counts |
| Notifications disabled | Erase any partial request assembly; a later subscription cannot resume stale fragments |
| Valid initialization | Ready -> Receiving; validate bounds; start monotonic 3s receive timer |
| Valid continuation | Append within bounds; sequence modulo128; refresh deadline |
| Complete message | Stop receive timer; Receiving -> Processing or WaitingForPresence |
| Receive expiry / invalid frame | Erase partial assembly; ERROR where secured/subscribed; Ready |
| Competing request | BUSY; never overwrite the active owner |
| CANCEL while cancellable | Cancel/join owned processing and prompt; stop keepalives; erase stale queued data; original request cancellation response; Ready |
| Operation past commit point | Preserve committed event semantics; do not roll back or fabricate cancellation |
| Success / CTAP failure | End processing, stop/remove pending keepalives, then deliver one terminal response; release buffers after borrowers/notification callback finish. The 500ms processing keepalive interval does not apply during response delivery |
| Disconnect | Invalidate generation, cancel/join work, erase buffers; resume advertising only if enabled/permitted |
| Stop / permission loss / Bluetooth off / service termination | Invalidate run before async teardown; cancel/join all owners; close server and advertiser; erase retained buffers; no late callback can resume |
| Retry after recoverable failure | New activation/session generation, same frozen parent mode; no Classic fallback |

## Lifetime and Boundedness

Callbacks carry run generation, session generation and transaction identity. Stale callbacks cannot alter readiness, enqueue data, dismiss a newer prompt or release reused buffers. Never erase memory concurrently with handler or Android notification use; join or await ownership release first. Failed startup closes partially acquired resources even when no ready callback arrives.

A bounded event/admission queue rejects work before ATT acknowledgement when it cannot retain it. Queue pressure must never silently discard a valid frame. The notification byte limit covers both the in-flight packet and queued packets together (at most `2 * maxMsgSize + 1024` bytes); failure to retain a packet aborts the affected session. Each notification has a five-second completion deadline; failure or expiry aborts the session and performs owned cleanup. This is a delivery-stall policy, distinct from the parent's activation deadline and the three-second receive timeout. Keepalive scheduling cannot create an infinite queue. A healthy waiting-for-presence operation uses periodic coalesced keepalive signals, not an unbounded retry loop.
