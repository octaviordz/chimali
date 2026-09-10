# Internal Contract: Authenticator Remediation

## Capability information

The Bluetooth HID get-info path MUST obtain capability information from one focused authoritative owner. The returned
version, algorithms, transports, resident-key support, and user-verification support MUST match the existing CTAP
contract. The path MUST NOT depend on `Fido2Authenticator` or `Fido2AuthenticatorImpl`.

## Credential policy projection

`getCredentialsRequiringUserVerification()` MUST emit each stored credential whose persisted credential-protection
policy is `3`, and no other credential. `getCredentialStatistics()` MUST report the count derived from the same rule.

## Failure behavior

Database failures remain typed/logged according to existing repository conventions. The remediation MUST NOT emit
placeholder `"Not implemented"` outcomes or silently convert a valid matching credential set to an empty result.
