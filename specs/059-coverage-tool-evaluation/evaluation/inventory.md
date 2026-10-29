# Current Workload Inventory

- Revision: `eebc391bbe1956a325a95bcc6b2e401607e72ce7` (checked-out source revision recorded for the evaluation; no comparative test execution used this revision).
- Gradle 9.6.0; AGP 9.4.1; Kotlin 2.3.21; JVM target 17; policy runtime/compiler Java 21; JaCoCo 0.8.15. Versions from `gradle/libs.versions.toml`, wrapper and `tools/coverage/policy.json`.
- Production source sets: commonMain, androidMain and main; iOS native and macrobenchmark excluded.
- 14 configured production modules in `tools/coverage/policy.json` (14 keys in `production_scope.variants`); use policy mappings as authoritative for per-module variant and compilation.
- Variants/compilations include androidMain, release and shrunkDebug. App device scope uses shrunkDebug.
- Required device modules: `:feature:fido2`, `:core:common`, `:feature:settings`; `:app` device tests optional.
- Device requirement: API 35+, approved arm64-v8a or x86_64 ABI, online and authorized device.
- Host unit tests required. Policy includes explicit tasks per module and source-set coverage obligations.
- Coverage policy: Tier 1 instruction and applicable branch floors 60%; repository instruction floor 50%; applicable Tier 2 instruction floor 20%; branch improvement goal 65%. These are fixed comparison constraints.
- Measurement fingerprint includes metric normalization, inventory, workloads, toolchain, harness and device identity; inspect current policy for full fields.
- No experimental comparison was run, so no new revision-specific run identity or measurements exist. This document describes the inspected checkout and policy; refresh before any future experiment.

**Sources**: `tools/coverage/policy.json`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, app/module Gradle files. Evidence R1.
