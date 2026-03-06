# Changelog: FIDO2 BiometricPrompt Context and Activity Fix

## Date: 2026-03-05
## Status: COMPLETED
## Feature: T145 - PIN Rate Limiting (System Delegation)

### Problem
When launching the FIDO2 registration or authentication flow, the app crashed with the error:
`java.lang.IllegalStateException: Activity context required for biometric prompt`.

This was caused by two issues:
1. **Context Wrapping**: In the Compose-based UI, `LocalContext.current` often returns a `ContextThemeWrapper` rather than the raw `Activity`. The initial implementation used a direct cast (`as? FragmentActivity`), which silently returned `null` when wrapped.
2. **Base Class Mismatch**: `MainActivity` originally extended `ComponentActivity`. However, the AndroidX `BiometricPrompt` library requires a `FragmentActivity` (or its descendant `AppCompatActivity`) to manage the biometric lifecycle. `ComponentActivity` is a base class that does not provide fragment management capabilities needed by the biometric library.

### Solutions

#### 1. Robust Context Lookup
Implemented a `findFragmentActivity()` extension function on `Context` that recursively walks up the `ContextWrapper` chain to find the underlying `FragmentActivity`. This ensures that even if Compose wraps the context in themes or other decorators, we can still retrieve the required activity host.

#### 2. Activity Base Class Migration
Migrated `MainActivity` to extend `FragmentActivity`. 
- **Note**: A trial migration to `AppCompatActivity` was attempted but caused a theme mismatch (`java.lang.IllegalStateException: You need to use a Theme.AppCompat theme`). Since the app uses Material3 (Compose), `FragmentActivity` was chosen as the optimal base class as it fulfills the `BiometricPrompt` requirements without enforcing legacy XML-based theme constraints.

### Modified Files
- `MainActivity.kt`: Changed base class to `FragmentActivity`.
- `RegistrationPromptScreen.kt`: Added `findFragmentActivity()` helper and updated `BiometricPrompt` initialization.
- `AuthenticationPromptScreen.kt`: Added `findFragmentActivity()` helper and updated `BiometricPrompt` initialization.
- `app/build.gradle.kts`: Temporarily added and then removed `androidx.appcompat` (determined unnecessary as `FragmentActivity` is transitive).

### Verification
- **Automated**: All 224 unit tests in the `:feature:fido2` module pass.
- **Manual**: Verified on-device that the BiometricPrompt now successfully launches during both Registration and Authentication flows when confirmation is clicked.
