# PasskeyCredential copy staging cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-013

## Source correction

`PasskeyCredential.copy()` copied four mutable text owners followed by AAGUID and credential-ID arrays before constructing the new model. A later copy or constructor failure could strand earlier copies. It now stages all six copies until model construction succeeds and erases each staged owner/array on failure. `copyWithIndependentSensitiveText()` delegates to this guarded copy path. The compatibility constructor similarly stages its four text-owner conversions until the owner bundle is complete.

## Verification and limitations

The new host regressions inject failure at every copy position for both copy and compatibility-construction paths, and during final credential construction. They verify that staged owners/arrays are erased and source model fields remain intact. The full FIDO host suite passes 1,345 tests across 174 reports with zero failures, errors or skips; FIDO KtLint passes.

This covers model-local copy and compatibility-constructor staging only. Repository/provider copies, downstream borrowers and heap roots remain open.
