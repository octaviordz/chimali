# Kover Candidate

**Version assessed**: 0.9.11.

**Engine relationship**: The Gradle plugin can use its embedded IntelliJ coverage engine or JaCoCo. The JaCoCo configuration changes the front end while retaining JaCoCo as the measurement engine; backend configurations are not separate candidates.

**Supported evidence**: Versioned documentation describes JVM/Kotlin Multiplatform host coverage, Android variants, mixed Kotlin/Java and instruction, branch and line report units. It documents JaCoCo-compatible XML and warns that using JaCoCo as an alternative backend does not guarantee all features.

**Unsupported evidence**: The versioned Gradle-plugin documentation explicitly says Android device-test coverage is not collected. Required device tests are part of Chimali's policy, so neither plugin configuration is established as a complete replacement for this required workload. The CLI/offline toolset is distinct from the Gradle plugin, but no maintained, supported end-to-end device collection path was established by the searched official sources; mark that path unverified.

**Exact stack**: Changelog entries include AGP 9 support/fixes, KMP Android fixes and Gradle 9.6 deprecation handling. They do not certify exact AGP 9.4.1/Kotlin 2.3.21/KMP compatibility. Mark exact project compatibility unverified.

**Disposition**: Host-side option supported in documented scope; required device collection unsupported for this plugin version; complete-workload replacement unsupported as documented. XML and counter/baseline equivalence remain unverified.

**Sources**: R4–R5, R8–R10.
