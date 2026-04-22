# Data Model Updates

## Database Layer

The `PasskeyCredential` table in `Fido2Database.sq` remains structurally identical.
We will introduce new query statements to support pagination:

```sql
getPagedCredentials:
SELECT * FROM PasskeyCredential
ORDER BY lastUsedAt DESC
LIMIT :limit OFFSET :offset;

getPagedCredentialsByRpId:
SELECT * FROM PasskeyCredential
WHERE rpId = :rpId
ORDER BY lastUsedAt DESC
LIMIT :limit OFFSET :offset;
```

## Domain Layer

No changes to the `PasskeyCredential` entity structure. 
We will introduce a pagination request model to standardize inputs:

```kotlin
data class PageRequest(
    val limit: Long,
    val offset: Long
)
```

## Data Mapping

`EntityMappers.kt` will be updated to remove the requirement for a pre-computed `java.security.PublicKey`.
Instead, it will utilize a new `PublicKeyDecoder` to transform `PasskeyCredentialEntity.publicKey`.

```kotlin
fun PasskeyCredentialEntity.toDomainModel(decoder: PublicKeyDecoder): PasskeyCredential {
    return PasskeyCredential(
        id = this.id,
        // ...
        publicKey = decoder.decode(this.publicKey, this.coseAlgorithm.toInt()),
        // ...
    )
}
```
