# Data Model: FIDO2 HID Authenticator

## Entities

### Fido2CredentialMetadata
Represents a registered FIDO2 credential stored in the vault.

| Field | Type | Description |
|-------|------|-------------|
| credential_id | ByteArray | Unique identifier for the credential. |
| public_key | ByteArray | The COSE public key. |
| rp_id | String | The Relying Party ID (e.g., "google.com"). |
| user_handle | ByteArray | The user handle (id) provided by the RP. |
| user_name | String | Friendly user name (e.g., "john.doe"). |
| creation_date | Long | Timestamp of registration. |
| last_used_date | Long | Last time the credential was used. |
| discovery_key | ByteArray | SHA-256(rp_id) for resident key lookup. |

### BluetoothDeviceMetadata
Stores information about paired desktop devices.

| Field | Type | Description |
|-------|------|-------------|
| device_address | String | Bluetooth MAC address. |
| device_name | String | Friendly name of the paired device. |
| last_connected | Long | Timestamp of last connection. |

## State Transitions

### Authentication Flow
1. **IDLE**: Service is running, waiting for HID reports.
2. **HANDSHAKE**: CTAP2 command received, parsing options.
3. **USER_PRESENCE_CHECK**: prompt shown to user for biometric/confirmation.
4. **CREDENTIAL_RETRIEVAL**: fetching sub-keys from vault.
5. **SIGNING**: generating attestation/assertion in Rust layer.
6. **RESPONSE**: sending HID reports back to host.
