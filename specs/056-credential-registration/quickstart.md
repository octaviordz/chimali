# Validation

Use Android Studio's bundled JDK and connect ASUS with USB debugging enabled.

```powershell
.\gradlew.bat :app:connectedShrunkDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.CredentialCryptoProviderTest' --console=plain
```

Expected: all three algorithm public-key round trips and signature checks pass. The test loads
the provider from the target APK; the regular `com.chimali` package and wallet are untouched.

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest ktlintCheck detekt :app:bundleRelease --console=plain
```

For external confirmation, run the normal release using Android Studio, request a synthetic
passkey at a relying party, approve on the phone, and verify registration plus a subsequent
assertion at that relying party. Report actual browser outcomes separately from provider tests.
