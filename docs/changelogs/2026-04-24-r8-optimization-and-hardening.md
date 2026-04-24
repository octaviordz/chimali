# R8 Optimization & Hardening
Date: 2026-04-24

## Summary
This update introduces a production-ready R8 configuration to minimize application size and harden the codebase against reverse engineering. By enabling R8 Full Mode and optimized resource shrinking, we achieve a leaner and more secure build.

## Changes

### Build Infrastructure
- Enabled **R8 Full Mode** via `android.r8.strictFullModeForKeepRules=true`.
- Enabled **Optimized Resource Shrinking** via `android.r8.optimizedResourceShrinking=true`.
- Introduced `shrunkDebug` build type for local verification of minification and obfuscation.
- Set `proguard-android-optimize.txt` as the default ProGuard configuration for optimized builds.

### ProGuard Keep Rules
- Purged 100+ redundant rules for Bouncy Castle, SQLCipher, Hilt (obsolete), and AndroidX.
- Added narrow, defensive keep rules for `kotlinx.serialization` named companion objects.
- Removed broad package-level wildcards to improve obfuscation depth.

### Verification
- Established `shrunkDebugAndroidTest` for running instrumentation tests against minified builds.
- Added `app/proguard-rules-test.pro` to stabilize test infrastructure in optimized environments.

## Impacts
- **APK Size**: Significant reduction in both code and resources.
- **Security**: Improved obfuscation by removing unnecessary keep rules.
- **Stability**: Verified compatibility with cryptographic and serialization layers.
