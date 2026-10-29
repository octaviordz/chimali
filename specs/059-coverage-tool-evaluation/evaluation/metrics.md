# Metrics and Report Fidelity

- JaCoCo instruction counters count bytecode instructions; branch counters cover if/switch decisions and exclude exception handling. Line coverage is a distinct diagnostic measure and cannot replace instruction floors.
- Kover documents LINE, INSTRUCTION and BRANCH units (LINE is its default in the versioned docs) and compatible XML output. Shared unit labels/report syntax do not prove equal denominator, Kotlin filtering or raw counts.
- JaCoCo 0.8.15 release notes include Kotlin filtering/SMAP corrections. Kover uses Kotlin-aware IntelliJ instrumentation by default and JaCoCo optionally; exact generated-code filtering parity for Chimali's configured outputs is unverified.
- Current policy maps compiler outputs, 14 production modules, source sets and variants; it rejects unmapped classes and uses explicit generated-code exclusion rules. Candidate inventory/report fidelity against this full mapping is unverified without a project experiment.
- Kover supports Android build variants in documented scope, but exact shrunkDebug plus device workload is not established. Its official device-test limitation rules out full-workload equivalence for the Gradle plugin version assessed.
- Shrinking can change bytecode and class identities; no candidate parity evidence is inferred from a variant label alone.

No percentages are compared because no same-revision cross-tool measurement was performed. Evidence R1, R4–R6, R8.
