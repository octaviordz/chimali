# Data and Baseline Continuity

JaCoCo execution data is associated with class IDs derived from class bytes; analysis requires matching classes. Current collector also checks probe/class compatibility and uses a project-specific normalized zero proof, fingerprint and accepted-target comparison.

Kover supports its own binary reports and external inputs and can emit JaCoCo-compatible XML. Documentation does not establish that Kover data can be merged with the current `.exec`/`.ec` data, that identical class/probe identity is preserved across Android device outputs, or that its report reproduces current inventory, denominator and baseline fingerprints. Treat all of these as unverified. Using Kover's JaCoCo backend may retain engine semantics but does not automatically preserve the custom collection and verification contract.

Current policy compares instruction and applicable branch ratios without regression and binds scope/toolchain/harness/device identity. Any change to engine or evidence contract would require separately governed migration and target recapture; no migration design is included here.

**Disposition**: Current JaCoCo continuity is documented. Alternative data merging, class/probe matching, denominator preservation and accepted-baseline continuity are unverified; compatible XML alone is insufficient evidence. Sources R1, R4, R6–R7.
