# Interface Contracts: Background Workers

## CorruptedKeyRepairWorker

A `kmpworkmanager` background task responsible for executing the HDK key derivation fallback to repair corrupted public keys.

```kotlin
interface CorruptedKeyRepairWorker {
    /**
     * Executes the HDK fallback for a batch of corrupted credentials.
     * @param credentialIds A list of IDs identifying the credentials to repair.
     */
    suspend fun doWork(credentialIds: List<String>): Result<Unit>
}
```

The worker is enqueued dynamically from the mapper/repository when a parsing exception is thrown by the `PublicKeyDecoder`. 
```kotlin
// Example Enqueue Contract
workManager.enqueue(
    WorkerBuilder("RepairKeysWorker")
        .setInputData(mapOf("credential_ids" to ids))
        .build()
)
```
