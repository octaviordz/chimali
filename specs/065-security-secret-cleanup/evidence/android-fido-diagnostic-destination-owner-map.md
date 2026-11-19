# Android FIDO diagnostic destination and support-owner map

**Date:** 2026-11-17  
**Revision:** Current exploratory worktree; no frozen-candidate claim.

`Fido2Initializer.init(Context)` installs a debug-only `PrivacyScrubbingLogWriter(LogcatWriter())` and always installs `LocalCrashReportingLogWriter(AndroidLogDirectoryProvider(context))`. The Android provider derives the path from the app-private `Context.filesDir` plus the fixed `logs` child. The writer scrubs the message before appending it to the current local file; throwable details are omitted. Filesystem errors go to fixed stderr labels without paths or exception text. `AndroidPlatformLock` supplies a `ReentrantLock` for the synchronous write/rotation critical section; the common `PlatformLock.withLock` borrows the closure only until unlock in `finally` and retains no closure state.

`BouncyCastleLoader.ensureRegistered()` has one direct Kermit emission: a fixed operation label with a boolean indicating whether a provider was already present. It does not interpolate provider names, exceptions, paths or cryptographic input. The enclosing warm-up maps failures to fixed labels and omits provider exception details.

Source search found no production caller of `IosLogDirectoryProvider` or `IosPlatformLock`, and the crash writer is Android-only. Those iOS helper files are recorded as assessed no-relevant-use for the current repository call graph; this does not certify future or downstream consumers or iOS diagnostic behavior.

This is a source-to-destination owner map only. It does not claim configured Logcat/local-file runtime redaction, current/rotated file capture, fallback execution, heap absence, or iOS runtime behavior. Those remain separate T029/T034/T035 verification obligations.
