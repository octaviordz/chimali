# Data Model: Error Handling & Logging

**Entity**: `ErrorUi`
Represents the user-facing mapped presentation of a technical error.
- `title: String`
- `message: String`
- `isRetryable: Boolean`
- `ctap2ErrorCode: Int?` (Optional for transport layer)

**Entity**: `LocalCrashPayload`
Represents the serialized JSON object written to the rotating log file upon an unhandled exception.
- `timestamp: Long`
- `threadName: String`
- `exceptionClass: String`
- `stackTrace: String` (Scrubbed of standard PII/Security keywords before writing)
