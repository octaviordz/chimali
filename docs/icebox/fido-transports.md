# Future Exploration: FIDO2 Transport Protocols and Scope Expansion

As the Chimali virtual authenticator stabilizes over Bluetooth Classic HID, future iterations of the project may require exploring alternative transport protocols. This document outlines the viability, technical considerations, and implementation strategies for adding alternative standards-compliant transports to the Android application.

The FIDO Alliance natively standardizes three primary local transports (USB, NFC, and BLE) and one remote/hybrid transport (caBLE). This document evaluates how an Android app can emulate these physical interfaces.

---

## 1. FIDO over NFC (Near Field Communication)

FIDO over NFC is the most robust, standardized, and straightforward alternative to Bluetooth. It relies on the ISO-14443 standard and APDU (Application Protocol Data Unit) exchanges.

### How it works on Android
*   **Implementation:** Android provides the **Host-based Card Emulation (HCE)** API (`HostApduService`). 
*   **Routing:** The application registers an ISO-14443-4 Application ID (AID) specifically reserved for FIDO U2F/CTAP2 (`A0000006472F0001`). When the user taps their phone to an NFC reader connected to a PC or another phone, the Android OS routes the raw APDU bytes directly to the background service.
*   **Processing:** The service parses the APDU command (e.g., `MakeCredential`, `GetAssertion`), passes it to the corresponding CTAP2 handler, and returns the response as an APDU payload.

### Pros
*   **Extreme Stability:** No pairing procedures, no Bluetooth MTU limits (e.g., the 64-byte limit we encountered on Windows), and no OS driver quirks (like `bthid.sys`).
*   **Native Support:** Windows, macOS, and Linux support it natively out of the box via smart card APIs (PC/SC).
*   **Easy Integration:** The existing CTAP2 handlers in Chimali can directly consume the payload once the APDU envelope is stripped.

### Cons
*   **Hardware Requirement:** Requires the host computer to have a physical NFC reader.

---

## 2. FIDO over BLE (Bluetooth Low Energy)

This is the standard used by modern wireless security keys (e.g., YubiKey Bio Series, Google Titan). 

### How it works on Android
*   **Implementation:** Instead of using the `BluetoothHidDevice` (keyboard) profile, the application uses Android's `BluetoothGattServer` API.
*   **Routing:** The app broadcasts BLE advertisements containing the official FIDO U2F Service UUID (`0xFFFD`). The host PC connects as a GATT Client.
*   **Processing:** The FIDO specification defines specific BLE Characteristics:
    *   **Control Point (Write):** The host writes CTAPHID-equivalent packets here.
    *   **Status (Notify):** The authenticator replies here.
    *   **Service Revision (Read):** Indicates CTAP2 support limits to the host.

### Pros
*   **Standards Compliant:** Fully adheres to the legal/official FIDO BLE specifications, unlike emulating a Bluetooth Keyboard (which is structurally a workaround).
*   **Larger MTU:** Bypasses strict L2CAP interrupt limits. BLE allows for MTU negotiation up to 512 bytes, enabling significantly faster transfer of large cryptographic payloads (like attestation statements).

### Cons
*   **Google Play Services Interference:** This is the most severe roadblock on Android. Google Play Services runs an internal high-priority FIDO BLE listener for the "Google Prompts" feature. It frequently hijacks the `0xFFFD` UUID, inherently rejecting or routing the connection away from custom apps, leading to extreme unreliability.

---

## 3. FIDO Hybrid Transport (caBLE / Passkeys)

*caBLE (Cloud Assisted BLE)* is the modern replacement for standard FIDO over BLE. It was created jointly by Apple, Google, and Microsoft to solve the pairing friction and Google Play Services interference associated with raw BLE.

### How it works
*   When a website asks for a passkey, a QR code is displayed on the PC.
*   The phone scans the QR code.
*   **BLE's Role:** BLE is used *only* to verify physical proximity between the phone and the PC (preventing remote relay attacks). 
*   **Data Transfer:** The actual CTAP payload is end-to-end encrypted and routed over the internet via Apple/Google/Microsoft cloud relays, bypassing Bluetooth bandwidth constraints completely.

### Considerations for Chimali
*   Implementing a fully custom remote caBLE provider is highly complex and typically restricted by the OS vendors (Apple/Google) who control the OS-level passkey UI. However, open-source implementations of the caBLE protocol (like those seen in Linux desktop authenticators) exist and could theoretically be reverse-engineered for an Android host.

---

## Conclusion & Strategic Roadmap

1. **Bluetooth Classic HID (Current Status):** Since the 62-byte MTU limit has been successfully implemented and stabilized, this remains the most universally compatible approach. It enables full "USB-like" functionality without requiring an NFC reader on the PC and entirely bypasses the Google Play Services BLE UUID conflict.
2. **NFC / HCE (Immediate Next Candidate):** If "Tap to Authenticate" physical semantics are desired, adding an `HostApduService` to Chimali is the most logical next step. It is fully supported by Android, highly reliable, and maps perfectly to the existing `Ctap2MakeCredentialHandler` and `Ctap2GetAssertionHandler`.
3. **FIDO BLE (Deprioritized):** Due to active interference from Google Play Services on modern Android builds, building a reliable standalone `BluetoothGattServer` for FIDO is not recommended for production environments.
