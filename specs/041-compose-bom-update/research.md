# Research: Compose BOM Update

## Decision: Upgrade Compose BOM to 2026.04.00

**Rationale**: 
- The current Compose BOM (2024.12.01) generates noisy `ClassNotFoundException` warnings for `PlaceholderHardwareCanvas` and `ComposeAnimation` classes during instrumented tests on API 35. 
- The latest stable BOM is 2026.04.00, which aligns with Compose 1.11.
- Updating ensures compatibility with the latest Android SDK, brings performance improvements, and reduces the risk of large future migration leaps.

**Alternatives considered**: 
- **Filtering Logs**: We could configure Logcat or the test runner to filter out `ScanningTestLoader` warnings. Rejected because it masks the symptom rather than addressing the outdated dependency.
- **Excluding tool dependencies**: We could attempt to strip `compose-ui-tooling` from `androidTest` via custom Proguard rules. Rejected because it adds brittle build complexity. Bumping the BOM is the officially supported path.
