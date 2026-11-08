# Feature Specification: Bluetooth Transport Selection for FIDO2 Authenticator

**Feature Branch**: `064-bluetooth-transport-selection`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "Add dual Bluetooth transport selection for FIDO2 authenticator, allowing users to choose between Classic HID and BLE GATT HID in settings, with BLE GATT HID as the default. Both implementations are retained concurrently to support testing and transition. The setting is located under Passkey Authenticator > Bluetooth HID and may require an app restart to take effect."

---

## Clarifications

### Session 2026-11-02

- Q: When an existing Chimali installation upgrades to this feature, which Bluetooth transport should it use until the user changes the setting? → A: FIDO BLE; Chimali has no releases and backward compatibility is not required.
- Q: Which Bluetooth protocol should the BLE option use for passkey ceremonies? → A: Use the FIDO BLE service and call the option FIDO BLE.
- Q: If the selected transport cannot start on the device, should Chimali automatically switch to the other transport? → A: No; show an error and let the user choose the other transport.
- Q: Should this feature include implementing FIDO BLE if that transport is not yet available? → A: Keep implementation separate and require it before this feature is accepted.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — First-Time User: FIDO BLE Is Selected by Default (Priority: P1)

A user installs Chimali for the first time or on a fresh device. They open the app and begin using it as a FIDO2 passkey authenticator. Without any configuration, the Bluetooth transport used is FIDO BLE — the recommended and modern standard. The user does not need to make any transport decision to get started.

**Why this priority**: Eliminating configuration burden for new users directly reduces onboarding friction and positions FIDO BLE as the forward-looking default. Windows 11 compatibility for the selected FIDO BLE implementation must be demonstrated on the acceptance setup.

**Independent Test**: Can be fully tested by installing the app fresh (no prior data), performing a complete FIDO2 registration and authentication ceremony with a Windows 11 PC over Bluetooth, and confirming that it completes successfully without any user visit to the transport settings.

**Acceptance Scenarios**:

1. **Given** the app is installed fresh with no prior preferences, **When** the user opens Chimali and uses it as an authenticator, **Then** the active Bluetooth transport is FIDO BLE without any user configuration.
2. **Given** the app is installed fresh, **When** the user navigates to Settings > Passkey Authenticator > Bluetooth HID, **Then** the "FIDO BLE" option is shown as selected.
3. **Given** the FIDO BLE transport is active, **When** the user performs a full FIDO2 registration and authentication ceremony with a paired PC, **Then** both ceremonies complete successfully.

---

### User Story 2 — User Switches to Classic HID for Compatibility Testing (Priority: P2)

An advanced user or developer needs to verify that the Classic HID transport continues to work with their PC setup (e.g., for regression or compatibility testing). They navigate to Settings > Passkey Authenticator > Bluetooth HID, select "Classic HID", and receive a prompt informing them that an app restart is required for the change to take effect. After restarting the app, Classic HID is selected for the new process and becomes active when the authenticator is started successfully.

**Why this priority**: This use case preserves Classic HID for compatibility and side-by-side testing during the transport transition. It is not the primary user flow but is essential for validating the Classic HID path remains functional.

**Independent Test**: Can be fully tested by navigating to the transport setting, selecting "Classic HID", restarting the app, and completing a FIDO2 registration and authentication ceremony over Classic Bluetooth HID.

**Acceptance Scenarios**:

1. **Given** the app is running with FIDO BLE active, **When** the user selects "Classic HID" in Settings > Passkey Authenticator > Bluetooth HID, **Then** the app displays a notification that a restart is required before the change takes effect.
2. **Given** the user has selected "Classic HID" and restarted the app, **When** the app starts, **Then** Classic HID is selected for the new process; when the startup check identifies no unavailable condition, it is shown as not started until an activation attempt begins; otherwise, the applicable unavailable state is shown. It becomes active only after successful startup.
3. **Given** Classic HID is the active transport, **When** the user performs a full FIDO2 registration and authentication ceremony with a paired PC, **Then** both ceremonies complete successfully.
4. **Given** Classic HID is active and the user force-stops the app without selecting a new transport, **When** the app is restarted, **Then** Classic HID remains selected and becomes active when the authenticator is started successfully.

---

### User Story 3 — User Switches Back to FIDO BLE (Priority: P2)

A user who previously switched to Classic HID wants to return to FIDO BLE. They navigate to Settings > Passkey Authenticator > Bluetooth HID, select "FIDO BLE", are informed that an app restart is required, and after restarting, FIDO BLE is selected for the new process and becomes active when the authenticator is started successfully.

**Why this priority**: Equal in importance to User Story 2 — both directions of transport switching must work identically. This story confirms the preference toggle is fully bidirectional.

**Independent Test**: Can be fully tested starting from Classic HID active state, switching to FIDO BLE, restarting the app, and starting the authenticator to confirm FIDO BLE is active.

**Acceptance Scenarios**:

1. **Given** Classic HID is the active transport, **When** the user selects "FIDO BLE" in settings and restarts, **Then** FIDO BLE is selected for the new process and becomes active when the authenticator is started successfully.
2. **Given** the user has switched from Classic HID to FIDO BLE, **When** the app restarts, **Then** the previously selected transport preference is correctly restored from persistent storage.
3. **Given** FIDO BLE is selected and the app is restarted, **When** the user performs a FIDO2 ceremony, **Then** it completes successfully over FIDO BLE.

---

### User Story 4 — Transport Selection Survives All App Lifecycle Events (Priority: P3)

A user selects their preferred transport, then experiences an app force-stop, device reboot, and OS-initiated process death. In all cases, when Chimali is next launched, the previously chosen transport is restored exactly.

**Why this priority**: Persistence of user preference is a correctness baseline — an authenticator that silently resets its transport on each boot would be unreliable and confusing. This is lower priority because the storage mechanism is straightforward once the selection toggle exists.

**Independent Test**: Can be fully tested by setting a transport preference, forcing an app stop, rebooting the device, and confirming the correct transport is selected on the next cold start and becomes active when the authenticator starts successfully.

**Acceptance Scenarios**:

1. **Given** the user has selected "Classic HID", **When** the device is rebooted and Chimali is launched, **Then** Classic HID is selected and becomes active when the authenticator is started successfully.
2. **Given** the user has selected "FIDO BLE", **When** the app is force-stopped and relaunched, **Then** FIDO BLE is selected and becomes active when the authenticator is started successfully.
3. **Given** a registration or authentication request is waiting on the user's confirmation, **When** the user opens Transport settings from the consent prompt, changes the saved transport, returns without confirming or cancelling, and then confirms the ceremony, **Then** the request completes on the previously active transport, the new choice remains pending until process restart, and the active session is not interrupted.

---

### Edge Cases

- What happens when the device does not support the selected transport at startup (e.g., BLE hardware unavailable)?
  - The app must report unsupported capability when it is reliably detectable at startup, or report activation failure when capability cannot be determined earlier. It must leave the selected transport unavailable without automatically switching and provide an informative error that allows the user to select the alternative.
- What happens if the transport preference cannot be read after retry?
  - Keep the authenticator unavailable and do not reset or overwrite the shared preference file. Offer retry and guidance to check device storage and relaunch Chimali; if the error persists, direct the user to seek technical assistance while preserving app data.
- What happens if a Bluetooth transport session is active when the user changes the transport setting?
  - The in-flight session must be allowed to complete using the currently active transport; the new preference takes effect only after the required restart.
- What if the user changes the transport setting and then force-stops the app without restarting?
  - The saved preference must be the newly selected value; the old transport was active until the force-stop and the new transport is applied on next launch.
- What if a device firmware update removes support for a previously working transport?
  - The app must report reliably detectable unsupported capability at startup, or report the failure on the next activation attempt, and guide the user to select an alternative transport.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST provide a transport selection option under Settings > Passkey Authenticator > Bluetooth HID, listing both available transport modes.
- **FR-002**: The available transport options MUST be "Classic HID" and "FIDO BLE"; FIDO BLE MUST use the FIDO Bluetooth service for passkey ceremonies.
- **FR-003**: When no transport preference has been saved, including on existing development installations, the app MUST select "FIDO BLE" for the process by default. It becomes active when the authenticator is started successfully; before a start attempt, settings MUST show it as not started when the startup check identifies no unavailable condition, or show the applicable unavailable state when unsupported hardware, missing required permission, or disabled Bluetooth is known.
- **FR-004**: The selected Bluetooth transport preference MUST be persisted durably across app restarts, force-stops, and device reboots.
- **FR-005**: When the user changes the transport selection, the app MUST display an explicit notification that the change requires an app restart before taking effect.
- **FR-006**: The settings screen MUST clearly indicate the active Bluetooth transport, or indicate that the selected transport is not started or unavailable when no transport is active.
- **FR-007**: The pending (not-yet-applied) transport selection MUST be visually distinguishable from the currently active transport in the settings screen.
- **FR-008**: Both transport modes MUST support a complete FIDO2 passkey registration ceremony (MakeCredential) with a Windows 11 PC.
- **FR-009**: Both transport modes MUST support a complete FIDO2 passkey authentication ceremony (GetAssertion) with a Windows 11 PC.
- **FR-010**: The app MUST resolve and freeze the transport preference at process startup before any selected transport is constructed or initializes Bluetooth. Android service creation may occur while the preference is resolving, but the service MUST defer transport initialization and use until a valid choice is frozen.
- **FR-011**: At process startup, the app MUST check whether the selected transport is known to be unsupported without initializing either transport. A known unsupported selection MUST be shown as unavailable with an actionable error and an option to select the alternative for activation after restart. If support cannot be determined without activation and no unavailable condition is known, the app MUST show the selection as not started until activation is attempted. A known unavailable condition, including missing required permission or disabled Bluetooth, takes precedence and MUST be shown as unavailable with actionable recovery. Activation failure MUST also be shown as unavailable with the same recovery option. The app MUST NOT switch transports automatically.
- **FR-012**: Changing the transport setting while a Bluetooth session is active MUST NOT interrupt or corrupt the ongoing session; the change MUST only apply after a restart.

### Key Entities *(include if feature involves data)*

- **Transport Preference**: The user's chosen Bluetooth transport mode. Attributes: transport type (Classic HID or FIDO BLE) and selection timestamp. Pending status is derived by comparing the saved preference with the process choice.
- **Active Transport**: The Bluetooth transport initialized successfully for the current process. The process choice is determined at startup from the persisted preference and remains immutable until process restart; the selected transport is not active before successful startup.
- **Process Availability**: The volatile lifecycle state of the selected transport (`Loading`, `SelectedNotStarted`, `Starting`, `Ready`, or `Unavailable(reason)`). The selected transport is displayed as "Not started" prior to activation, transitions to "Starting" during activation, and becomes "Active" only upon reaching the `Ready` state.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user on a fresh install can complete a full FIDO2 registration and authentication ceremony over FIDO BLE without visiting any settings screen. Verified by: automated or manual end-to-end ceremony test on a clean install.
- **SC-002**: For each switching direction, a saved choice is selected after one full process restart and becomes active after a successful start. Existing synthetic credentials and their committed event history remain readable and unchanged by the setting change, and a registration and authentication ceremony completes with the new transport. Verified by: recording credentials and events before selection, comparing them after restart and completing both ceremonies.
- **SC-003**: Both transport modes individually complete a full FIDO2 registration ceremony and a full FIDO2 authentication ceremony against a Windows 11 paired PC. Verified by: two independent ceremony sequences, one per transport.
- **SC-004**: The selected transport preference survives a device reboot. Verified by: setting the preference, rebooting the device, confirming the selected transport on cold start, then starting the authenticator and confirming that transport becomes active.
- **SC-005**: For each transport, a controlled registration and authentication ceremony held at user confirmation completes on the original transport after a successful concurrent setting change, without disconnect or unintended credential/event mutation; the new choice remains pending until process restart. Verified by: bounded deterministic concurrency tests and the corresponding physical-device procedure.
- **SC-006**: For each switching direction, settings identifies the saved selection and the process-active transport separately before restart, announces the pending choice and restart requirement, and after restart identifies the restored selection as not started until successful activation. Verified by: UI semantics assertions and inspection before and after restart.

---

## Assumptions

- Chimali has no releases; preserving a previous default for existing installations is not required.
- This feature covers transport selection; FIDO BLE implementation is a separate feature and its functional availability is a prerequisite for accepting this feature. Both transports must be functional for the required ceremonies.
- The separate FIDO BLE transport uses the FIDO BLE service (`0xFFFD`), distinct from the Bluetooth HID Over GATT Profile (`0x1812`). Its compatibility as a roaming authenticator with the selected Windows 11 host and browser is an acceptance condition to be demonstrated.
- A settings screen already exists; this feature adds the Passkey Authenticator > Bluetooth HID subsection to it.
- The minimum supported Android version is API 28 / Android 9+. Actual Bluetooth peripheral capabilities vary by device; unsupported selected transports follow FR-011.
- An app restart is accepted as the mechanism for switching the active transport. Background hot-swapping of a live Bluetooth authenticator transport is explicitly out of scope.
- The transport preference store is independent of other credential or vault data; it does not require encryption (it is not a security-sensitive value, only a transport mode flag).
- macOS and Linux host compatibility is desirable for both transports but is not a gating acceptance criterion for this feature. Windows 11 is the target host for feature acceptance; compatibility remains unverified until the required physical ceremonies are completed.
- iOS client support is out of scope for this feature. The iOS transport layer is a separate future initiative.
