# JaCoCo Candidate

**Version assessed**: 0.8.15, the configured engine.

**Engine relationship**: Coverage engine and current collection/reporting basis.

**Workload evidence**: Repository tooling and feature 058 §11.144 document collection for configured host and Android device tests, including an API 35 ARM64 run. The collector captures execution data, checks class/probe compatibility and validates inventories and raw instruction/branch counters. This supports historical local eligibility for the recorded configuration, not every current revision or an independent clean rerun.

**Toolchain**: Existing configuration operates with Gradle 9.6.0, AGP 9.4.1 and Kotlin 2.3.21; this is direct project evidence. It is not comparative cost evidence.

**Disposition**: Supported historically for the required workload; current-run reproducibility unverified. Counter and class identity details are in [metrics.md](metrics.md) and [continuity.md](continuity.md).

**Sources**: R1–R3, R6–R7.
