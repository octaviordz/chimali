# Feature Specification: FIDO Bluetooth Low Energy (BLE) Transport

**Feature Branch**: `064-bluetooth-transport-selection`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "Create a specification using speckit for the FIDO BLE related to active specification specs/062-bluetooth-transport-selection/spec.md. Since this is going to be part of specs/062-bluetooth-transport-selection/spec.md but an independent part create a new specification directory but using the same prefix 062. Do not create a new git branch."

**Renumbering note**: Both feature directories and the shared branch now use prefix `064`.

---

## Clarifications

### Session 2026-11-02

- Q: Which Bluetooth pairing security method should Chimali enforce when a host PC pairs over BLE? → A: Delegate pairing to the Android OS system dialog, enforcing authenticated, encrypted pairing and bonding at the GATT characteristic level with system-managed numeric comparison, without custom in-app pairing screens.
- Q: Should the FIDO BLE transport run as an Android Foreground Service while listening for connections, or operate only while Chimali is open in the foreground? → A: Use an Android Foreground Service (`connectedDevice`) with a persistent notification while the authenticator is enabled, allowing connections when backgrounded.
- Q: How should the FIDO BLE transport handle connection concurrency when multiple host devices attempt to connect? → A: Accept exactly one active BLE host connection at a time, pausing advertising while connected and resuming when disconnected.
- Q: Which FIDO protocol revision(s) should the FIDO Service Revision Bitfield characteristic advertise to Windows 11 host platforms? → A: Advertise FIDO2 only using byte 0 bit 5 (`0x20`), with no U2F revision bits. The host must select that revision through the read/write bitfield before issuing commands. CTAP capabilities remain those truthfully reported by the existing core; this bit does not select CTAP 2.1 or 2.2 features.
- Q: What should be the default inactivity timeout before BLE peripheral advertising automatically stops when no host is connected? → A: No automatic timeout; BLE advertising remains active indefinitely until the user explicitly stops the authenticator in the app or notification, matching Chimali's existing transport lifecycle.

### Session 2026-11-02 (continued)

- Q: Should the FIDO Control Point characteristic accept only reliable ATT Write Requests (with acknowledgement) from the host, or also allow Write Without Response commands? → A: Follow FIDO CTAP 2.2 §11.4: declare the standard Write property and support ATT Write Requests. Write Without Response is not a required substitute; framing sequence validation remains independently required.
- Q: What maximum duration should the FIDO BLE framing layer wait to receive all continuation frames before aborting a partially assembled command? → A: Discard an incomplete request after 3 seconds without another valid fragment, measured from the accepted initialization fragment or most recent accepted continuation fragment. This is Chimali's reassembly policy, not a server timeout mandated by FIDO; it does not limit processing or user verification after reassembly completes.
- Q: How should Chimali handle a bond state mismatch when a host attempts to connect with a retained bond record that Android has lost? → A: Handle observable pairing failures and bond-state changes, clean up the failed session, and resume advertising while enabled and permitted. Automatic repair may use supported public APIs where available; otherwise guide the user to forget the pairing on the PC and phone where present, pair again, and select Retry. Do not depend on hidden bond-removal APIs or promise seamless automatic repair.
- Q: How should the FIDO BLE transport handle keepalive notifications during long-running operations and user verification? → A: Send FIDO BLE CMD_KEEPALIVE (`0x82`) notifications carrying PROCESSING (`0x01`) or UP_NEEDED (`0x02`) at intervals no greater than 500ms while command processing or user verification is pending. Stop keepalives when processing produces a terminal response, fails, or is cancelled; delivery of that terminal response follows separately.
- Q: How should Chimali handle an incoming CMD_CANCEL (0xBE) command during an active ceremony? → A: Cancel the pending cancellable operation, stop keepalives, dismiss the on-device prompt, and complete the original request with CTAP2_ERR_KEEPALIVE_CANCEL without disconnecting the BLE link. Do not send a response to CANCEL itself.
- Q: Should Chimali require users to confirm matching pairing numbers on their phone and PC before allowing BLE authentication? → A: Require authenticated, encrypted pairing and bonding through the Android system numeric-comparison dialog; reject connections that cannot meet this requirement, without falling back to unauthenticated Just Works pairing.
- Q: When a saved Bluetooth pairing becomes invalid and automatic repair is unavailable, how should Chimali help the user reconnect? → A: Guide the user to forget the pairing on the PC and phone where present, then pair again through system dialogs; provide Retry afterward, using supported public APIs only.
- Q: Should the three-second BLE sign-in target be a usability goal or a mandatory release requirement? → A: Record physical BLE sign-in timing diagnostically without a mandatory three-second limit; performance acceptance uses the constitutional under-200ms internal-operation requirement.
- Q: How long should Chimali wait for the next piece of an incomplete BLE request before discarding it? → A: Discard the incomplete request after 3 seconds without another valid fragment, clear its owned buffers, and allow a fresh request; this timeout does not apply to user verification.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Windows 11 PC Pairing and Passkey Registration (Priority: P1)

A user wants to use Chimali as their primary FIDO2 passkey authenticator for their Windows 11 PC using Bluetooth Low Energy. They initiate Bluetooth pairing on their PC, discover Chimali broadcasting the standard FIDO service, and complete secure pairing. Later, when creating a new passkey account on a WebAuthn-enabled website in their browser, Windows routes the credential creation request to Chimali over BLE. Chimali prompts the user for verification on the phone, creates the passkey, and transmits the signed attestation back to Windows over the FIDO BLE service, completing the registration.

**Why this priority**: Passkey registration is the foundation of WebAuthn and passkey usage. Providing a reliable, standards-compliant BLE transport without relying on Classic Bluetooth HID is essential for modern Windows 11 roaming authenticator interoperability and onboarding friction reduction.

**Independent Test**: Can be fully tested on a fresh setup by pairing an Android device running Chimali with a Windows 11 PC over Bluetooth Low Energy, navigating to a WebAuthn test site (e.g., webauthn.io), clicking "Register", and verifying that Chimali receives the request, confirms verification, and completes registration over BLE with zero Classic HID connection.

**Acceptance Scenarios**:

1. **Given** Chimali is running with the FIDO BLE transport enabled, **When** a Windows 11 PC scans for Bluetooth Low Energy devices, **Then** Chimali is discovered advertising the standard FIDO Bluetooth Service (`0xFFFD`).
2. **Given** Chimali is discovered by Windows 11, **When** the user initiates pairing from Windows Settings and confirms matching numbers on the phone and PC, **Then** authenticated, encrypted BLE pairing and bonding completes through the system dialogs.
3. **Given** Chimali is paired with Windows 11 over BLE, **When** the user triggers a WebAuthn registration (`MakeCredential`) ceremony on the PC, **Then** Chimali receives the request over the FIDO BLE GATT service, prompts the user, and sends a valid signed credential response back to the PC.
4. **Given** a registration request arrives over BLE, **When** the response payload exceeds the single-packet BLE transmission unit (MTU), **Then** the transport fragments and transmits the response sequentially, and the host reassembles it without data loss or corruption.
5. **Given** a host cannot establish authenticated, encrypted pairing or the user rejects numeric comparison, **When** it attempts a CTAP2 transaction, **Then** Chimali rejects access, explains that authenticated pairing is required, and performs no credential operation; unauthenticated Just Works pairing is not accepted.
6. **Given** a secured host has discovered the standard characteristics, **When** it reads and selects revision `0x20` and enables Status notifications, **Then** FIDO2 commands may be processed; commands before revision selection or notification enablement and malformed or unsupported revision selections are rejected without credential side effects.
7. **Given** the default or a larger negotiated ATT MTU, **When** the host reads Control Point Length and exchanges fragmented requests and responses, **Then** the reported write capacity is supported and within 20–512 bytes, and packets stay within their respective write and notification limits without loss or truncation.

---

### User Story 2 — Subsequent WebAuthn Passkey Authentication (Priority: P1)

A user who has previously registered a passkey with Chimali returns to the website on their Windows 11 PC to sign in. The website triggers a WebAuthn assertion request. Windows connects to the paired Chimali authenticator over BLE, transmits the assertion request via the FIDO BLE service, and displays an authentication prompt. The user confirms their identity on the phone, and Chimali sends the cryptographic assertion response back to Windows over BLE, logging the user in.

**Why this priority**: Authentication is the high-frequency daily operation for an authenticator. It must be rapid, reliable, and completely seamless across repeated sessions.

**Independent Test**: Can be fully tested by visiting a website where a passkey was previously registered, clicking "Sign in with Passkey", verifying that Windows connects to Chimali over BLE, confirming authentication on the phone, and verifying successful login within normal user response time.

**Acceptance Scenarios**:

1. **Given** Chimali has an established passkey and is paired with a Windows 11 PC, **When** the user triggers a WebAuthn assertion (`GetAssertion`) ceremony on the PC, **Then** Windows connects to Chimali over BLE and transmits the assertion request to the FIDO BLE service.
2. **Given** an assertion request is received, **When** the user completes verification on Chimali, **Then** Chimali transmits the signed assertion response over BLE, and the PC accepts the assertion and completes login.
3. **Given** an assertion request requires fragmented transmission across multiple BLE packets, **When** the response packets are sent, **Then** they are paced and delivered in correct order according to the FIDO BLE framing specification.

---

### User Story 3 — BLE Advertising and Lifecycle Control (Priority: P2)

When the user activates the authenticator or opens the active authenticator screen, Chimali begins broadcasting BLE peripheral advertisements containing the FIDO service UUID, allowing nearby paired or pairing hosts to establish a connection. When the user explicitly stops the authenticator in the app or taps Stop on the persistent notification, BLE advertising is halted immediately to release device battery and radio resources, running without an automatic inactivity timeout to match Chimali's existing transport lifecycle.

**Why this priority**: Proper lifecycle management guarantees that radio broadcasting is active when needed for ceremonies, while preventing background battery drain and unnecessary radio emissions when the authenticator is idle.

**Independent Test**: Can be tested by starting the authenticator, observing BLE advertisement packets using a BLE scanner tool (or PC discovery), stopping the authenticator, and verifying that BLE advertising ceases immediately.

**Acceptance Scenarios**:

1. **Given** the authenticator is in an inactive state, **When** the user starts the authenticator in FIDO BLE mode, **Then** BLE peripheral advertising commences with the standard FIDO service identifier (`0xFFFD`).
2. **Given** the authenticator is actively advertising, **When** the user explicitly stops it in the app or persistent notification, **Then** BLE advertising terminates within 1 second and all GATT server resources are released cleanly.
3. **Given** the authenticator is advertising, **When** a host establishes a connection, **Then** advertising pauses and additional hosts are not accepted until that host disconnects, after which advertising resumes while enabled and permitted.
4. **Given** the authenticator is enabled, **When** the app screen closes or the app moves to the background without an explicit Stop action, **Then** the foreground service and notification remain active and host discovery and ceremonies remain available, subject to Android system lifecycle constraints.

---

### User Story 4 — Link Interruption, Range Exits, and Reconnection Recovery (Priority: P2)

During an active session or between ceremonies, a user might walk away with their phone, sleep their PC, or experience momentary radio interference. When the host reconnects or the user returns into range, Chimali cleans up any stale partial packets or aborted fragmentation buffers and accepts subsequent ceremony requests cleanly without requiring an app force-stop or phone restart.

**Why this priority**: Wireless connections are inherently prone to transient disconnects. Robust error recovery and state isolation ensure that link dropouts do not leave the authenticator in an unrecoverable "zombie" state.

**Independent Test**: Can be tested by disconnecting or disabling Bluetooth on the PC mid-idle or during connection setup, re-enabling Bluetooth, and verifying that a subsequent ceremony executes successfully without errors or leftover state.

**Acceptance Scenarios**:

1. **Given** a connected BLE host abruptly terminates the link or drops out of range, **When** the link loss is detected, **Then** Chimali tears down the connection session, discards any partial or pending reassembly buffers, and resets the transport to an available state.
2. **Given** an aborted connection has occurred, **When** the host reconnects and initiates a new ceremony, **Then** the new ceremony starts from a clean initial frame state and completes without interference from previous session data.
3. **Given** malformed, out-of-order, or invalid BLE frames are received by the service, **When** the transport parses them, **Then** it rejects them with an appropriate FIDO status error code and does not crash or corrupt the service.
4. **Given** a saved pairing is invalid and automatic repair is unavailable or fails, **When** reconnection fails, **Then** Chimali cleans up the failed session and guides the user to forget the pairing on the PC and phone where present and pair again through system dialogs, with a Retry action afterward. Retry must establish a fresh authenticated, encrypted bond before accepting CTAP2 transactions and must not require an app force-stop or phone restart.
5. **Given** a request has been only partially received, **When** 3 seconds elapse without another valid fragment, **Then** Chimali aborts that request, clears its owned reassembly buffers, reports the FIDO request-timeout error when the secured link permits, and accepts a fresh initialization frame without reconnecting. Invalid fragments must not extend this deadline; completed requests awaiting processing or user verification are not subject to it.
6. **Given** a cancellable request is awaiting user verification, **When** the host sends CANCEL, **Then** the prompt and keepalives stop, the original request receives `CTAP2_ERR_KEEPALIVE_CANCEL`, and the secured link remains usable for the next request. CANCEL itself receives no response, including when no request is pending.

---

### Edge Cases

- **Variable ATT MTU Size**: What happens if a host negotiates an MTU smaller or larger than the default?
  - Support standard MTU exchange and calculate packet boundaries using each direction's supported size. Control Point Length must truthfully report the maximum supported single-write size within 20–512 bytes; incoming writes must also respect negotiated ATT limits. Response notifications must not exceed `ATT_MTU - 3` or the platform's supported notification capacity. A large MTU must never cause Control Point Length to exceed 512 bytes.
- **Unbonded, Unencrypted, or Unauthenticated Connection Attempts**: What happens if a host without authenticated, encrypted pairing and bonding attempts to write to the FIDO Control Point?
  - Chimali requires authenticated encryption (Bluetooth LE Security Mode 1 Level 3 or stronger), exceeding the FIDO BLE minimum. Initial pairing must use system-managed numeric comparison with matching numbers confirmed on the phone and PC. Missing bonding, encryption, or authentication must deny CTAP2 access without credential side effects; unsupported or rejected numeric comparison must surface an actionable pairing failure without falling back to Just Works. Reconnection may reuse a valid authenticated bond without repeating initial pairing.
- **Concurrent Requests or Multi-Client Collisions**: What happens if multiple central devices attempt to connect or write simultaneously?
  - The transport must enforce single-active-host exclusivity. When a host establishes an active connection, peripheral advertising is paused and additional connection requests are not accepted until the current session terminates.
- **Bond State Mismatch (Stale Host Bond)**: What happens if a host PC retains its bond record but Android has lost it (e.g., following an app data clear or system reset)?
  - Observable pairing failures or bond-state changes must trigger failed-session cleanup without assuming that every mismatch produces a particular bond-state transition. Automatic repair may use supported public APIs only where available. If repair is unavailable or fails, Chimali must guide the user to forget the pairing on the PC and phone where present and pair again through system dialogs, then offer Retry. Advertising resumes while the authenticator remains enabled and Bluetooth permissions and adapter state permit it; retry must preserve the authenticated-pairing requirement and must not depend on hidden APIs or a process restart.
- **Host Cancellation (CMD_CANCEL)**: What happens if the host or user cancels a ceremony on the PC while Chimali is awaiting verification on the phone?
  - Cancel a pending cancellable operation, stop keepalives, dismiss the on-device prompt, clear owned transaction buffers, and complete the original request with `CTAP2_ERR_KEEPALIVE_CANCEL`, returning to ready state without dropping the BLE connection. CANCEL itself must never receive a response, including when no cancellable request is pending.
- **Incomplete Request Timeout**: What happens if a host stops sending fragments before a complete request has arrived?
  - After 3 seconds without another valid fragment, measured from the accepted initialization fragment or most recent accepted continuation fragment, the transport must abort reassembly, clear owned buffers, and return to ready state. Report the FIDO request-timeout error when the secured link permits; invalid fragments must not refresh the deadline. This receive-only timeout must stop once reassembly completes and must not limit command processing, user verification, or enabled advertising.
- **Bluetooth Adapter Disabled or Permissions Revoked at Runtime**: What happens if the user disables Bluetooth in system quick settings or revokes Bluetooth permissions while the authenticator is running?
  - The transport must detect the state change, transition its availability state to unavailable (`BluetoothOff` or `PermissionRequired`), notify the controlling service/UI, and release radio resources without crashing.

---

## Requirements *(mandatory)*

### Functional Requirements

Protocol authority: [FIDO CTAP 2.2 §11.4](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-fido-service). The identifiers and wire behavior below correct earlier protocol descriptions while preserving FIDO2-only scope. Authenticated numeric-comparison pairing and the 3-second receive-only timeout are explicit additional Chimali policies.

- **FR-001**: The transport MUST expose the FIDO BLE GATT Service as a Primary Service with UUID `0xFFFD`, together with the mandatory Device Information and Generic Access Profile services and their required characteristics under CTAP 2.2 §11.4.5. Device information MUST NOT introduce per-device tracking identifiers.
- **FR-002**: The GATT Service MUST declare and support the following standard characteristics:
  - **FIDO Control Point** (UUID `F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB`): Write property, supporting ATT Write Requests with the single-write capacity defined by FR-004.
  - **FIDO Status** (UUID `F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB`): Notify property with CCCD `0x2902`; notifications must be enabled before command processing.
  - **FIDO Control Point Length** (UUID `F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB`): Read property, reporting the supported maximum single-write size as a 2-byte big-endian value in the inclusive range 20–512.
  - **FIDO Service Revision Bitfield** (UUID `F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB`): Read/Write properties, advertising only FIDO2 as one byte `0x20`. The host must select that revision by writing exactly one supported bit before sending FIDO commands; unsupported or malformed selections must be rejected. U2F revision bits and the superseded U2F 1.0 Service Revision characteristic are not advertised.
- **FR-003**: The transport MUST broadcast BLE peripheral advertisements specifying the FIDO Service UUID (`0xFFFD`) to enable discovery and pairing by host operating systems, broadcasting continuously while enabled until explicitly stopped by the user via the app or notification without an automatic inactivity timeout.
- **FR-004**: The transport MUST support dynamic ATT MTU negotiation and truthfully report its supported maximum Control Point single-write size within 20–512 bytes. Received writes MUST respect that maximum and negotiated ATT limits; outgoing notifications MUST respect `ATT_MTU - 3` and the platform's supported notification capacity. Request and response fragmentation MUST use their respective supported capacities rather than an unbounded common MTU-derived value.
- **FR-005**: The transport MUST implement FIDO BLE initialization frames (command, total payload length, initial data) and continuation frames for request reassembly and response fragmentation. Continuation sequence numbers MUST start at zero and wrap to zero after `0x7f` under CTAP 2.2 §11.4.10. Invalid sizes, lengths, or sequences MUST be rejected without processing a partial CTAP2 request.
- **FR-006**: The transport MUST detect framing errors and invalid continuation sequence numbers, abort invalid transactions, and return the applicable FIDO error frame. An incomplete request MUST time out after 3 seconds without another valid fragment, measured from its accepted initialization fragment or most recent accepted continuation fragment; invalid fragments MUST NOT extend this deadline. On timeout, the transport MUST clear owned reassembly buffers, report FIDO `ERR_REQ_TIMEOUT` (`0x05`) when the secured link permits, and accept a fresh initialization frame without dropping an otherwise healthy connection. This Chimali receive-only policy MUST stop once reassembly completes and MUST NOT limit command processing, user verification, or advertising; it is separate from the FIDO specification's client timing requirements.
- **FR-007**: The transport MUST require authenticated, encrypted BLE pairing and bonding (Bluetooth LE Security Mode 1 Level 3 or stronger) before processing CTAP2 transactions, enforcing access at the GATT characteristic level. Initial pairing MUST use the Android system Bluetooth dialog for numeric comparison, with matching numbers confirmed on the phone and PC, without custom in-app pairing UI. Unauthenticated Just Works pairing MUST NOT be accepted. Hosts unable to satisfy this security policy or rejected by the user MUST receive a pairing failure and MUST NOT trigger credential operations; reconnecting hosts may reuse a valid authenticated bond.
- **FR-008**: The transport MUST route received and reassembled CTAP2 commands to Chimali's core FIDO2 message processor and transmit generated responses back to the host via FIDO Status notifications.
- **FR-009**: Outgoing FIDO Status notifications MUST preserve their required delivery order and MUST NOT be silently lost or corrupted because of concurrent requests or radio buffer overruns. Delivery failure MUST terminate the affected session with owned resources released.
- **FR-010**: The transport MUST conform to Chimali's unified `Fido2Transport` abstraction and operate within an Android Foreground Service of type `connectedDevice` with a persistent notification while enabled, allowing host PCs to discover, connect to, and complete ceremonies with Chimali when the app is backgrounded.
- **FR-011**: The transport MUST cleanly tear down active GATT connections, release peripheral advertising handles, and discard owned in-flight buffers on explicit user Stop or system termination of the transport/service. Closing or backgrounding an app screen alone MUST NOT be treated as Stop.
- **FR-012**: If the device hardware lacks BLE peripheral advertising support or Bluetooth is disabled, the transport MUST fail gracefully and report a typed unavailable status (`Unsupported` or `BluetoothOff`) without throwing uncaught exceptions.
- **FR-013**: The transport MUST enforce a single-active-host connection policy, pausing peripheral advertising while a host is connected and resuming advertising when the link disconnects.
- **FR-014**: The transport MUST handle observable pairing failures and bond-state changes during reconnection by cleaning up the failed session and resuming advertising while enabled and permitted. Automatic bond repair MAY use supported public APIs where available; hidden APIs MUST NOT be required. When automatic repair is unavailable or fails, Chimali MUST guide the user to forget the pairing on the PC and phone where present, pair again through system dialogs, and provide Retry afterward. Retry MUST require a valid authenticated, encrypted bond before accepting CTAP2 transactions, without an app force-stop or phone restart.
- **FR-015**: While an accepted command is being processed or awaiting user presence, the transport MUST emit FIDO BLE keepalive notifications (`CMD_KEEPALIVE` `0x82`) via FIDO Status at intervals no greater than 500ms, carrying PROCESSING (`0x01`) during processing or UP_NEEDED (`0x02`) while waiting for user presence. Processing ends when the operation produces its terminal response, fails, or is cancelled; keepalives MUST then stop before terminal-response delivery begins. Fragmented terminal-response delivery is governed by FR-009 rather than the keepalive interval, as required by CTAP 2.2 §11.4.
- **FR-016**: Upon receiving FIDO BLE `CMD_CANCEL` (`0xBE`) during a pending cancellable operation, the transport MUST cancel that operation, stop keepalives, dismiss active user verification UI, clear owned transaction buffers, and complete the original request with `CTAP2_ERR_KEEPALIVE_CANCEL` before returning to ready state without dropping the BLE link. The transport MUST NOT respond to CANCEL itself, including when no cancellable request is pending.

### Key Entities *(include if feature involves data)*

- **FIDO BLE Service**: The GATT server service representing the virtual security key to host clients, exposing the Control Point, Status, Control Point Length, and Service Revision attributes.
- **BLE Session**: Transient state for one connected host, including negotiated MTU, supported write and notification capacities, selected revision, notification subscription, encryption/authentication/bond status, and active transaction with its receive deadline. Disconnection invalidates the transaction and owned buffers; revision selection must be established before processing commands on a new connection.
- **FIDO BLE Frame**: A framed binary packet conforming to the FIDO BLE specification:
  - *Initialization Frame*: Contains the command identifier, total payload byte count, and the initial segment of data.
  - *Continuation Frame*: Contains a sequence index and subsequent segments of data.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user on a Windows 11 PC can pair with Chimali over Bluetooth Low Energy and successfully complete a WebAuthn registration (`MakeCredential`) ceremony on a clean setup without error.
- **SC-002**: A user can complete a WebAuthn authentication (`GetAssertion`) ceremony over BLE with an established passkey on a paired host. Real-device end-to-end sign-in timing, including time awaiting user verification, is recorded diagnostically with the tested configuration and measurement boundaries; it has no mandatory three-second limit and is not the performance acceptance gate.
- **SC-003**: For 100% of valid fragmented CTAP2 requests within the advertised message and packet limits in the defined acceptance corpus, on an authenticated stable link with successful notification delivery, request and response bytes are preserved in order without corruption. Malformed input, capacity rejection, delivery failure and link interruption are assessed against the applicable failure and recovery requirements, not counted as successful exchanges.
- **SC-004**: The transport survives 50 consecutive host connect/disconnect/reconnect cycles without crashing, leaking memory, or requiring an application process restart.
- **SC-005**: BLE peripheral advertising begins within 500 milliseconds of starting the transport, and terminates completely within 1 second of stopping the transport.
- **SC-006**: Performance acceptance MUST measure every implemented app-internal FIDO operation through a host-simulated ceremony using a synthetic relying party and user-verification test double. Each operation MUST complete in <200ms, excluding measured user-verification work and Bluetooth response handoff, as required by the constitution. Physical BLE timing recorded under SC-002 is diagnostic and does not substitute for this assessment.

---

## Assumptions

- **Host Platform Support**: Windows 11 (22H2 or newer) is the intended acceptance host, not a verified compatibility claim. Registration and assertion require physical evidence identifying the Windows build, browser, adapter/driver, and Android device/OS; mocks and general host documentation do not establish interoperability.
- **Device Hardware Capabilities**: Android API 28+ is the existing minimum but does not guarantee BLE peripheral advertising or the required security behavior. Supported devices must demonstrate these capabilities at runtime and during physical acceptance; missing capabilities must produce FR-012's typed failure. Multiple advertising sets are not a prerequisite for a single active host.
- **Core FIDO2 Reuse**: Reuse existing cryptography, credential storage, authorization, user verification, and event semantics without duplication or weakening. Current request/response interfaces are HID-coupled; a pre-existing transport-neutral message processor is not assumed. Necessary boundary adaptation belongs in the implementation plan, with Classic HID behavior preserved and verified.
- **Independent Coexistence**: This FIDO BLE transport implementation operates as an independent transport component, satisfying the dependency prerequisite defined in `specs/064-bluetooth-transport-selection/spec.md`.
- **No Classic HID Conflation**: The FIDO BLE transport operates exclusively over Bluetooth Low Energy GATT (`0xFFFD`) and does not instantiate or interact with Classic Bluetooth `BluetoothHidDevice` or L2CAP sockets.
