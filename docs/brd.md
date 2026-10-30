# Business Requirements Document (BRD) - Chimali

**Project Name**: Chimali
**Version**: 0.3.0
**Project Type**: Android Native Application
**Technology Stack**: Kotlin + Jetpack Compose Multiplatform

> [!NOTE]
> This document follows the **IEEE 830** standard for software requirements specifications and adheres to **modern Agile documentation** principles, maintaining stable mnemonic requirement identifiers for enhanced traceability.

---

## 1. Executive Summary
Chimali is a modern, robust Android Wallet Secure Cryptographic Application (WSCA) built on proven security standards and best practices. It is designed to bridge the gap between traditional credential management and modern passwordless authentication by leveraging Bluetooth HID capabilities to transform the Android device into a secure virtual authenticator while providing full support for Passkeys.

## 2. Project Overview
### 2.1 Mission Statement
To provide a secure, intuitive, and highly integrated authentication experience that allows users to manage their digital identity across multiple platforms using their Android device as a software virtual authenticator over Bluetooth HID.

### 2.2 Target Audience
- Privacy-conscious individuals.
- Developers and power users needing seamless authentication across OS environments.
- Organizations transitioning to passkey-based security.

### 2.3 Success Criteria
- Successful emulation of a Bluetooth HID device on Windows, macOS, and Linux.
- Seamless creation and usage of Passkeys via Android Credential Manager.
- High user satisfaction with the Jetpack Compose-based modern UI.

## 3. Business Goals & Objectives
1.  **Security Leadership**: Implement industry-standard AES-256 protection and supported post-quantum signature algorithms. Use Android Keystore for persisted-secret protection where the configured platform/provider supports it, preferring StrongBox when available; credential signing keys in the current local profile are software-derived and are not hardware-backed.
2.  **Cross-Platform Utility**: Allow users to authenticate on desktop systems without requiring specialized hardware keys.
3.  **Modern UX**: Deliver a premium user experience through fluid animations, intuitive interactions, and a Material Design 3 (M3) design system.
4.  **Future-Proofing**: Full support for FIDO2 and WebAuthn via Passkeys.

## 4. Functional Requirements
### 4.1 Core Credential Management
- **FR-VAULT-010**: Securely store and organize passwords, notes, and credit cards.
- **FR-FLOW-010**: Auto-fill credentials in Android apps and mobile browsers.
- **FR-VAULT-020**: Secure search and categorization of accounts.
    - Implement on-device AI for automated categorization (e.g., via ML Kit or Gemini Nano) for privacy-first organization.
    - Fallback to manual/rule-based categorization if on-device AI is not supported by the hardware.
- **FR-VAULT-030**: Smart Contextualization via Geolocation.
    - Surface relevant accounts based on the user's current location (e.g., prioritize "Work" accounts when at the office).
    - All location processing must be performed locally (on-device geofencing) to maintain user privacy.

### 4.2 Virtual Authenticator & Bluetooth HID
- **FR-HID-010**: Act as a FIDO2 Virtual Authenticator via `BluetoothHidDevice`.
- **FR-HID-020**: Support pairing and connection management for multiple desktop devices.
- **FR-HID-030**: Trigger authentication "confirmations" on the phone to release credentials to the host.

### 4.3 Passkey Support
- **FR-PASS-010**: Create, store, and manage Passkeys using the Android Credential Manager.
- **FR-PASS-020**: Support cross-device sign-in (scanning a QR code from another device to use a Passkey on Android).

### 4.4 Authentication & Security
- **FR-AUTH-010**: Mandatory Authentication using Biometrics (Fingerprint/Face) or Device PIN, defaulting to the user's device settings.
- **FR-AUTH-020**: Automatic lock on app backgrounding or device inactivity.
- **FR-AUTH-030**: Local-first storage with a secure backup mechanism utilizing secret sharing (e.g., Shamir's Secret Sharing). The backup will be based on a **Master Seed**, ensuring that all credentials can be restored from a single recovery point. See [Credential ID Recovery Analysis](research/credential_id_recovery_analysis.md) for details on recovery consistency and deterministic metadata requirements.

### 4.5 Human Interface & Accessibility
- **FR-UI-010: Password Legibility and Confusion Prevention**: Ensure passwords are displayed using high-legibility fonts (e.g., monospaced) that clearly distinguish ambiguous characters (e.g., 'O' vs '0', 'I' vs 'l' vs '1'). Implement colorblind-friendly indicators or semantic highlighting to differentiate between character types (uppercase, lowercase, digits, symbols) to reduce visual confusion.
- **FR-UI-011: High-Contrast and Dynamic Scaling Support**: The application must support high-contrast modes meeting WCAG AAA color contrast ratios and dynamic text scaling (up to 200%) to accommodate low-vision users. *(Note: Full implementation deferred to a future dedicated UI accessibility phase)*. Technical verification criteria and evaluation procedures are specified in [TRD](trd.md) §9.5 and `.agents/rules/specify-rules.md` §1 under the Constitution Principle V verifiable acceptance policy (Constitution 5.0.0).

### 4.6 Passkey User Experience (UX) Alignment
- **FR-UI-020: Standardized Passkey Iconography**: The application shall implement the official FIDO Passkey Icon across all passkey-related screens (registration, authentication, and management) to ensure immediate user recognition and trust, adhering to the FIDO Alliance Design Guidelines.
- **FR-UI-030: Visual Relying Party Identification**: To enhance trust and reduce phishing risks, the application shall display the Relying Party's brand icon or favicon durante registration and authentication prompts.
- **FR-UI-040: Contextual Authentication Messaging**: The application shall clearly distinguish between local device authentication and remote authentication (acting as a FIDO2 HID security key for another device) through explicit header labeling or contextual badges to provide clear user context.
- **FR-UI-050: Global Action Button Consistency**: To ensure a premium, unified brand experience, all primary and secondary action buttons across the application (FIDO2 prompts, Vault details, and Authenticator Home) shall utilize a standardized Material 3-idiomatic sizing and shape (using content padding and large corner radii) to provide high-density, accessible touch targets.

### 4.7 Validation & Interoperability Requirements
- **FR-VAL-010: Multi-Device Interoperability**: The application must be verified to work correctly across a diverse range of Android device manufacturers (e.g., Samsung, Pixel) and OS versions (API 28+) to ensure hardware-level Bluetooth HID stack compatibility.
- **FR-VAL-020: FIDO2 Protocol Compliance**: The implementation must be validated using official FIDO Alliance conformance tools and key interoperability platforms (e.g., webauthn.io, passkeys.dev) to ensure strict adherence to FIDO2.1/CTAP2.1 specifications.
- **FR-VAL-030: Performance Verification**: The application MUST provide a host-simulated FIDO2 ceremony using a synthetic relying party and user-verification test double to assess all app-internal FIDO operations against the <200ms target in NFR-PERF-030. The measurement starts at simulated request dispatch and records independent response verification, while each internal operation is reported separately. Human verification waits and physical transport latency are outside the acceptance workload. Physical-device measurements may be diagnostic only.

### 4.8 Documentation Requirements
- **FR-DOC-010: User Guidance**: The application shall provide comprehensive user-facing documentation for Bluetooth pairing, passkey registration, and troubleshooting.

### 4.9 Local Credential Derivation
- **FR-HID-024 (superseded)**: The prior remote key-derivation/KEM requirement is retired for the current local credential profile. It does not authorize remote provisioning or issuer flows. See the separate [OpenID4VCI investigation backlog](backlog/openid4vci-hdk-issuance-investigation.md).
- **NFR-SEC-041: Current Local Credential Profile**: For fresh installations, credential recovery MUST use the full 64-byte BIP39 seed to derive the versioned 32-byte wallet PRK with RFC 5869 HKDF-SHA256. The current P-256 profile MUST bind the complete decoded credential identifier and adapt the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00`, substituting a local RFC 5869 credential secret for the draft's per-issuance X25519 shared secret. It MUST use the profile's documented deterministic software parent. This is a local custom protocol, not the draft's issuance flow or an interoperability claim. Ed25519 and ML-DSA MUST use separate algorithm-specific seed labels. Credential metadata MUST retain the supported algorithm and exact profile identifier needed for reconstruction. Unsupported pairs MUST fail; private child keys/seeds MUST NOT be persisted. The signing parent is software-derived and is not represented as non-exportable hardware key material. A mnemonic alone does not restore missing credential metadata. This feature is a clean-install breaking change and makes no OpenID4VCI or issuer-assurance claim.

### 4.10 Onboarding Flow
- **FR-OB-010: First-Run Onboarding**: The application MUST display an onboarding flow on first launch when no prior onboarding completion is recorded, before presenting any main application content.
- **FR-OB-020: Vision & Feature Introduction**: The onboarding flow MUST present informational screens covering Chimali's vision (bridging traditional credential management with modern passwordless authentication) and core features.
- **FR-OB-030: Feature Selection**: The onboarding flow MUST include a feature selection step allowing the user to choose one or both core features: Vault (Password Manager) and Passkey Authenticator. The user MUST select at least one feature.
- **FR-OB-040: Default Screen — Vault Only**: When the user selects only Vault (Password Manager), the Vault screen MUST become the default landing screen.
- **FR-OB-050: Default Screen — Passkey Only**: When the user selects only Passkey Authenticator, the Passkey Authenticator screen MUST become the default landing screen.
- **FR-OB-060: Default Screen — Both Features**: When the user selects both core features, the Passkey Authenticator screen MUST be the initial default landing screen.
- **FR-OB-070: Cross-Feature Navigation**: When the user has selected both core features, the application MUST provide a navigation mechanism to move between the Vault and Passkey Authenticator main screens.
- **FR-OB-080: Last-Visited Persistence**: When the user has selected both core features, the application MUST persist the last-visited main screen and restore it as the default on the next application launch.
- **FR-OB-090: Onboarding Completion Persistence**: Upon onboarding completion, the application MUST persist the onboarding-completed state so the onboarding is not shown on subsequent launches.
- **FR-OB-100: Re-take Onboarding from Settings**: The application settings MUST include an option to re-take the onboarding flow, presenting the same full experience as the first-run onboarding.
- **FR-OB-110: Re-take Applies Immediately**: Upon completing a re-taken onboarding, the new feature selection MUST take effect immediately, replacing any previous selection.
- **FR-OB-120: Cancel Re-take Preserves State**: If the user cancels or exits the re-taken onboarding without completing it, the previous feature selection and onboarding-completed state MUST remain unchanged.

## 5. Non-Functional Requirements
### 5.1 Security
- **NFR-SEC-010**: All sensitive data must be encrypted. The application MUST follow a **Multi-Mode Symmetric Encryption Strategy**: **AES-256-GCM** for general payloads (files, credential blobs) and encrypted metadata values. Hardware acceleration or offloading MUST NOT be assumed unless verified for the actual device/provider path. Exact-match searchable metadata MUST use deterministic keyed lookup tokens (e.g., HMAC blind indexes). Partial-text search is permitted ONLY via explicitly classified SQLCipher-protected display fields. Key wrapping MUST use platform-backed AES-GCM/AEAD with unique nonces and associated data. Bouncy Castle is retained for the current local profile and ML-DSA operations; AES-SIV is not a current requirement. The application SHOULD support Post-Quantum digital signature schemes (e.g., ML-DSA-65) for FIDO2 attestation and assertion signing, as specified by NFR-SEC-041.
- **NFR-SEC-020**: Keys used to protect persisted secrets MUST use Android KeyStore where the current platform integration supports the operation; StrongBox MAY be preferred when available. The software-derived credential signing parent and operation keys defined by NFR-SEC-041 are not Android KeyStore keys and MUST NOT be described as non-exportable.
- **NFR-SEC-030**: Sensitive credentials and derivation material MUST NOT be persisted in plaintext. Decrypted inputs and derived signing material may exist transiently in volatile memory only under the mutable ownership and cleanup controls in the constitution; application-owned mutable buffers MUST be erased on terminal paths.
- **NFR-SEC-040: Master Key Management**: Use a BIP39 recovery mnemonic as the root of a local credential architecture, protected at rest with approved authenticated encryption. Apply mutable secret ownership, authorized signing, and explicit recovery metadata. The current derivation profile and algorithm requirements are specified by NFR-SEC-041.

The former NFR-SEC-040 derivation text mandating `draft-dijkhuis-cfrg-hdkeys-06`, HDK `DeriveSalt`, and a shared classical/PQ hierarchy is superseded by this amendment. Historical references are retained only as history.

- **NFR-SEC-050: System Clipboard Protection**: Sensitive credential data copied to the system clipboard must be explicitly cleared within 60 seconds of the copy action.

Classified data fields, memory ownership rules, and audited platform UI text boundaries are governed by the canonical registers [Data Classification Register](security/data-classification.md) and [Platform Text Boundary Register](security/platform-text-boundaries.md), synchronized with [TRD](trd.md) §5.1 and `.agents/rules/specify-rules.md` §5.5 under the Constitution Principle V verifiable acceptance policy (Constitution 5.0.0). Historical security review scopes (2026-09-09 Vault remediation and 2026-10-14..16 feature-055 local key derivation) retain their original dated scopes and commit fingerprints without asserting current-source certification.

### 5.2 Performance & Reliability
- **NFR-PERF-010: Startup Performance** (Android Vitals Targets):
    - **Cold Start**: < 2 seconds (Time to initial display).
    - **Warm Start**: < 1 second.
    - **Hot Start**: < 500 milliseconds.
- **NFR-PERF-020: Rendering Smoothness**:
    - Maintain a consistent **60 FPS** (16.6ms per frame) during UI interactions.
    - Zero "Frozen Frames" (render time > 700ms) and < 1% "Slow Frames" (render time > 16ms).
    - Minimum verification journeys are designated as vault list scrolling and password detail inspection, governed by the measurement-contract requirements in [TRD](trd.md) §9.6 and `.agents/rules/specify-rules.md` §1 under Constitution Principle V (Constitution 5.0.0). General UI rendering smoothness obligations across the entire application remain in full effect.
- **NFR-PERF-030: Simulated and Internal FIDO Operation Latency**:
    - FIDO2 latency acceptance MUST use the reproducible host-simulated ceremony defined by FR-VAL-030 and report each app-internal operation against the strict target below. The former physical Bluetooth RP-to-verified-response <1,000ms acceptance target is retired; physical-device results are diagnostic and do not gate compliance.
    - All app-internal FIDO operation processing MUST be **< 200ms**, excluding user-verification wait and Bluetooth response handoff. The internal interval includes operation-specific lookup, derivation, signing, and response construction where applicable.
- **NFR-PERF-040: Resource Optimization**:
    - Zero memory leaks detected via Profiler or LeakCanary.
    - "Excessive Wake Locks" must remain below 0.1% to minimize battery impact.
- **NFR-PERF-050: Performance Monitoring and Instrumentation**: Provide a local report for the host-simulated ceremony and each internal FIDO operation, with raw durations, verified outcome, workload and environment. Physical HID acceptance may be reported diagnostically. Measurements remain local and must not include credential secrets or user-identifying data.
- **NFR-PERF-060: App Binary & Tiered Memory Budgets**:
    - **App Binary Budget**: Release AAB base module MUST NOT exceed 50 MB; feature size delta MUST NOT exceed +500 KB without documented architecture approval. R8 minification (`isMinifyEnabled`) and resource shrinking (`isShrinkResources`) must be enabled and verified on release/staging builds.
    - **Tiered Memory Budgets**: Dynamic adaptation based on device RAM tier:
        - Standard devices (4–6 GB RAM): Baseline < 150 MB | Hard peak limit 250 MB.
        - High-end devices (≥ 8 GB RAM): Baseline < 250 MB | Hard peak limit 400 MB.
    - **Memory Pressure Response**: Custom caches, image loaders, and in-memory managers must implement `ComponentCallbacks2.onTrimMemory()` to release 100% of non-essential in-memory caches upon receiving critical, background, or moderate memory signals. Dedicated unit tests must verify cache flushing on memory trim.
- **NFR-PERF-070: Vault Scalability**:
    - Support 10,000+ vault items with < 2% latency regression compared to baseline, measured via Jetpack Macrobenchmark.

### 5.3 Maintainability
- **NFR-ARCH-010: Architecture**: Implementation of Clean Architecture with Unidirectional Data Flow (UDF) using the **MVI (Model-View-Intent)** pattern for predictable state management.
- **NFR-ARCH-020: Dependency Injection**: Use **Koin** (with Koin Compiler) for standardized, compile-time safe dependency management across Kotlin Multiplatform targets.
- **NFR-ARCH-030: Modularization**: Adoption of a multi-module project structure (Feature-by-module) to ensure separation of concerns and optimized build performance.
- **NFR-ARCH-040: Static Analysis**: Use **Detekt** and **Ktlint** (open-source) to enforce coding standards and detect architectural regressions automatically.
- **NFR-ARCH-050: Module Criticality Classification (Tier 1 & Tier 2)**:
    - **Tier 1 (Critical)**: Modules where a defect can directly compromise security, cryptographic operations, data integrity, or authentication correctness (`:core:security`, `:core:domain`, `:core:data`, `:feature:fido2`, `:feature:authenticator`). Requires DO-178B-level traceability (`@Req`), at least 60% JaCoCo instruction coverage and at least 60% branch coverage where branches exist, and bounded, fail-safe execution. Each module is gated independently.
    - **Tier 2 (Feature & Infrastructure)**: All other modules (`:core:bluetooth`, `:core:crdt`, `:core:database`, `:core:common`, `:core:ui`, `:feature:vault`, `:feature:editor`, `:feature:settings`, `:feature:onboarding`, `:app`). Requires module-level `README.md` requirement mapping and baseline coverage (>= 20%).
    - The aggregate across all production modules MUST meet at least 50% JaCoCo instruction coverage. A complete coverage run for the proposed integration tree is required for integration, and changed Tier 1 modules MUST NOT regress in instruction or applicable branch coverage against the accepted integration-target baseline identified at assessment time. If the target advances, the original comparison remains valid when the proposed integration tree is unchanged; a changed tree requires renewed verification. An older comparison does not establish nonregression against a newer baseline. An authorized reviewer MUST decide acceptance using accessible, trusted evidence; hosted enforcement remains subject to the existing waiver. Approximately 65% Tier 1 branch coverage is a remediation goal, not a binding floor. Coverage floors are decided from raw covered and missed instruction/branch counts, not rounded display percentages.
- **NFR-ARCH-060: Event Sourcing & Perpetual History**:
    - Core aggregates (`VaultEntry`, `PasskeyCredential`) must utilize Event Sourcing with an append-only event log (no historical pruning), deterministic `Decider` fold reconstruction, temporal query support (`asOf`), snapshot hydration (every 20 events with fallback to full fold), and strictly additive schema evolution.
- **Documentation**: Comprehensive API documentation and user troubleshooting guides are documented as part of the post-release stabilization phase.
- **FR-DOC-010**: User guides must be available as localized Markdown files within the application's help system.

## 6. User Interface & Experience (UI/UX)
- **Design System**: Material Design 3 (M3).
- **Theming**: Dynamic Colors support.
- **Interactions**: Smooth transitions between screens using Compose Navigation.
- **Accessibility**: Support for screen readers and high-contrast modes.

## 7. Technical Constraints
- **Platform**: Android Only (initial release).
- **Minimum SDK**: Android 9.0 (API 28) for `BluetoothHidDevice` support.
- **Language**: Primary language is Kotlin (100% for UI/Android layers). Languages that produce native code (e.g., Rust) are allowed under special cases (e.g., core cryptography, shared low-level logic).
- **UI Framework**: Jetpack Compose Multiplatform.
- **Hardware Requirement**: Device must support Bluetooth HID Device Profile.
- **AI Constraints**: Optional on-device AI features require compatible hardware (e.g., AICore/Gemini Nano support) or specialized ML Kit models.

## 8. Inspiration & References
- **wiokey-android**: Implementation of virtual HID authenticator logic (to be ported from Java to Kotlin).
- **Allthenticate**: [Play Store Link](https://play.google.com/store/apps/details?id=net.allthenticate.sda) (Inspiration for desktop/mobile integration).
- **Android Bluetooth HID API**: [BluetoothHidDevice Reference](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice).
- **Credential Manager**: Google's modern API for Passkeys and Passwords.

### 8.1 FIDO2 / WebAuthn Testing Tools
- **WebAuthn.io**: [Testing Site](https://webauthn.io) (Duo Labs playground).
- **Yubico Demo**: [WebAuthn Test](https://demo.yubico.com/webauthn-technical) (Hardware-focused testing).
- **Passkeys.dev**: [Resource Hub](https://passkeys.dev/demo/) (Modern passkey testing demos).
- **FIDO Conformance Tools**: [FIDO Alliance](https://fidoalliance.org/certification/conformance-tools/) (Official validation).

## 9. Development & Testing Methodology
- **Test-Driven Development (TDD)**: Implementation should follow TDD principles where feasible to ensure high code quality and reliability.
- **Integration Testing**: Integration verification MUST cover distinct Bluetooth relying party (`FR-HID-010`) and Android Credential Manager (`FR-PASS-010`) entry-path journeys across registration, encrypted persistence, restart/reconstruction, and authentication/retrieval, including the 12 failure categories specified in [TRD](trd.md) §9.4 and `.agents/rules/specify-rules.md` §1. Verification evidence must strictly differentiate host/component test double substitutes from physical device and transport execution, in alignment with the Constitution Principle V verifiable acceptance policy (Constitution 5.0.0).
- **Unit Testing**: Platform-neutral Kotlin behavior should be covered in KMP `commonTest` with `kotlin.test`, using Fake test doubles by default and mocking frameworks only for justified platform or interaction-focused cases.


