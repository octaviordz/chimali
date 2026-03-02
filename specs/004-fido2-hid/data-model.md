# Data Model: FIDO2 Virtual Authenticator

**Date**: 2026-03-01  
**Feature**: 004-fido2-hid

## Core Entities

### PasskeyCredential

Represents a FIDO2 credential stored in the authenticator.

**Fields**:
- `credentialId: ByteArray` - Unique identifier for the credential
- `privateKey: ByteArray` - ECDSA P-256 private key (encrypted in KeyStore)
- `publicKey: ByteArray` - ECDSA P-256 public key
- `rpId: String` - Relying party identifier
- `rpName: String` - Human-readable relying party name
- `userId: ByteArray` - User identifier
- `userName: String` - Human-readable username
- `userDisplayName: String` - User display name
- `creationTime: Instant` - When credential was created
- `lastUsedTime: Instant?` - Last authentication timestamp
- `signCount: UInt` - Counter to prevent replay attacks
- `userVerificationRequired: Boolean` - Whether user verification is required
- `backupEligible: Boolean` - Whether credential can be backed up
- `backedUp: Boolean` - Whether credential has been backed up

**Validation Rules**:
- `credentialId` must be unique and non-empty
- `rpId` must be valid domain format
- `signCount` must increment on each use
- `privateKey` must be stored in Android KeyStore only

### RelyingParty

Represents a service or website that credentials are associated with.

**Fields**:
- `id: String` - Relying party identifier (domain)
- `name: String` - Human-readable name
- `iconUrl: String?` - Optional icon URL
- `credentialCount: Int` - Number of stored credentials for this RP

**Validation Rules**:
- `id` must be valid HTTPS domain format
- `name` must be non-empty

### UserConsentRecord

Represents a record of user approval for FIDO2 operations.

**Fields**:
- `operationType: UserConsentOperation` - Type of operation (Registration, Authentication, Deletion)
- `rpId: String` - Relying party identifier
- `credentialId: ByteArray?` - Specific credential if applicable
- `timestamp: Instant` - When consent was given
- `biometricUsed: Boolean` - Whether biometric verification was used
- `pinUsed: Boolean` - Whether PIN verification was used

**Enum: UserConsentOperation**
- `REGISTRATION`
- `AUTHENTICATION`
- `CREDENTIAL_DELETION`
- `ALL_CREDENTIALS_DELETION`

### BluetoothHidSession

Represents an active Bluetooth HID connection with a host device.

**Fields**:
- `sessionId: String` - Unique session identifier
- `hostDevice: BluetoothDevice` - Connected host device
- `startTime: Instant` - When session started
- `lastActivityTime: Instant` - Last message timestamp
- `protocolVersion: Fido2Version` - Negotiated FIDO2 version
- `isEncrypted: Boolean` - Whether session is encrypted
- `messageCount: UInt` - Number of messages exchanged

**Enum: Fido2Version**
- `FIDO2_0`
- `FIDO2_1`

### AuthenticationState

Represents the current state of user verification.

**Fields**:
- `isAuthenticated: Boolean` - Current authentication status
- `method: AuthenticationMethod?` - Last used authentication method
- `timestamp: Instant?` - When authentication occurred
- `expiresAt: Instant?` - When authentication expires

**Enum: AuthenticationMethod**
- `BIOMETRIC_FINGERPRINT`
- `BIOMETRIC_FACE`
- `PIN`
- `DEVICE_UNLOCK`

## Relationships

```
PasskeyCredential 1..* RelyingParty
PasskeyCredential 0..* UserConsentRecord
BluetoothHidSession 0..* PasskeyCredential
AuthenticationState 1..1 User (current session)
```

## State Transitions

### PasskeyCredential Lifecycle

1. **Created** → **Active** (after successful registration)
2. **Active** → **Used** (after successful authentication, increments signCount)
3. **Active** → **Deleted** (after user deletion)
4. **Any** → **Error** (on cryptographic failures)

### BluetoothHidSession Lifecycle

1. **Connecting** → **Connected** (after HID handshake)
2. **Connected** → **Active** (after FIDO2 negotiation)
3. **Active** → **Idle** (no activity for 30 seconds)
4. **Active** → **Disconnected** (connection lost or closed)
5. **Idle** → **Active** (new activity)

### AuthenticationState Lifecycle

1. **Unauthenticated** → **Verifying** (user verification started)
2. **Verifying** → **Authenticated** (successful verification)
3. **Verifying** → **Failed** (verification failed)
4. **Authenticated** → **Expired** (after timeout period)

## Data Storage Schema

### SQLDelight Tables

```sql
CREATE TABLE PasskeyCredential (
    id TEXT PRIMARY KEY,
    rpId TEXT NOT NULL,
    rpName TEXT NOT NULL,
    userId TEXT NOT NULL,
    userName TEXT NOT NULL,
    userDisplayName TEXT NOT NULL,
    creationTime INTEGER NOT NULL,
    lastUsedTime INTEGER,
    signCount INTEGER NOT NULL DEFAULT 0,
    userVerificationRequired INTEGER NOT NULL DEFAULT 1,
    backupEligible INTEGER NOT NULL DEFAULT 1,
    backedUp INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (rpId) REFERENCES RelyingParty(id)
);

CREATE TABLE RelyingParty (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    iconUrl TEXT,
    credentialCount INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE UserConsentRecord (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operationType TEXT NOT NULL,
    rpId TEXT NOT NULL,
    credentialId TEXT,
    timestamp INTEGER NOT NULL,
    biometricUsed INTEGER NOT NULL DEFAULT 0,
    pinUsed INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (rpId) REFERENCES RelyingParty(id)
);

CREATE TABLE BluetoothHidSession (
    sessionId TEXT PRIMARY KEY,
    hostDeviceAddress TEXT NOT NULL,
    startTime INTEGER NOT NULL,
    lastActivityTime INTEGER NOT NULL,
    protocolVersion TEXT NOT NULL,
    isEncrypted INTEGER NOT NULL DEFAULT 1,
    messageCount INTEGER NOT NULL DEFAULT 0
);
```

## Security Considerations

- All private keys stored in Android KeyStore with StrongBox when available
- Sensitive data encrypted with AES-256-GCM at rest
- Memory zeroing for all temporary credential data
- Sign count validation to prevent replay attacks
- Biometric authentication required for all credential operations
- PIN fallback for devices without biometric support
