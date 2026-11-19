# Sensitive UI test fixture disposition

`VaultInputPolicyInstrumentationTest` enters only the synthetic title `synthetic-sensitive-title` to inspect Android `EditorInfo` and autofill flags. Its `@After` now attempts to replace the editor value with an empty string and removes the test composition. The source set passes `:feature:vault:ktlintCheck`; the instrumentation was not run on an Android device in this audit step.

`CredentialDisplayNameTest` renders fixed synthetic ASCII display-name strings. The test creates no credential identifiers or credential records; the Compose test rule owns the temporary rendering composition. UI execution remains unverified.

`Fido2SettingsMigrationTest` migrates only the fixed numeric setting `max_credential_count=42`, and `VaultDestinationsTest` checks routes using a fixed UUID and vault type. Their inventory records include source-specific no-relevant-secret rationale. These source assessments do not establish runtime behavior or platform storage cleanup beyond the code paths inspected.

`Fido2DeviceTestApplication` starts a minimal Koin graph containing an empty UI event bus. `VaultNavigationInstrumentationTest` navigates with a fixed UUID and fixed labels, without constructing vault content. Both have file-specific no-relevant-secret dispositions; these do not claim device execution.
