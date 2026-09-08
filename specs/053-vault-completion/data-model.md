# Data Model: Vault Feature Completion

**Feature**: `053-vault-completion`  
**Date**: 2026-09-07  
**Status**: Complete

## 1. Domain Entities & Payload Models

### 1.1 `VaultItem` (Stored Entity / Aggregate Projection)
```kotlin
enum class VaultType {
    PASSWORD,
    CREDIT_CARD,
    NOTE,
}

data class VaultItem(
    val id: UUID,
    val type: VaultType,
    val title: String,
    val payload: ByteArray,         // AES-256-GCM encrypted payload bytes
    val crdtState: ByteArray,       // Loro.dev CRDT binary state
    val dateCreated: String,        // ISO-8601 UTC
    val dateModified: String,       // ISO-8601 UTC
    val lastBackedUpAt: String?,    // ISO-8601 UTC or null
    val identityId: UUID,
)
```

### 1.2 In-Memory Decrypted Payloads (Zero-Memory Traceable)

#### `PasswordPayload`
```kotlin
data class PasswordPayload(
    val title: String,
    val username: CharArray,
    val password: CharArray,
    val uri: String,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        username.fill('0')
        password.fill('0')
        notes?.fill('0')
        customFields?.forEach { it.clearMemory() }
    }
}
```

#### `CreditCardPayload`
```kotlin
data class CreditCardPayload(
    val title: String,
    val cardholderName: CharArray,
    val cardNumber: CharArray,
    val expirationDate: String, // MM/YY
    val cvv: CharArray,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        cardholderName.fill('0')
        cardNumber.fill('0')
        cvv.fill('0')
        notes?.fill('0')
        customFields?.forEach { it.clearMemory() }
    }
}
```

#### `SecureNotePayload`
```kotlin
data class SecureNotePayload(
    val title: String,
    val content: CharArray,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        content.fill('0')
        customFields?.forEach { it.clearMemory() }
    }
}
```

#### `CustomField`
```kotlin
data class CustomField(
    val name: String,
    val value: CharArray,
    val isConcealed: Boolean,
) {
    fun clearMemory() {
        value.fill('0')
    }
}
```

---

## 2. Serialization Binary Schema (Payload Format)

When encrypting a payload with AES-256-GCM, the plaintext bytes before encryption are structured via a versioned binary or canonical UTF-8 JSON format:

### JSON Payload Schema (Versioned)
```json
{
  "version": 1,
  "type": "PASSWORD",
  "fields": {
    "title": "Example Bank",
    "username": "user123",
    "password": "secretPassword!",
    "uri": "https://bank.example.com",
    "notes": "Security pin in safe",
    "customFields": [
      {
        "name": "PIN",
        "value": "1234",
        "isConcealed": true
      }
    ]
  }
}
```

*Security Requirement*: After conversion to `ByteArray` and immediate encryption via `EncryptionManager.encrypt()`, the plaintext byte array is zeroed out (`plaintext.fill(0)`).

---

## 3. Database Schema Mapping (SQLDelight: `Vault.sq`)

| Table | Column | Type | Description |
|---|---|---|---|
| `vault_entry` | `id` | `TEXT PRIMARY KEY` | UUID string |
| `vault_entry` | `type` | `TEXT NOT NULL` | Enum: `PASSWORD`, `CREDIT_CARD`, `NOTE` |
| `vault_entry` | `title` | `TEXT NOT NULL` | Item title (unencrypted search/display header) |
| `vault_entry` | `encrypted_payload` | `BLOB NOT NULL` | AES-256-GCM encrypted payload (IV + ciphertext + tag) |
| `vault_entry` | `crdt_state` | `BLOB NOT NULL` | Loro doc bytes |
| `vault_entry` | `identity_id` | `TEXT NOT NULL` | Owning identity UUID |
| `vault_entry` | `date_created` | `TEXT NOT NULL` | ISO-8601 UTC timestamp |
| `vault_entry` | `date_modified` | `TEXT NOT NULL` | ISO-8601 UTC timestamp |
| `vault_entry` | `last_backed_up_at` | `TEXT` | Nullable ISO-8601 UTC timestamp |
| `label` | `id` | `TEXT PRIMARY KEY` | UUID string |
| `label` | `name` | `TEXT NOT NULL` | Label name |
| `label` | `color_hex` | `TEXT NOT NULL` | Hex color string |
| `vault_entry_label` | `entry_id` | `TEXT NOT NULL` | References `vault_entry(id)` |
| `vault_entry_label` | `label_id` | `TEXT NOT NULL` | References `label(id)` |

---

## 4. UI State & Navigation Models

### 4.1 `VaultDestinations`
```kotlin
object VaultDestinations {
    const val LIST_ROUTE = "vault/list"
    const val ENTRY_PASSWORD_ROUTE = "vault/entry/password"
    const val ENTRY_CARD_ROUTE = "vault/entry/card"
    const val ENTRY_NOTE_ROUTE = "vault/entry/note"
    const val DETAIL_PASSWORD_ROUTE = "vault/detail/password/{id}"
    const val DETAIL_CARD_ROUTE = "vault/detail/card/{id}"
    const val DETAIL_NOTE_ROUTE = "vault/detail/note/{id}"
    const val LABELS_ROUTE = "vault/labels"
}
```

### 4.2 `VaultUiState` / `VaultState`
```kotlin
data class VaultState(
    val items: List<VaultItem> = emptyList(),
    val labels: List<LabelUiModel> = emptyList(),
    val selectedLabelId: UUID? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedPasswordPayload: PasswordPayload? = null,
    val selectedCreditCardPayload: CreditCardPayload? = null,
    val selectedSecureNotePayload: SecureNotePayload? = null,
)
```
