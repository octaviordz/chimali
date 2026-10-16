# Root-cause investigation

## Report and checkout identity

The “Implement HDK draft migration” chat recorded baseline registration returning “Generated key
not found in KeyStore” and migrated registration returning a generic crypto failure. The checkout
is `8b4d85ad`; migration source is preserved in its existing stash. The local signing edit predates
this task and is preserved.

## Evidence and hypothesis

Baseline registration derives a public key, then calls `getPublicKey`, which derives it again
and converts it using Bouncy Castle. A GeneralSecurityException becomes null and then the
misleading KeyStore error. These are derived keys, not persisted KeyStore credential keys.

Existing release `usage.txt` lists `EC$Mappings`, `EdEC$Mappings`, and EC/Ed25519 KeyFactory SPI
classes as removed. The FIDO2 rules incorrectly assume dependency consumer rules protect Bouncy
Castle. The merged configuration has a BC dontwarn directive but no corresponding keeps.

Bouncy Castle constructs mapping class names and registers service implementation names as
strings. See [the pinned upstream provider source](https://github.com/bcgit/bc-java/blob/r1rv80/prov/src/main/java/org/bouncycastle/jce/provider/BouncyCastleProvider.java).
Inspection of the local 1.80 JAR confirms the names.

## Decision

Reproduce missing services in the optimized target APK, then preserve three credential mappings
and their needed public SPI constructors. Keep dependencies optimizable. Do not disable R8,
preserve the entire crypto library, alter algorithms, or apply the HDK migration stash.

The stashed migration still converts Ed25519 keys through BouncyCastleProvider, so this packaging
defect applies there too. It does not establish that every earlier browser failure had this cause.

## Alternatives

- Unshrunk tests: insufficient because they do not exercise optimized application packaging.
- Provider instances: already used by the baseline; cannot restore removed classes.
- Entire-library keep: unnecessary when supported entry points can be retained narrowly.

## Validation

Controlled before/after results and artifact sizes are recorded in `verification.md`.
