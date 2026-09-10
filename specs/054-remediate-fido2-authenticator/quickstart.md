# Quickstart: Validate FIDO2 Authenticator Remediation

## Prerequisites

- Android development environment configured for the repository.
- A clean or intentionally understood working tree.

## Validation scenarios

1. Run the focused FIDO2 host tests for capability information and credential-policy repository behavior:
   `./gradlew.bat :feature:fido2:testAndroidHostTest`.
2. Compile the FIDO2 Android host-test target:
   `./gradlew.bat :feature:fido2:compileAndroidHostTest`.
3. Run static analysis:
   `./gradlew.bat :feature:fido2:detekt :feature:fido2:ktlintCheck`.
4. Exercise CTAP make-credential and get-assertion through the existing transport test harness. Confirm that the
   responses originate from the established handlers/use cases and no stale facade class remains in production source.
5. Store credentials with policy values that both do and do not require verification. Confirm the policy-specific
   listing and statistics count exactly match the stored data.

## Expected outcomes

- No production FIDO2 authenticator facade returns an unimplemented placeholder.
- Get-info continues to report the expected capabilities.
- Registration and assertion retain their current successful and typed-error behavior.
- Verification-required credential reports are accurate.
