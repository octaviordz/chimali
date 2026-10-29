# Workload Eligibility Matrix

| Candidate/configuration | Host tests | Required Android device tests | Variants/shrinking | Exact toolchain | Overall |
|---|---|---|---|---|---|
| JaCoCo 0.8.15 current collector | Supported historically by configured local run | Supported historically by recorded API 35 ARM64 run | Supported in existing recorded setup including shrunkDebug app scope | Supported by actual configured project history, though no fresh run here | Historically complete; current fresh confirmation unverified |
| Kover 0.9.11 embedded engine | Supported in documented JVM/KMP host scope | Unsupported by Gradle plugin documentation | Android variant support documented; exact project/shrunkDebug parity unverified | Exact AGP 9.4.1/Kotlin 2.3.21/KMP combination unverified | Limited host-side; not eligible as complete replacement |
| Kover 0.9.11 JaCoCo backend | Supported in documented scope, with feature limitations warning | Unsupported by same Gradle plugin limitation | Variant support documented; project-specific parity unverified | Exact combination unverified | Front-end option over current engine; not complete replacement |

No third candidate qualified within bounded search (see `third-candidate.md`). Kover configurations are shown to disclose engine relationship, not counted as separate candidates. No experimental comparison was run. Evidence R1–R10.
