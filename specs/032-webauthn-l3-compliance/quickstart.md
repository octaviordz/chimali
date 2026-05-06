# Quickstart

## Testing the Headless GetAssertion Path

1. **Setup**: Ensure you have an Android device paired to a Windows host. Use `webauthn.io` for testing.
2. **Register a Credential**: Complete a standard registration ceremony on `webauthn.io`.
3. **Authenticate**: Click "Authenticate" on `webauthn.io`.
4. **Verification**: 
   - The device should **not** show the "Sign in" screen. The authentication should complete headlessly and instantly.
   - You should see `GetAssertion` succeed in logcat in <200ms.
   - If the host sends a fallback `GetAssertion` immediately after, it will also complete headlessly. Only 1 prompt (or zero, if UV=NONE) should be visible overall.
