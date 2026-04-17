# Quickstart: Local Error Handling

To test the localized logging and crash reporting:

1. Build and run the `feature:fido2` module.
2. The logging system uses Kermit. In Debug mode, it will log to Logcat. In Release mode, it logs to a local rotating file (max 5MB) at `context.filesDir/logs/`.
3. To test a crash, explicitly trigger an unhandled exception inside a ViewModel. Verify the app navigates back or crashes silently, but the crash stack trace is written to the local file.
4. To test log scrubbing, attempt to log a string with `Logger.e { "Mnemonic is abandon abandon..." }`. Verify the resulting file output replaces the sensitive word with `[REDACTED]`.
