# FIDO2 Protocol Contract

**Date**: 2025-02-25  
**Version**: 1.0.0  
**Standard**: FIDO2/WebAuthn Level 2

## Protocol Overview

The FIDO2 Virtual Authenticator implements the Client to Authenticator Protocol (CTAP2) over Bluetooth HID transport layer. This contract defines the exact protocol implementation requirements.

## Transport Layer: Bluetooth HID

### HID Descriptor

```c
// HID Report Descriptor for FIDO2 Authenticator
const uint8_t fido2_hid_report_descriptor[] = {
    0x05, 0x01,        // Usage Page (Generic Desktop)
    0x09, 0x06,        // Usage (Keyboard)
    0xA1, 0x01,        // Collection (Application)
    0x85, 0x01,        // Report ID (1)
    0x05, 0xD0,        // Usage Page (FIDO Alliance)
    0x09, 0x01,        // Usage (U2F HID)
    0x15, 0x00,        // Logical Minimum (0)
    0x26, 0xFF, 0x00,  // Logical Maximum (255)
    0x75, 0x08,        // Report Size (8 bits)
    0x95, 0x40,        // Report Count (64 bytes)
    0x81, 0x02,        // Input (Data, Variable, Absolute)
    0xC0                 // End Collection
};
```

### Communication Channel

- **Report ID**: 1 (for all FIDO2 messages)
- **Maximum Packet Size**: 64 bytes
- **Protocol Version**: CTAP2.1
- **Channel Initialization**: Via HID INIT command

## CTAP2 Commands

### Command Structure

```c
struct ctap_command {
    uint8_t command;       // Command ID (0x01-0x10)
    uint8_t data_length;   // Data length (big-endian)
    uint8_t data[...];     // Command payload
};
```

### Supported Commands

| Command ID | Name | Description | Status |
|------------|------|-------------|---------|
| 0x01 | makeCredential | Create new credential | ✅ Required |
| 0x02 | getAssertion | Authenticate with existing credential | ✅ Required |
| 0x04 | getInfo | Get authenticator information | ✅ Required |
| 0x06 | clientPIN | PIN management | ❌ Out of scope |
| 0x07 | getAssertion | Get next assertion | ❌ Out of scope |
| 0x08 | getAssertion | Large blob storage | ❌ Out of scope |
| 0x0A | getAssertion | Config management | ❌ Out of scope |
| 0x0B | getAssertion | Bio enrollment | ❌ Out of scope |

## Command Specifications

### makeCredential (0x01)

Creates a new FIDO2 credential (passkey).

**Request Structure**:
```c
struct make_credential_request {
    uint8_t client_data_hash[32];     // SHA-256 hash of client data
    uint8_t rp[...];                 // Relying party information
    uint8_t user[...];                // User information
    uint8_t pub_key_cred_params[...];  // Public key algorithm parameters
    uint8_t exclude_list[...];         // Credentials to exclude
    uint8_t extensions[...];           // Extension data
    uint8_t options[...];              // Authentication options
    uint8_t pin_auth[16];             // PIN authorization (unused)
    uint8_t pin_protocol;              // PIN protocol version (unused)
};
```

**Response Structure**:
```c
struct make_credential_response {
    uint8_t fmt[...];                 // Attestation format
    uint8_t auth_data[...];            // Authenticator data
    uint8_t att_stmt[...];             // Attestation statement
};
```

### getAssertion (0x02)

Authenticates user with existing credential.

**Request Structure**:
```c
struct get_assertion_request {
    uint8_t rp_id_hash[32];           // SHA-256 hash of RP ID
    uint8_t client_data_hash[32];      // SHA-256 hash of client data
    uint8_t allow_list[...];           // Allowed credentials
    uint8_t extensions[...];           // Extension data
    uint8_t options[...];              // Authentication options
    uint8_t pin_auth[16];             // PIN authorization (unused)
    uint8_t pin_protocol;              // PIN protocol version (unused)
};
```

**Response Structure**:
```c
struct get_assertion_response {
    uint8_t credential_id[...];        // Credential ID
    uint8_t auth_data[...];            // Authenticator data
    uint8_t signature[...];             // Signature over client data
    uint8_t user[...];                 // User handle
};
```

### getInfo (0x04)

Returns authenticator capabilities and information.

**Response Structure**:
```c
struct get_info_response {
    uint8_t versions[...];             // Supported protocol versions
    uint8_t extensions[...];           // Supported extensions
    uint8_t aaguid[16];               // Authenticator AAGUID
    uint8_t options[...];              // Supported options
    uint8_t max_msg_size[2];           // Maximum message size
    uint8_t pin_uv_auth_protocols[...]; // PIN/UV protocols
    uint8_t max_credential_count_in_list[2];
    uint8_t max_credential_id_length[2];
    uint8_t transports[...];           // Supported transports
    uint8_t algorithms[...];           // Supported algorithms
};
```

## Data Structures

### Authenticator Data

```c
struct authenticator_data {
    uint8_t rp_id_hash[32];           // SHA-256 hash of RP ID
    uint8_t flags;                    // Authentication flags
    uint32_t sign_count;               // Signature counter
    uint8_t attested_credential_data[...]; // For makeCredential
    uint8_t extensions[...];           // Extension data
};
```

**Flags Bitfield**:
- Bit 0: User Present (UP)
- Bit 2: User Verified (UV)
- Bit 6: Attested Credential Data Included (AT)
- Bit 7: Extension Data Included (ED)

### Relying Party (RP)

```c
struct relying_party {
    uint8_t id[...];                  // RP ID (domain)
    uint8_t name[...];                // RP name
    uint8_t icon[...];                // Optional RP icon URL
};
```

### User

```c
struct user {
    uint8_t id[...];                  // User ID
    uint8_t name[...];                // User display name
    uint8_t display_name[...];          // User-friendly name
    uint8_t icon[...];                // Optional user icon URL
};
```

## Error Codes

| Code | Name | Description |
|------|------|-------------|
| 0x01 | INVALID_COMMAND | Command not supported |
| 0x02 | INVALID_PARAMETER | Invalid parameter in command |
| 0x03 | INVALID_LENGTH | Invalid length field |
| 0x04 | INVALID_SEQ | Invalid sequence of commands |
| 0x05 | TIMEOUT | Operation timed out |
| 0x06 | CHANNEL_BUSY | Channel already in use |
| 0x07 | LOCK_REQUIRED | Command requires channel lock |
| 0x08 | INVALID_CHANNEL | Invalid channel ID |
| 0x0A | CBOR_UNEXPECTED_TYPE | Unexpected CBOR data type |
| 0x0B | INVALID_CBOR | Invalid CBOR data |
| 0x0C | MISSING_PARAMETER | Missing required parameter |
| 0x0D | LIMIT_EXCEEDED | Exceeded size/number limit |
| 0x0E | UNSUPPORTED_EXTENSION | Unsupported extension |
| 0x0F | CREDENTIAL_EXCLUDED | Credential excluded by allowList |
| 0x10 | PROCESSING | Processing request (async) |
| 0x21 | INVALID_CREDENTIAL | Invalid credential |
| 0x22 | USER_ACTION_PENDING | User interaction required |
| 0x23 | USER_ACTION_TIMEOUT | User interaction timed out |
| 0x24 | CREDENTIAL_CURRENTLY_SELECTED | Credential already selected |
| 0x25 | USER_CONSENT_DENIED | User denied consent |
| 0x26 | NO_CREDENTIALS | No suitable credentials |
| 0x27 | USER_VERIFICATION_BLOCKED | User verification blocked |
| 0x28 | USER_VERIFICATION_TIMEOUT | User verification timed out |
| 0x29 | USER_VERIFICATION_CANCELLED | User cancelled verification |
| 0x2A | USER_ACTION_CANCELLED | User cancelled action |
| 0x2B | USER_ACTION_TIMEOUT | User action timed out |
| 0x2C | NO_OPERATIONS | No operations found |
| 0x2D | UNSUPPORTED_ALGORITHM | Unsupported algorithm |
| 0x2E | OPERATION_DENIED | Operation denied |
| 0x2F | KEY_STORE_FULL | Key storage full |
| 0x30 | NOT_BUSY | No operation in progress |
| 0x31 | NO_OPERATION_PENDING | No pending operation |
| 0x32 | UNSUPPORTED_OPTION | Unsupported option |
| 0x33 | INVALID_OPTION | Invalid option value |
| 0x34 | KEEPALIVE_CANCELLED | Keepalive cancelled |
| 0x35 | NO_CREDENTIALS | No valid credentials |
| 0x36 | USER_ACTION_TIMEOUT | User action timed out |
| 0x37 | NOT_ALLOWED | Operation not allowed |
| 0x38 | PIN_INVALID | Invalid PIN (unused) |
| 0x39 | PIN_BLOCKED | PIN blocked (unused) |
| 0x3A | PIN_AUTH_INVALID | PIN auth invalid (unused) |
| 0x3B | PIN_AUTH_BLOCKED | PIN auth blocked (unused) |
| 0x3C | PIN_NOT_SET | PIN not set (unused) |
| 0x3D | PIN_REQUIRED | PIN required (unused) |
| 0x3E | PIN_POLICY_VIOLATION | PIN policy violation (unused) |
| 0x3F | PIN_TOKEN_EXPIRED | PIN token expired (unused) |
| 0x40 | REQUEST_TOO_LARGE | Request too large |
| 0x41 | ACTION_TIMEOUT | Action timed out |
| 0x42 | UP_REQUIRED | User presence required |

## Security Requirements

### Cryptographic Algorithms

**Supported Public Key Algorithms**:
- ES256 (ECDSA with P-256 and SHA-256) - ✅ Required
- EdDSA (Ed25519) - ✅ Optional
- RS256 (RSASSA-PKCS1-v1_5 with SHA-256) - ❌ Not supported

**Supported Signature Algorithms**:
- ECDSA with P-256 - ✅ Required
- EdDSA - ✅ Optional

**Key Derivation**:
- HDK-ECDH-P256 per IETF draft-dijkhuis-cfrg-hdkeys-06
- Master Seed derivation using BIP39 mnemonic

### User Verification

**Required Methods**:
- Biometric authentication (fingerprint, face) - ✅ Required
- Device PIN fallback - ✅ Required
- User presence confirmation - ✅ Required

**Verification Levels**:
- UV (User Verified) - Biometric or PIN authentication
- UP (User Present) - Physical confirmation

### Attestation

**Supported Formats**:
- packed - ✅ Required
- fido-u2f - ✅ Required
- none - ✅ Required (privacy preserving)
- tpm - ❌ Not supported
- android-key - ✅ Optional
- android-safetynet - ✅ Optional

## Performance Requirements

### Timing Constraints

- **Command Processing**: <100ms for getInfo, <200ms for makeCredential/getAssertion
- **User Interaction**: <30 seconds for user confirmation
- **Connection Setup**: <5 seconds for Bluetooth HID connection
- **Overall Latency**: <200ms end-to-end authentication

### Size Constraints

- **Maximum Message Size**: 2048 bytes
- **Maximum Credential ID**: 1024 bytes
- **Maximum User Handle**: 255 bytes
- **Maximum RP ID**: 255 bytes

## Testing Requirements

### Conformance Testing

**Required Test Suites**:
- FIDO2 Alliance Conformance Tools
- WebAuthn.io Test Suite
- Yubico WebAuthn Demo

**Test Scenarios**:
- Registration with different algorithms
- Authentication with user verification
- Error handling and edge cases
- Cross-platform compatibility

### Integration Testing

**Test Environments**:
- Windows 10/11 with Chrome/Firefox/Edge
- macOS 12+ with Chrome/Firefox/Safari
- Linux (Ubuntu 22.04+) with Chrome/Firefox

**Test Cases**:
- Successful registration and authentication
- User denial scenarios
- Connection interruption handling
- Multiple device management

## Implementation Notes

### Android Integration

**BluetoothHidDevice API**:
- Minimum SDK: API 28 (Android 9.0)
- Required permissions: BLUETOOTH_PRIVILEGED
- Thread safety: All operations must be thread-safe

**Credential Manager API**:
- Minimum SDK: API 28 (Android 9.0)
- Integration with Android KeyStore
- Biometric authentication integration

### Memory Management

**Secure Memory Handling**:
- Use mutable byte/char arrays for sensitive data
- Zero out arrays immediately after use
- Avoid String objects for credential data
- Use constant-time cryptographic operations

**Performance Optimization**:
- Connection pooling for multiple devices
- Asynchronous operations for UI responsiveness
- Efficient CBOR encoding/decoding
- Minimal memory allocations in hot paths
