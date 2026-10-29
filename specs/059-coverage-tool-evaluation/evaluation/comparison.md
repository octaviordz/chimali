# Consolidated Comparison

| Dimension | JaCoCo 0.8.15 | Kover embedded 0.9.11 | Kover with JaCoCo 0.9.11 |
|---|---|---|---|
| Required host/device workload | Historically supported in project | Device collection unsupported by documented Gradle plugin | Device collection unsupported by same plugin |
| Exact project toolchain | Existing configured and historically run | Exact AGP/Kotlin combination unverified | Exact combination unverified |
| Metrics | Current policy uses instructions and applicable branches | Supports these units; equivalence unverified | JaCoCo engine may preserve counter basis; integration parity unverified |
| Inventory and variants | Existing configured inventory and collector | Android variants documented; project mapping/shrunk parity unverified | Same front end limitation; custom inventory parity unverified |
| Data merging and baseline | Existing project contract | Compatibility unverified | Engine retained but custom evidence continuity still unverified |
| Operational cost | Existing pipeline; costs unmeasured | Host-only and no replacement for device collection; added cost unknown | Front-end change and added integration cost unknown |
| Evidence confidence | Historical and direct repository evidence | Versioned documentation; exact project experiment absent | Versioned documentation; exact project experiment absent |

Kover's Android device limitation excludes it as a demonstrated complete replacement in the assessed version. The bounded search found no third credible candidate with evidence of full required workload support. No numerical tool-to-tool claim is made. Documented capability limits are distinguished from project-specific unverified details. Sources R1–R10.
