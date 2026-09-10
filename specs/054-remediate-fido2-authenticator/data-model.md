# Data Model: Remediate FIDO2 Authenticator

## Credential policy projection

| Field | Source | Rule |
|-------|--------|------|
| Credential identifier | Stored passkey credential | Identifies one credential for listing, deletion, and statistics. |
| Relying-party identifier | Stored passkey credential | Scopes credential listings to a relying party. |
| Credential-protection policy | `cred_protect_policy` | Value `3` means the credential requires user verification; other supported values do not enter the verification-required projection. |
| Last-used timestamp | Stored passkey credential | Used only by existing statistics and recency calculations. |

## Authenticator capability information

| Attribute | Meaning | Invariant |
|-----------|---------|-----------|
| Version | Supported authenticator protocol version | Must remain consistent with the CTAP get-info response. |
| Algorithms | Supported credential algorithms | Must reflect the established CTAP capability set. |
| Transports | Supported host transports | Must reflect supported live transport behavior. |
| Resident-key / verification support | Capability flags | Must not be inferred from a removed facade's unconditional response. |

## State changes

- Removing the facade does not change credential state.
- Listing and statistics are read-only.
- Existing CTAP registration, assertion, deletion, and reset flows retain ownership of state changes.
