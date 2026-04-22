# Interface Contracts: Repository Pagination

## CredentialRepository.kt

The repository interface must be updated to expose suspending functions (or flows) that accept pagination parameters.

```kotlin
interface CredentialRepository {
    // ... existing methods ...

    /**
     * Retrieves a paginated list of all credentials.
     * @param limit The maximum number of items to return.
     * @param offset The number of items to skip.
     * @return A list of credentials for the requested page.
     */
    suspend fun getPagedCredentials(limit: Long, offset: Long): Result<List<PasskeyCredential>>

    /**
     * Retrieves a paginated list of credentials filtered by Relying Party ID.
     */
    suspend fun getPagedCredentialsByRpId(rpId: String, limit: Long, offset: Long): Result<List<PasskeyCredential>>
}
```

## PublicKeyDecoder.kt

A new internal contract for decoding keys.

```kotlin
interface PublicKeyDecoder {
    /**
     * Decodes a stored public key string back into a java.security.PublicKey.
     * @param storedKey The text representation from the database.
     * @param coseAlgorithm The COSE algorithm identifier (e.g., -7 for ES256).
     * @return The reconstructed public key, or null if decoding fails.
     */
    fun decode(storedKey: String, coseAlgorithm: Int): java.security.PublicKey?
}
```
