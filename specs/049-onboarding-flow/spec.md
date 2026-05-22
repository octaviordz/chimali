# Feature Specification: Onboarding Flow

**Feature Branch**: `049-onboarding-flow`

**Created**: 2026-05-22

**Status**: Draft

**Input**: User description: "Add a new onboarding process displayed by default to all new users on initial application run. The onboarding informs users about Chimali's vision and features, allows feature selection (Vault/Password Manager, Passkey Authenticator), determines the default screen based on selection, and can be retaken from user settings."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First-Run Onboarding Experience (Priority: P1)

A new user installs Chimali and launches the application for the first time. Instead of the main screen, the user is presented with an onboarding flow that introduces Chimali's vision—bridging traditional credential management with modern passwordless authentication—and highlights the application's core features. The onboarding guides the user through informational screens and concludes with a feature selection step. After completing the onboarding, the user lands on their chosen default screen and will not see the onboarding again on subsequent launches.

**Why this priority**: This is the foundational experience. Without it, new users have no guided introduction and no mechanism to personalize their default workflow. It directly shapes the first impression and initial engagement.

**Independent Test**: Can be fully tested by installing the app fresh (or clearing onboarding state) and verifying the onboarding screens appear, information is presented, and the flow can be completed to reach the main application.

**Acceptance Scenarios**:

1. **Given** a user launches Chimali for the first time (no prior onboarding completion recorded), **When** the application starts, **Then** the onboarding flow is displayed before any main application content.
2. **Given** a user is viewing the onboarding, **When** the user progresses through informational screens, **Then** each screen presents clear, concise information about Chimali's vision and key features.
3. **Given** a user has completed the onboarding, **When** the user closes and reopens the application, **Then** the onboarding is not displayed and the user is taken directly to the appropriate default screen.

---

### User Story 2 - Feature Selection and Default Screen (Priority: P1)

During the onboarding flow, the user is presented with a feature selection step where they choose which core features they want to use: Vault (Password Manager), Passkey Authenticator, or both. The application uses this selection to determine which screen is shown by default on subsequent launches.

**Why this priority**: The feature selection directly determines the user's primary workflow and the application's default navigation. This is tightly coupled with the onboarding and constitutes the core personalization mechanism.

**Independent Test**: Can be tested by completing the onboarding with each permutation of feature selection and verifying the correct default screen is displayed on subsequent launches.

**Acceptance Scenarios**:

1. **Given** a user is on the feature selection step of the onboarding, **When** the user selects only "Vault, Password Manager", **Then** the Vault screen becomes the default screen on subsequent application launches.
2. **Given** a user is on the feature selection step of the onboarding, **When** the user selects only "Passkey Authenticator", **Then** the Passkey Authenticator screen becomes the default screen on subsequent application launches.
3. **Given** a user is on the feature selection step of the onboarding, **When** the user selects both "Vault, Password Manager" and "Passkey Authenticator", **Then** the Passkey Authenticator screen becomes the default screen on the immediate next launch.
4. **Given** a user has selected both features, **When** the user navigates between Vault and Passkey Authenticator during normal usage, **Then** the application saves the last visited main screen and displays it as the default on the next application launch.
5. **Given** a user is on the feature selection step, **When** the user attempts to proceed without selecting any feature, **Then** the application prevents progression and prompts the user to select at least one feature.

---

### User Story 3 - Cross-Feature Navigation (Priority: P2)

A user who selected both core features (Vault and Passkey Authenticator) needs an intuitive way to switch between the two main screens during normal application usage. The navigation mechanism provides clear, persistent access to both features.

**Why this priority**: This enables the full dual-feature experience. Without it, users who selected both features are locked into one screen. It is secondary to the onboarding itself because users who selected only one feature do not require it.

**Independent Test**: Can be tested by completing onboarding with both features selected, then verifying navigation between Vault and Passkey Authenticator is accessible and the last-visited screen persists across restarts.

**Acceptance Scenarios**:

1. **Given** a user who selected both features is viewing the Vault screen, **When** the user uses the navigation mechanism, **Then** the user is taken to the Passkey Authenticator screen.
2. **Given** a user who selected both features is viewing the Passkey Authenticator screen, **When** the user uses the navigation mechanism, **Then** the user is taken to the Vault screen.
3. **Given** a user who selected both features navigates to the Vault screen and then closes the application, **When** the user reopens the application, **Then** the Vault screen is displayed (last-visited persistence).
4. **Given** a user who selected only one feature, **When** the user views the main screen, **Then** no cross-feature navigation element for the unselected feature is visible.

---

### User Story 4 - Re-take Onboarding from Settings (Priority: P2)

A user who has previously completed the onboarding wants to revisit it—either to review information or to change their feature selection. The user navigates to the application's settings, finds the option to re-take the onboarding, and goes through the flow again. Upon completion, the new feature selection takes effect.

**Why this priority**: This provides user control and recoverability. It is lower priority than the initial onboarding because it requires the settings screen to already exist and the onboarding flow to be functional.

**Independent Test**: Can be tested by completing the initial onboarding, navigating to settings, triggering re-take, going through the flow again with different selections, and verifying the new defaults apply.

**Acceptance Scenarios**:

1. **Given** a user has completed onboarding and is in the application settings, **When** the user locates the onboarding re-take option, **Then** the option is clearly labeled and accessible.
2. **Given** a user triggers the onboarding re-take from settings, **When** the onboarding flow begins, **Then** the full onboarding experience is displayed (same as first-run, including vision/feature screens and feature selection).
3. **Given** a user completes the re-taken onboarding with a different feature selection, **When** the onboarding finishes, **Then** the new feature selection takes effect immediately—updating the default screen and navigation accordingly.
4. **Given** a user starts the re-take onboarding but cancels or navigates back before completing, **When** the user returns to the application, **Then** the previous feature selection and settings remain unchanged.

---

### Edge Cases

- What happens if the onboarding completion state is corrupted or lost (e.g., data cleared)? The application treats it as a first-run and displays the onboarding again.
- What happens if the user's stored feature selection becomes invalid in a future application version? The application falls back to displaying the onboarding flow to re-collect the user's preference.
- What happens if the user force-closes the application mid-onboarding? On next launch, the onboarding restarts from the beginning since it was not completed.
- How does the application handle the transition when a user who previously selected one feature re-takes onboarding and selects a different single feature? The previous default screen preference is replaced entirely with the new selection.
- What happens if the user selects both features, navigates to Vault, and then re-takes the onboarding selecting only Passkey Authenticator? The last-visited state is cleared and Passkey Authenticator becomes the sole default screen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-OB-010**: The application MUST display the onboarding flow on first launch when no prior onboarding completion is recorded.
- **FR-OB-020**: The onboarding flow MUST present informational screens covering Chimali's vision (bridging traditional credential management with modern passwordless authentication) and core features.
- **FR-OB-030**: The onboarding flow MUST include a feature selection step allowing the user to choose one or both core features: Vault (Password Manager) and Passkey Authenticator.
- **FR-OB-040**: The feature selection step MUST require the user to select at least one core feature before allowing progression.
- **FR-OB-050**: Upon onboarding completion, the application MUST persist the onboarding-completed state so the onboarding is not shown on subsequent launches.
- **FR-OB-060**: When the user selects only Vault (Password Manager), the application MUST set the Vault screen as the default landing screen.
- **FR-OB-070**: When the user selects only Passkey Authenticator, the application MUST set the Passkey Authenticator screen as the default landing screen.
- **FR-OB-080**: When the user selects both core features, the application MUST set the Passkey Authenticator screen as the initial default landing screen.
- **FR-OB-090**: When the user has selected both core features, the application MUST provide a navigation mechanism to move between the Vault and Passkey Authenticator main screens.
- **FR-OB-100**: When the user has selected both core features, the application MUST persist the last-visited main screen and restore it as the default on the next application launch.
- **FR-OB-110**: The application settings MUST include an option to re-take the onboarding flow.
- **FR-OB-120**: Re-taking the onboarding MUST present the same full experience as the first-run onboarding, including informational screens and feature selection.
- **FR-OB-130**: Upon completing a re-taken onboarding, the new feature selection MUST take effect immediately, replacing any previous selection.
- **FR-OB-140**: If the user cancels or exits the re-taken onboarding without completing it, the previous feature selection and onboarding-completed state MUST remain unchanged.
- **FR-OB-150**: If the onboarding-completed state is missing or corrupted, the application MUST treat it as a first-run scenario and display the onboarding.
- **FR-OB-160**: When the user changes from dual-feature selection to single-feature selection via re-take, the last-visited screen state MUST be cleared and the new single default screen MUST apply.

### Key Entities

- **Onboarding State**: Represents whether the user has completed the onboarding. Key attributes: completion status (boolean), timestamp of last completion.
- **Feature Selection**: Represents the user's chosen feature configuration. Key attributes: vault enabled (boolean), passkey authenticator enabled (boolean).
- **Default Screen Preference**: Represents which main screen the application should display. Key attributes: selected default screen identifier, last-visited screen identifier (for dual-feature users).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of first-time users see the onboarding flow before reaching any main application content.
- **SC-002**: Users can complete the entire onboarding flow (from first screen to feature selection to landing on default screen) in under 90 seconds.
- **SC-003**: After onboarding completion, 100% of subsequent application launches bypass the onboarding and land on the correct default screen within the application's cold-start performance target.
- **SC-004**: Feature selection correctly determines the default screen in all three permutations (vault-only, passkey-only, both) with 100% accuracy.
- **SC-005**: For dual-feature users, the last-visited screen is correctly restored on 100% of application relaunches.
- **SC-006**: Users can locate and trigger the re-take onboarding option from settings within 2 taps from the main settings screen.
- **SC-007**: Re-taken onboarding correctly updates feature selection and default screen with 100% accuracy.
- **SC-008**: Cancelling a re-taken onboarding preserves the existing configuration with 100% reliability.

## Assumptions

- The application already has a settings/preferences screen where the re-take onboarding option can be added.
- The existing authentication flow (biometrics/PIN) precedes the onboarding check—users authenticate first, then see the onboarding if applicable.
- The onboarding content (vision, feature descriptions) will be provided as text and static visuals; no video or animated tutorial content is required for the initial version.
- The feature selection affects only the default landing screen and navigation availability; both Vault and Passkey Authenticator functionality remain accessible regardless of selection (e.g., through settings or secondary navigation) in the long term.
- The onboarding state and feature selection are stored locally on the device and are not synced across devices.
- The cross-feature navigation mechanism design (e.g., bottom navigation bar, tabs, drawer) will be determined during the planning phase based on existing UI patterns in the application.
