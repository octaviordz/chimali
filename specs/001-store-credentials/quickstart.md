# Quickstart: Vault Feature

## Integration
To use the secure vault in another module, inject the `VaultRepository`.

```kotlin
// Example: Saving a password
val password = PasswordEntry(
    title = "My Account",
    username = charArrayOf('u', 's', 'e', 'r'),
    password = charArrayOf('p', 'a', 's', 's'),
    uri = "https://example.com"
)

vaultRepository.save(password)
```

## Security Best Practices
1. **Never use Strings** for sensitive payloads. Always use `CharArray` or `ByteArray`.
2. **Clear memory**: Manually zero out char arrays after use.
```kotlin
password.fill('0')
```
3. **Clipboard Management**: Use the provided `ClipboardManager` wrapper which clears entries automatically after 60 seconds.
